// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.bordermode

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.onlytrusted.app.deadmanwipe.DeadmanWipeState
import com.onlytrusted.app.privacy.ProtectionSettings
import com.onlytrusted.app.security.AppLockManager
import com.onlytrusted.app.services.PocketDetectionService
import com.onlytrusted.app.services.SensorMonitorService
import com.onlytrusted.app.services.WatermarkOverlayService
import com.onlytrusted.app.ui.duress.DuressState
import com.onlytrusted.app.ui.safety.AutoLockPrefs
import com.onlytrusted.app.ui.settings.SettingsViewModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Border Mode (Elite). Mirrors iOS `BorderModeState`.
 *
 * One-tap preset that stacks every defensive layer at the maximum setting for high-risk
 * crossings (border, airport, protest, hostile territory). On [arm], the previous state of
 * every setting we touch is snapshotted into `previous_state` (JSON blob), then every
 * orchestrated setting is forced to its most defensive value. On [disarm], the snapshot is
 * restored. See the DESIGN_BRIEF for the explicit 9-point list this covers.
 *
 * All persisted fields live in `EncryptedSharedPreferences` (AES-256-GCM) under the key
 * `border_mode_prefs_v1`, so a filesystem copy cannot read the cover identity blob.
 */
object BorderModeState {

    private const val PREFS = "border_mode_prefs_v1"
    private const val KEY_ARMED = "armed"
    private const val KEY_ARMED_AT = "armed_at"
    private const val KEY_PREVIOUS_STATE = "previous_state_json"
    private const val KEY_COVER_IDENTITY = "cover_identity_json"

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

    // ---------- Public read API ----------

    fun isArmed(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ARMED, false)

    fun armedAtMillis(ctx: Context): Long = prefs(ctx).getLong(KEY_ARMED_AT, 0L)

    /** Returns the stored cover identity, or `null` if the user never set one up. */
    fun coverIdentity(ctx: Context): CoverIdentity? {
        val raw = prefs(ctx).getString(KEY_COVER_IDENTITY, null) ?: return null
        return runCatching { CoverIdentity.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun saveCoverIdentity(ctx: Context, identity: CoverIdentity?) {
        val editor = prefs(ctx).edit()
        if (identity == null) {
            editor.remove(KEY_COVER_IDENTITY)
        } else {
            editor.putString(KEY_COVER_IDENTITY, identity.toJson().toString())
        }
        editor.apply()
    }

    // ---------- Models ----------

    /** Owner-chosen alias + plausible trusted contact list. */
    data class CoverIdentity(
        val ownerName: String,
        val contacts: List<CoverContact>
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ownerName", ownerName)
            put("contacts", JSONArray().apply {
                contacts.forEach { c -> put(c.toJson()) }
            })
        }

        companion object {
            fun fromJson(j: JSONObject): CoverIdentity {
                val arr = j.optJSONArray("contacts") ?: JSONArray()
                val list = (0 until arr.length()).mapNotNull { i ->
                    runCatching { CoverContact.fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
                return CoverIdentity(
                    ownerName = j.optString("ownerName", ""),
                    contacts = list
                )
            }
        }
    }

    data class CoverContact(val name: String, val number: String) {
        fun toJson(): JSONObject =
            JSONObject().apply { put("name", name); put("number", number) }

        companion object {
            fun fromJson(j: JSONObject): CoverContact =
                CoverContact(j.optString("name", ""), j.optString("number", ""))
        }
    }

    /**
     * Snapshot of every setting Border Mode will mutate. Stored as a JSON blob under
     * `previous_state_json`; read back on [disarm] to put everything back where the user
     * had it.
     */
    private data class PreviousState(
        val deadmanWipeEnabled: Boolean,
        val deadmanWipeInterval: Int,
        val duressEnabled: Boolean,
        val appLockEnabled: Boolean,
        val sensorMonitorEnabled: Boolean,
        val pocketDetectionEnabled: Boolean,
        val screenshotProtectionEnabled: Boolean,
        val autoLockEnabled: Boolean,
        val autoLockSeconds: Int,
        val shieldOverlayActive: Boolean,
        val ownerTag: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("deadmanWipeEnabled", deadmanWipeEnabled)
            put("deadmanWipeInterval", deadmanWipeInterval)
            put("duressEnabled", duressEnabled)
            put("appLockEnabled", appLockEnabled)
            put("sensorMonitorEnabled", sensorMonitorEnabled)
            put("pocketDetectionEnabled", pocketDetectionEnabled)
            put("screenshotProtectionEnabled", screenshotProtectionEnabled)
            put("autoLockEnabled", autoLockEnabled)
            put("autoLockSeconds", autoLockSeconds)
            put("shieldOverlayActive", shieldOverlayActive)
            put("ownerTag", ownerTag)
        }

        companion object {
            fun fromJson(j: JSONObject): PreviousState = PreviousState(
                deadmanWipeEnabled = j.optBoolean("deadmanWipeEnabled", false),
                deadmanWipeInterval = j.optInt("deadmanWipeInterval", 30),
                duressEnabled = j.optBoolean("duressEnabled", false),
                appLockEnabled = j.optBoolean("appLockEnabled", false),
                sensorMonitorEnabled = j.optBoolean("sensorMonitorEnabled", true),
                pocketDetectionEnabled = j.optBoolean("pocketDetectionEnabled", true),
                screenshotProtectionEnabled = j.optBoolean("screenshotProtectionEnabled", true),
                autoLockEnabled = j.optBoolean("autoLockEnabled", false),
                autoLockSeconds = j.optInt("autoLockSeconds", 60),
                shieldOverlayActive = j.optBoolean("shieldOverlayActive", false),
                ownerTag = j.optString("ownerTag", "")
            )
        }
    }

    // ---------- Arm / disarm ----------

    enum class ArmError(val message: String) {
        DURESS_NOT_CONFIGURED(
            "Duress Mode must be configured first — add a Duress PIN and alert contact before arming Border Mode."
        )
    }

    /** True only if Duress Mode has been fully set up (PIN + enabled). */
    fun duressReady(ctx: Context): Boolean {
        if (!DuressState.isEnabled(ctx)) return false
        // Peek at the Duress prefs file to see if a PIN salt + hash have been stored. We
        // don't expose Duress internals so we re-create its prefs handle locally.
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val duressPrefs = EncryptedSharedPreferences.create(
            ctx.applicationContext,
            "duress_prefs_v1",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return duressPrefs.contains("pin_salt") && duressPrefs.contains("pin_hash")
    }

    /**
     * Arm the preset. Caller is responsible for having gated this on
     * `SubscriptionState.hasElite(ctx)` and having run the triple-confirmation UI
     * (two warnings + current-PIN check).
     *
     * Returns `null` on success; an [ArmError] on refusal.
     */
    fun arm(ctx: Context, settingsPrefs: SharedPreferences): ArmError? {
        if (!duressReady(ctx)) return ArmError.DURESS_NOT_CONFIGURED

        // 1 — Snapshot current state so disarm can restore it.
        val snapshot = PreviousState(
            deadmanWipeEnabled = DeadmanWipeState.isEnabled(ctx),
            deadmanWipeInterval = DeadmanWipeState.intervalDays(ctx),
            duressEnabled = DuressState.isEnabled(ctx),
            appLockEnabled = AppLockManager.isEnabled(ctx),
            sensorMonitorEnabled = settingsPrefs.getBoolean(SettingsViewModel.KEY_SENSOR, true),
            pocketDetectionEnabled = settingsPrefs.getBoolean(SettingsViewModel.KEY_POCKET, true),
            screenshotProtectionEnabled = settingsPrefs.getBoolean(SettingsViewModel.KEY_SCREENSHOT, true),
            autoLockEnabled = AutoLockPrefs.isEnabled(ctx),
            autoLockSeconds = AutoLockPrefs.currentSeconds(ctx),
            shieldOverlayActive = settingsPrefs.getBoolean(KEY_SHIELD_ACTIVE, false),
            ownerTag = ProtectionSettings.ownerTag(ctx)
        )
        prefs(ctx).edit()
            .putString(KEY_PREVIOUS_STATE, snapshot.toJson().toString())
            .apply()

        // 2 — Force each orchestrated setting to its max-protection value.
        //
        // Point 1 — Dead-Man Wipe → 7 days (shortest).
        DeadmanWipeState.enable(ctx, intervalDays = 7)
        // Point 2 — Duress Mode active (already required by duressReady check; make sure).
        DuressState.setEnabled(ctx, true)
        // Point 3 — Biometric unlock off, PIN-only. AppLock stays on; we flip the
        // "biometric allowed" pref that the lock screen reads.
        settingsPrefs.edit().putBoolean(KEY_BIOMETRIC_ALLOWED, false).apply()
        // Point 4 — Sensor Monitor Service forced ON.
        settingsPrefs.edit()
            .putBoolean(SettingsViewModel.KEY_SENSOR, true)
            .apply()
        SensorMonitorService.start(ctx.applicationContext)
        // Point 5 — Screenshot protection ON.
        settingsPrefs.edit()
            .putBoolean(SettingsViewModel.KEY_SCREENSHOT, true)
            .apply()
        // Point 6 — Shield watermark ON with "DO NOT RECORD" text. We swap the owner tag
        // so WatermarkOverlayService's refresh loop picks the new label immediately.
        ProtectionSettings.setOwnerTag(ctx, "DO NOT RECORD")
        WatermarkOverlayService.start(ctx.applicationContext)
        settingsPrefs.edit().putBoolean(KEY_SHIELD_ACTIVE, true).apply()
        // Point 7 — Pocket-dial protection ON (Android-only, so the UI brief flags it).
        settingsPrefs.edit()
            .putBoolean(SettingsViewModel.KEY_POCKET, true)
            .apply()
        PocketDetectionService.start(ctx.applicationContext)
        // Point 8 — App Lock auto-lock → 30 s.
        AutoLockPrefs.setEnabled(ctx, true)
        AutoLockPrefs.setSeconds(ctx, 30)
        AppLockManager.setGraceSeconds(ctx, 30)
        // Point 9 — Cover identity swap: flip Duress session on so the vault / dashboard
        // read from the decoy. The cover identity (if any) is applied to the decoy below.
        DuressState.setDuressSession(true)
        coverIdentity(ctx)?.let { cover ->
            DuressState.setOwnerName(ctx, cover.ownerName)
        }

        // 3 — Flip the armed flag.
        prefs(ctx).edit()
            .putBoolean(KEY_ARMED, true)
            .putLong(KEY_ARMED_AT, System.currentTimeMillis())
            .apply()
        return null
    }

    /** Disarm. Caller is responsible for the biometric + PIN double-factor check. */
    fun disarm(ctx: Context, settingsPrefs: SharedPreferences) {
        val raw = prefs(ctx).getString(KEY_PREVIOUS_STATE, null)
        val snapshot = raw?.let {
            runCatching { PreviousState.fromJson(JSONObject(it)) }.getOrNull()
        }

        if (snapshot != null) {
            // Restore Dead-Man Wipe.
            if (snapshot.deadmanWipeEnabled) {
                DeadmanWipeState.enable(ctx, snapshot.deadmanWipeInterval)
            } else {
                DeadmanWipeState.disable(ctx)
            }
            DuressState.setEnabled(ctx, snapshot.duressEnabled)

            settingsPrefs.edit()
                .putBoolean(KEY_BIOMETRIC_ALLOWED, true)
                .putBoolean(SettingsViewModel.KEY_SENSOR, snapshot.sensorMonitorEnabled)
                .putBoolean(SettingsViewModel.KEY_POCKET, snapshot.pocketDetectionEnabled)
                .putBoolean(SettingsViewModel.KEY_SCREENSHOT, snapshot.screenshotProtectionEnabled)
                .putBoolean(KEY_SHIELD_ACTIVE, snapshot.shieldOverlayActive)
                .apply()

            val app = ctx.applicationContext
            if (snapshot.sensorMonitorEnabled) {
                SensorMonitorService.start(app)
            } else {
                SensorMonitorService.stop(app)
            }
            if (snapshot.pocketDetectionEnabled) {
                PocketDetectionService.start(app)
            } else {
                PocketDetectionService.stop(app)
            }
            if (!snapshot.shieldOverlayActive) WatermarkOverlayService.stop(ctx.applicationContext)
            ProtectionSettings.setOwnerTag(ctx, snapshot.ownerTag)

            AutoLockPrefs.setEnabled(ctx, snapshot.autoLockEnabled)
            AutoLockPrefs.setSeconds(ctx, snapshot.autoLockSeconds)
            AppLockManager.setGraceSeconds(
                ctx,
                if (!snapshot.autoLockEnabled) 600 else snapshot.autoLockSeconds
            )
        }
        DuressState.setDuressSession(false)

        prefs(ctx).edit()
            .putBoolean(KEY_ARMED, false)
            .remove(KEY_ARMED_AT)
            .remove(KEY_PREVIOUS_STATE)
            .apply()
    }

    // Public flags other screens can read without pulling in the whole state object.
    const val KEY_BIOMETRIC_ALLOWED = "settings.applock.biometric_allowed"
    const val KEY_SHIELD_ACTIVE = "shield_active"

    /** Clears every pref under this feature. Called by the global "Wipe everything" path. */
    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
