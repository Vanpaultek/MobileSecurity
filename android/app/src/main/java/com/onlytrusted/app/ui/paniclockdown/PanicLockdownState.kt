// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.paniclockdown

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.data.db.entities.SecurityEvent
import com.onlytrusted.app.security.AppLockManager
import com.onlytrusted.app.services.SensorMonitorService
import com.onlytrusted.app.ui.duress.DuressState
import com.onlytrusted.app.ui.settings.SettingsViewModel
import com.onlytrusted.app.ui.smartlinkguard.SmartLinkGuardQrActivity
import com.onlytrusted.app.ui.smartlinkguard.SmartLinkGuardSmsReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Panic Lockdown Mode (Elite). The "I'm in trouble RIGHT NOW" counterpart to Border Mode's
 * triple-confirmation "I'm about to be in trouble" preset. Mirrors iOS `PanicLockdownState`.
 *
 * On [activate]:
 *   1. Immediately locks the app via [AppLockManager.resetUnlock].
 *   2. Arms Duress Mode so the very next unlock triggers the decoy vault + silent SOS SMS
 *      regardless of whether the real PIN or the duress PIN is entered.
 *   3. Forces Sensor Monitor to its most aggressive flagging posture (starts the foreground
 *      service and flips the pref so the dashboard reads it as "forced on").
 *   4. Disables the Smart Link Guard share-equivalent (SMS receiver + QR activity) via
 *      `PackageManager.setComponentEnabledSetting` so no accidental data leak.
 *   5. Disables biometric unlock — PIN-only (Fifth-Amendment advantage in US courts).
 *   6. Logs a `PANIC_LOCKDOWN_ACTIVATED` SecurityEvent with a timestamp.
 *
 * On [disarm] the snapshot is restored. Disarm requires biometric + PIN double-factor
 * (caller enforces that); the hardware-key tap is also required when a key is enrolled.
 *
 * Storage: `EncryptedSharedPreferences` (AES-256-GCM) keyed `panic_lockdown_prefs_v1`.
 */
object PanicLockdownState {

    private const val PREFS = "panic_lockdown_prefs_v1"
    private const val KEY_ARMED = "armed"
    private const val KEY_ARMED_AT = "armed_at"
    private const val KEY_PREVIOUS_STATE = "previous_state_json"

    /** Settings pref the lock screen reads to decide whether biometric unlock is allowed. */
    private const val KEY_BIOMETRIC_ALLOWED = "settings.applock.biometric_allowed"

    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Public read API ---

    fun isArmed(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ARMED, false)

    fun armedAtMillis(ctx: Context): Long = prefs(ctx).getLong(KEY_ARMED_AT, 0L)

    // --- Snapshot ---

    /**
     * Snapshot of every setting Panic Lockdown mutates. Stored as a JSON blob so [disarm]
     * can put everything back where the user had it. Mirrors Border Mode's PreviousState
     * approach — different field set, same mechanism.
     */
    private data class PreviousState(
        val duressEnabled: Boolean,
        val sensorMonitorEnabled: Boolean,
        val biometricAllowed: Boolean,
        val smartLinkGuardSmsEnabled: Int,
        val smartLinkGuardQrEnabled: Int
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("duressEnabled", duressEnabled)
            put("sensorMonitorEnabled", sensorMonitorEnabled)
            put("biometricAllowed", biometricAllowed)
            put("smartLinkGuardSmsEnabled", smartLinkGuardSmsEnabled)
            put("smartLinkGuardQrEnabled", smartLinkGuardQrEnabled)
        }

        companion object {
            fun fromJson(j: JSONObject): PreviousState = PreviousState(
                duressEnabled = j.optBoolean("duressEnabled", false),
                sensorMonitorEnabled = j.optBoolean("sensorMonitorEnabled", true),
                biometricAllowed = j.optBoolean("biometricAllowed", true),
                smartLinkGuardSmsEnabled = j.optInt(
                    "smartLinkGuardSmsEnabled",
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                ),
                smartLinkGuardQrEnabled = j.optInt(
                    "smartLinkGuardQrEnabled",
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                )
            )
        }
    }

    // --- Activate / disarm ---

    /**
     * Activate Panic Lockdown. Caller is responsible for having gated this on
     * `SubscriptionState.hasElite(ctx)`.
     *
     * Note: all actions here are idempotent — calling [activate] twice is safe. The snapshot
     * is only written on the first armed transition so we never overwrite a legitimate
     * previous-state with the lockdown's own values.
     */
    fun activate(ctx: Context) {
        val settingsPrefs = ctx.applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pm = ctx.applicationContext.packageManager
        val smsComponent = ComponentName(ctx, SmartLinkGuardSmsReceiver::class.java)
        val qrComponent = ComponentName(ctx, SmartLinkGuardQrActivity::class.java)

        // Only snapshot on the first armed transition. If a snapshot already exists, the
        // user re-tapped Activate while armed — don't clobber the saved pre-lockdown values.
        if (!isArmed(ctx)) {
            val snapshot = PreviousState(
                duressEnabled = DuressState.isEnabled(ctx),
                sensorMonitorEnabled = settingsPrefs.getBoolean(
                    SettingsViewModel.KEY_SENSOR, true
                ),
                biometricAllowed = settingsPrefs.getBoolean(KEY_BIOMETRIC_ALLOWED, true),
                smartLinkGuardSmsEnabled = pm.getComponentEnabledSetting(smsComponent),
                smartLinkGuardQrEnabled = pm.getComponentEnabledSetting(qrComponent)
            )
            prefs(ctx).edit()
                .putString(KEY_PREVIOUS_STATE, snapshot.toJson().toString())
                .apply()
        }

        // 1 — Lock the app. The next foregrounding goes through the lock screen.
        AppLockManager.resetUnlock(ctx)

        // 2 — Arm Duress Mode. Any subsequent unlock (real PIN or duress PIN) is treated
        //     as a duress session; the AppLockGate / vault read the live Duress state when
        //     the owner enters a PIN. See DuressState for the session-flag flow.
        DuressState.setEnabled(ctx, true)

        // 3 — Force Sensor Monitor to its most aggressive posture.
        settingsPrefs.edit()
            .putBoolean(SettingsViewModel.KEY_SENSOR, true)
            .apply()
        SensorMonitorService.start(ctx.applicationContext)

        // 4 — Disable Smart Link Guard receiver + QR activity so no accidental data leak.
        //     `COMPONENT_ENABLED_STATE_DISABLED` persists across restarts and is reversed
        //     on disarm by restoring the snapshotted state.
        pm.setComponentEnabledSetting(
            smsComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            qrComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        // 5 — Biometric unlock OFF. The lock screen reads this flag and only presents PIN.
        settingsPrefs.edit().putBoolean(KEY_BIOMETRIC_ALLOWED, false).apply()

        // 6 — Log the activation. Fire-and-forget on IO; we don't block the UI arming.
        logEvent(ctx, SecurityEvent.TYPE_PANIC_LOCKDOWN_ACTIVATED, "Panic Lockdown armed.")

        // Flip the armed flag last so observers see the orchestrated settings in place.
        prefs(ctx).edit()
            .putBoolean(KEY_ARMED, true)
            .putLong(KEY_ARMED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Disarm. Caller is responsible for biometric + PIN + optional hardware-key tap. */
    fun disarm(ctx: Context) {
        val settingsPrefs = ctx.applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pm = ctx.applicationContext.packageManager
        val smsComponent = ComponentName(ctx, SmartLinkGuardSmsReceiver::class.java)
        val qrComponent = ComponentName(ctx, SmartLinkGuardQrActivity::class.java)
        val raw = prefs(ctx).getString(KEY_PREVIOUS_STATE, null)
        val snapshot = raw?.let {
            runCatching { PreviousState.fromJson(JSONObject(it)) }.getOrNull()
        }

        if (snapshot != null) {
            // Restore each toggle to the pre-lockdown state. Duress session is cleared so
            // the owner is not stuck in the decoy vault post-disarm.
            DuressState.setEnabled(ctx, snapshot.duressEnabled)
            DuressState.setDuressSession(false)
            settingsPrefs.edit()
                .putBoolean(SettingsViewModel.KEY_SENSOR, snapshot.sensorMonitorEnabled)
                .putBoolean(KEY_BIOMETRIC_ALLOWED, snapshot.biometricAllowed)
                .apply()
            if (snapshot.sensorMonitorEnabled) {
                SensorMonitorService.start(ctx.applicationContext)
            } else {
                SensorMonitorService.stop(ctx.applicationContext)
            }
            pm.setComponentEnabledSetting(
                smsComponent,
                snapshot.smartLinkGuardSmsEnabled,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                qrComponent,
                snapshot.smartLinkGuardQrEnabled,
                PackageManager.DONT_KILL_APP
            )
        } else {
            // No snapshot — fall back to sane defaults. Should never happen in practice.
            DuressState.setDuressSession(false)
            settingsPrefs.edit().putBoolean(KEY_BIOMETRIC_ALLOWED, true).apply()
            pm.setComponentEnabledSetting(
                smsComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                qrComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
        }

        logEvent(ctx, SecurityEvent.TYPE_PANIC_LOCKDOWN_DISARMED, null)

        prefs(ctx).edit()
            .putBoolean(KEY_ARMED, false)
            .remove(KEY_ARMED_AT)
            .remove(KEY_PREVIOUS_STATE)
            .apply()
    }

    /** Clears every pref under this feature. Called by the global "Wipe everything" path. */
    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    // --- Event logging (fire-and-forget) ---

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun logEvent(ctx: Context, type: String, detail: String?) {
        val app = ctx.applicationContext as? OnlyTrustedApp ?: return
        scope.launch {
            runCatching { app.repository.log(type = type, detail = detail) }
        }
    }
}
