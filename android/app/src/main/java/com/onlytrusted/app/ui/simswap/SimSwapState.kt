// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.simswap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SIM-Swap Alarm state (Pro tier).
 *
 * Fingerprints the current SIM identity (SIM serial + IMSI + carrier operator + operator
 * name) and stores the SHA-256 hash in `EncryptedSharedPreferences` (same AES-256-GCM-via-
 * Keystore scheme Duress uses). On every app launch the current identity is hashed and
 * compared to the baseline; a mismatch surfaces an alarm.
 *
 * Permissions:
 *  - `READ_PHONE_STATE` is required for `getSimSerialNumber` / `getSubscriberId` /
 *    `getSimOperator`. On Android 10+ IMSI is restricted to privileged apps so we fall
 *    back to SIM serial + operator. On devices where neither is readable (no SIM, no
 *    permission, or OEM lockdown) [capture] returns an all-unavailable identity and the
 *    UI shows a warning banner.
 */
object SimSwapState {

    private const val PREFS = "simswap_prefs_v1"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BASELINE_HASH = "baseline_hash"
    private const val KEY_BASELINE_DISPLAY = "baseline_display"
    private const val KEY_BASELINE_AT = "baseline_captured_at"
    private const val KEY_BASELINE_IS_SENTINEL = "baseline_is_sentinel"
    private const val KEY_CARRIER_NUMBER = "carrier_number"

    private val _pendingAlarm = MutableStateFlow<Alarm?>(null)
    val pendingAlarm: StateFlow<Alarm?> = _pendingAlarm.asStateFlow()

    data class Alarm(
        val detectedAtMillis: Long,
        val oldHashShort: String,
        val newHashShort: String,
        val currentDisplay: String
    )

    data class Baseline(
        val hash: String,
        val display: String,
        val capturedAtMillis: Long,
        val isSentinel: Boolean
    ) {
        fun formattedTimestamp(): String =
            SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(capturedAtMillis))
    }

    data class SimIdentity(
        val serial: String,
        val imsi: String,
        val operator: String,
        val operatorName: String
    ) {
        val isSentinel: Boolean
            get() = serial.isEmpty() && imsi.isEmpty() && operator.isEmpty() && operatorName.isEmpty()

        val displayString: String
            get() {
                if (isSentinel) return "SIM unavailable (no SIM, no permission, or OEM lockdown)"
                val op = operatorName.ifEmpty { operator.ifEmpty { "Unknown carrier" } }
                val tail = serial.takeLast(4).ifEmpty { "----" }
                return "$op · SIM ****$tail"
            }

        val hash: String
            get() {
                val material = "$serial|$imsi|$operator|$operatorName"
                val bytes = MessageDigest.getInstance("SHA-256")
                    .digest(material.toByteArray(Charsets.UTF_8))
                return bytes.joinToString("") { "%02x".format(it) }
            }
    }

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ---------- Permissions ----------

    fun hasPhoneStatePermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    // ---------- Public API ----------

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun carrierNumber(ctx: Context): String =
        prefs(ctx).getString(KEY_CARRIER_NUMBER, null) ?: "611"

    fun setCarrierNumber(ctx: Context, value: String) {
        val cleaned = value.trim().ifEmpty { "611" }
        prefs(ctx).edit().putString(KEY_CARRIER_NUMBER, cleaned).apply()
    }

    fun baseline(ctx: Context): Baseline? {
        val p = prefs(ctx)
        val hash = p.getString(KEY_BASELINE_HASH, null) ?: return null
        val display = p.getString(KEY_BASELINE_DISPLAY, null).orEmpty()
        val at = p.getLong(KEY_BASELINE_AT, 0L)
        val isSentinel = p.getBoolean(KEY_BASELINE_IS_SENTINEL, false)
        if (at == 0L) return null
        return Baseline(hash, display, at, isSentinel)
    }

    /** Reads the current carrier identity from TelephonyManager. */
    @SuppressLint("HardwareIds", "MissingPermission")
    fun capture(ctx: Context): SimIdentity {
        if (!hasPhoneStatePermission(ctx)) {
            return SimIdentity("", "", "", "")
        }
        val tm = ctx.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return SimIdentity("", "", "", "")
        val serial = runCatching { tm.simSerialNumber ?: "" }.getOrDefault("")
        val imsi = runCatching { tm.subscriberId ?: "" }.getOrDefault("")
        val operator = runCatching { tm.simOperator ?: "" }.getOrDefault("")
        val name = runCatching { tm.simOperatorName ?: "" }.getOrDefault("")
        return SimIdentity(serial, imsi, operator, name)
    }

    /**
     * Captures the current SIM identity and writes it as the baseline, and enables the
     * alarm. Returns the new baseline. If no SIM data is readable, the baseline is still
     * stored with `isSentinel = true` so the UI can display a warning.
     */
    fun captureBaseline(ctx: Context): Baseline {
        val id = capture(ctx)
        val b = Baseline(
            hash = id.hash,
            display = id.displayString,
            capturedAtMillis = System.currentTimeMillis(),
            isSentinel = id.isSentinel
        )
        prefs(ctx).edit()
            .putString(KEY_BASELINE_HASH, b.hash)
            .putString(KEY_BASELINE_DISPLAY, b.display)
            .putLong(KEY_BASELINE_AT, b.capturedAtMillis)
            .putBoolean(KEY_BASELINE_IS_SENTINEL, b.isSentinel)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        _pendingAlarm.value = null
        return b
    }

    /** Clears the baseline. The alarm stays disabled until the user captures a new one. */
    fun resetBaseline(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_BASELINE_HASH)
            .remove(KEY_BASELINE_DISPLAY)
            .remove(KEY_BASELINE_AT)
            .remove(KEY_BASELINE_IS_SENTINEL)
            .putBoolean(KEY_ENABLED, false)
            .apply()
        _pendingAlarm.value = null
    }

    /**
     * Called from `MainActivity.onResume`. Compares the current SIM identity against the
     * stored baseline and surfaces an alarm if the hashes differ. No-op when the alarm
     * is disabled or no baseline has been captured.
     *
     * Returns the alarm payload if one was fired (for logging convenience); otherwise
     * `null`.
     */
    fun checkOnForeground(ctx: Context): Alarm? {
        if (!isEnabled(ctx)) return null
        val b = baseline(ctx) ?: return null
        val current = capture(ctx)
        if (current.hash == b.hash) return null
        val alarm = Alarm(
            detectedAtMillis = System.currentTimeMillis(),
            oldHashShort = b.hash.take(8),
            newHashShort = current.hash.take(8),
            currentDisplay = current.displayString
        )
        _pendingAlarm.value = alarm
        return alarm
    }

    /** Dismisses the alarm WITHOUT touching the baseline. */
    fun dismissAlarm() {
        _pendingAlarm.value = null
    }

    /** User chose "I did this swap on purpose" — overwrite the baseline with the current. */
    fun acknowledgeAndRebaseline(ctx: Context) {
        captureBaseline(ctx)
    }

    @Suppress("unused")
    private fun base64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
}
