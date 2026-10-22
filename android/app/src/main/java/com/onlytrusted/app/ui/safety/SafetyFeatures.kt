// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.safety

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.onlytrusted.app.data.repository.MaskedContact
import com.onlytrusted.app.privacy.PhoneUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =============================================================================
// F3 — Known-contact caller name helper
// =============================================================================

/**
 * Returns the display name of a trusted contact matching the given phone-number-like
 * string, or null when there is no match. Mirrors iOS CallerLookup: tolerates mixed
 * input formats by comparing both the normalised form and a last-6-digits fallback.
 */
object CallerLookup {

    private val numberRegex = Regex("\\+?\\d{7,}")

    fun nameForNumber(phoneLike: String?, contacts: List<MaskedContact>): String? {
        if (phoneLike.isNullOrEmpty()) return null
        val candidate = numberRegex.find(phoneLike)?.value ?: phoneLike
        val target = PhoneUtils.normalise(candidate)
        if (target.isEmpty()) return null

        for (contact in contacts) {
            if (contact.normalisedNumbers.any { it == target }) {
                return contact.displayName
            }
            val tail = target.takeLast(6)
            if (tail.length == 6 && contact.normalisedNumbers.any { it.endsWith(tail) }) {
                return contact.displayName
            }
        }
        return null
    }
}

// =============================================================================
// F2 — Scheduled auto-lock preferences
// =============================================================================

/**
 * Backed by [com.onlytrusted.app.security.AppLockManager] which already knows how to
 * check a `graceSeconds` window — we simply expose UI-friendly options and a toggle
 * that maps "Never" (unchecked) to a very large grace window so [AppLockManager
 * .shouldPrompt] never fires on resume. The toggle itself persists in a separate flag
 * because AppLockManager treats 0 seconds as "always prompt on resume" which would
 * surprise users who flipped the toggle off.
 */
object AutoLockPrefs {

    private const val PREFS = "autolock_prefs"
    private const val KEY_ENABLED = "autolock.enabled"
    private const val KEY_SECONDS = "settings.autolock.seconds"

    /** Shown in the picker in order: 30s, 1m, 5m, 15m, Never. */
    val options: List<Int> = listOf(30, 60, 300, 900, 0)

    fun label(seconds: Int): String = when {
        seconds == 0 -> "Never"
        seconds < 60 -> "$seconds seconds"
        seconds == 60 -> "1 minute"
        seconds == 300 -> "5 minutes"
        seconds == 900 -> "15 minutes"
        else -> "${seconds / 60} minutes"
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun currentSeconds(ctx: Context): Int = prefs(ctx).getInt(KEY_SECONDS, 60)

    fun setSeconds(ctx: Context, seconds: Int) {
        prefs(ctx).edit().putInt(KEY_SECONDS, seconds).apply()
    }
}

// =============================================================================
// F4 Weekly digest + F5 Honest Limits refresher — persistence
// =============================================================================

object HomeBannerPrefs {

    private const val PREFS = "home_banner_prefs"
    private const val KEY_LAST_WEEKLY = "home.banner.weeklyDigest.lastShownYMD"
    private const val KEY_LAST_REFRESH = "home.banner.honestLimitsRefresher.lastShownYM"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun shouldShowWeeklyDigest(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) return false
        val key = isoWeekKey(cal)
        return prefs(ctx).getString(KEY_LAST_WEEKLY, null) != key
    }

    fun markWeeklyDigestShown(ctx: Context, now: Long = System.currentTimeMillis()) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        prefs(ctx).edit().putString(KEY_LAST_WEEKLY, isoWeekKey(cal)).apply()
    }

    fun shouldShowHonestLimitsRefresher(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        val key = yearMonthKey(now)
        return prefs(ctx).getString(KEY_LAST_REFRESH, null) != key
    }

    fun markHonestLimitsRefresherShown(ctx: Context, now: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putString(KEY_LAST_REFRESH, yearMonthKey(now)).apply()
    }

    private fun isoWeekKey(cal: Calendar): String {
        val weekYear = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return "$weekYear-W$week"
    }

    private fun yearMonthKey(nowMillis: Long): String {
        val f = SimpleDateFormat("yyyy-MM", Locale.US)
        return f.format(Date(nowMillis))
    }
}

// =============================================================================
// F1 Panic contact helpers
// =============================================================================

object PanicContact {

    /**
     * Opens the dialer for the given [phoneNumber]. Uses ACTION_DIAL (no runtime
     * permission) so the user confirms the call in the dialer screen — matches iOS's
     * tel: behaviour.
     */
    fun dial(ctx: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No dialer app available.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Attempts to send the panic SMS silently using [SmsManager]. Falls back to the
     * system Messaging composer (ACTION_SENDTO) if SEND_SMS is not granted. The
     * silent-send path requires the Duress Mode permission that the manifest already
     * declares; we reuse it here because F1 is a user-triggered panic flow.
     */
    fun sendPanicSms(ctx: Context, phoneNumber: String, body: String) {
        val hasPermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            runCatching {
                @Suppress("DEPRECATION")
                val sms = SmsManager.getDefault()
                sms.sendTextMessage(phoneNumber, null, body, null, null)
            }.onFailure {
                openSmsComposer(ctx, phoneNumber, body)
            }
            return
        }
        // Fall back to the composer so the user can tap Send.
        openSmsComposer(ctx, phoneNumber, body)
    }

    private fun openSmsComposer(ctx: Context, phoneNumber: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No messaging app available.", Toast.LENGTH_SHORT).show()
        }
    }
}

// =============================================================================
// F7 Report-scam share text
// =============================================================================

object ReportScam {

    const val US_SMS_SHORTCODE = "7726"

    fun buildMessage(number: String, timestampMillis: Long): String {
        val f = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US)
        val human = f.format(Date(timestampMillis))
        val numberOut = if (number.isBlank()) "an unknown number" else number
        return "Blocked suspected scam call from $numberOut at $human. Reported via OnlyTrusted."
    }

    fun share(ctx: Context, number: String, timestampMillis: Long) {
        val body = buildMessage(number, timestampMillis)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            // Pre-fill the US carrier shortcode as recipient hint; downstream apps may ignore.
            putExtra("address", US_SMS_SHORTCODE)
            putExtra(Intent.EXTRA_SUBJECT, "OnlyTrusted scam report")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(send, "Report scam call")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No apps available to share this report.", Toast.LENGTH_SHORT).show()
        }
    }
}

// =============================================================================
// F8 Carrier spam report shortcuts
// =============================================================================

object CarrierSpamReport {

    enum class Carrier(
        val label: String,
        val playStorePackage: String,
        val playStoreUrl: String,
        val launchScheme: Uri?
    ) {
        TMobile(
            label = "T-Mobile Scam Shield",
            playStorePackage = "com.tmobile.pr.mytmobile",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.tmobile.pr.mytmobile",
            launchScheme = null
        ),
        ATT(
            label = "AT&T ActiveArmor",
            playStorePackage = "com.att.mobilesecurity",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.att.mobilesecurity",
            launchScheme = null
        ),
        Verizon(
            label = "Verizon Call Filter",
            playStorePackage = "com.vzw.ecid",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.vzw.ecid",
            launchScheme = null
        ),
        Other(
            label = "Other / Learn more",
            playStorePackage = "",
            playStoreUrl = "https://www.ftc.gov/faq/consumer-protection/report-unwanted-calls",
            launchScheme = null
        );

        fun isCarrierAppInstalled(ctx: Context): Boolean {
            if (playStorePackage.isEmpty()) return false
            return try {
                ctx.packageManager.getPackageInfo(playStorePackage, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    fun open(ctx: Context, carrier: Carrier) {
        // Prefer launching the installed carrier app via its launcher intent.
        if (carrier.isCarrierAppInstalled(ctx)) {
            val launch = ctx.packageManager.getLaunchIntentForPackage(carrier.playStorePackage)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(launch) }
                    .onFailure { Toast.makeText(ctx, "Couldn't open ${carrier.label}.", Toast.LENGTH_SHORT).show() }
                return
            }
        }
        // Fall back to Play Store (or the FTC web page for Other).
        val url = Uri.parse(carrier.playStoreUrl)
        val view = Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No browser or Play Store available.", Toast.LENGTH_SHORT).show()
        }
    }
}
