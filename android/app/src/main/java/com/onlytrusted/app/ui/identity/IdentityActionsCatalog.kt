// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.identity

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import androidx.work.Data as WorkData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.onlytrusted.app.work.IdentityReminderWorker
import java.util.concurrent.TimeUnit

/**
 * Static catalog of identity-protection actions. Mirrors iOS `IdentityActionsCatalog`.
 * All entries deep-link to official self-service portals; OnlyTrusted never contacts a
 * bureau or agency on the user's behalf. Local "done" state tracked in
 * EncryptedSharedPreferences.
 */
enum class IdentityActionPhase(val displayName: String, val subtitle: String) {
    URGENT_FREEZE(
        "Freeze & alert — do first",
        "Free with all three bureaus. Blocks new credit lines opened in your name."
    ),
    MONITORING(
        "Ongoing monitoring",
        "Set once, run forever. Catches issues early."
    ),
    RECOVERY(
        "If fraud has already happened",
        "Formal reports that unlock bank reimbursements and new credit lines."
    )
}

data class IdentityAction(
    val id: String,
    val phase: IdentityActionPhase,
    /** ISO 3166-1 alpha-2 country code the action applies to, or "WW" for worldwide. */
    val region: String,
    val title: String,
    val organisation: String,
    val timeEstimate: String,
    val url: String,
    val notes: String
)

object IdentityActionsCatalog {

    val all: List<IdentityAction> = listOf(
        // --- Urgent freeze ---
        IdentityAction(
            "equifax-freeze", IdentityActionPhase.URGENT_FREEZE, "US",
            "Freeze credit at Equifax", "Equifax", "~5 min",
            "https://www.equifax.com/personal/credit-report-services/credit-freeze/",
            "Free. Takes effect within one business day. Reversible with a PIN."
        ),
        IdentityAction(
            "experian-freeze", IdentityActionPhase.URGENT_FREEZE, "US",
            "Freeze credit at Experian", "Experian", "~5 min",
            "https://www.experian.com/freeze/center.html",
            "Free. Must be done separately from the other two bureaus."
        ),
        IdentityAction(
            "transunion-freeze", IdentityActionPhase.URGENT_FREEZE, "US",
            "Freeze credit at TransUnion", "TransUnion", "~5 min",
            "https://www.transunion.com/credit-freeze",
            "Free. Keep the PIN somewhere safer than this app's screen."
        ),
        IdentityAction(
            "innovis-freeze", IdentityActionPhase.URGENT_FREEZE, "US",
            "Freeze at Innovis (4th bureau)", "Innovis", "~5 min",
            "https://www.innovis.com/securityFreeze/index",
            "Smaller bureau that some lenders still pull from. Skipped by most guides."
        ),
        IdentityAction(
            "chex-freeze", IdentityActionPhase.URGENT_FREEZE, "US",
            "Freeze at ChexSystems", "ChexSystems", "~5 min",
            "https://www.chexsystems.com/security-freeze/place-freeze",
            "Blocks fraudulent checking/savings account openings in your name."
        ),

        // --- Monitoring ---
        IdentityAction(
            "hibp-notify", IdentityActionPhase.MONITORING, "US",
            "Enroll in Have I Been Pwned notifications", "Have I Been Pwned", "~2 min",
            "https://haveibeenpwned.com/NotifyMe",
            "Separate from OnlyTrusted's own Breach Watch — gives you Troy Hunt's full feed with explicit breach names."
        ),
        IdentityAction(
            "irs-ip-pin", IdentityActionPhase.MONITORING, "US",
            "Get an IRS Identity Protection PIN", "IRS", "~15 min",
            "https://www.irs.gov/identity-theft-fraud-scams/get-an-identity-protection-pin",
            "Blocks someone else filing a fraudulent tax return in your name. Needs ID.me verification."
        ),
        IdentityAction(
            "ssa-mysocialsecurity", IdentityActionPhase.MONITORING, "US",
            "Open your my Social Security account", "Social Security Administration", "~15 min",
            "https://www.ssa.gov/myaccount/",
            "Prevents a thief from creating one in your name first and redirecting your benefits."
        ),
        IdentityAction(
            "ssa-eservices-block", IdentityActionPhase.MONITORING, "US",
            "Block online SSN changes (eServices block)", "Social Security Administration", "~10 min (phone call)",
            "https://www.ssa.gov/pubs/EN-05-10220.pdf",
            "Call 1-800-772-1213 after you've opened the account above to lock out online changes."
        ),
        IdentityAction(
            "annualcreditreport", IdentityActionPhase.MONITORING, "US",
            "Pull free weekly credit reports", "AnnualCreditReport.com", "~10 min",
            "https://www.annualcreditreport.com/",
            "The only federally authorised free report site. Weekly reports from all 3 bureaus are now permanent."
        ),

        // --- Recovery ---
        IdentityAction(
            "ftc-identitytheft", IdentityActionPhase.RECOVERY, "US",
            "File an FTC identity theft report", "FTC", "~20 min",
            "https://www.identitytheft.gov/",
            "This report is what banks and lenders ask for to reverse fraudulent charges. Save the PDF."
        ),
        IdentityAction(
            "fraud-alert-one-call", IdentityActionPhase.RECOVERY, "US",
            "Place a 1-year fraud alert (one-call does all three bureaus)",
            "Equifax / Experian / TransUnion", "~5 min",
            "https://www.equifax.com/personal/credit-report-services/credit-fraud-alerts/",
            "Lenders must verify your identity before opening new credit. One bureau notifies the others."
        ),
        IdentityAction(
            "police-report", IdentityActionPhase.RECOVERY, "US",
            "File a local police report", "Local law enforcement", "~45 min",
            "https://www.usa.gov/local-governments",
            "Bring your FTC report. Some banks require a police report before refunding six-figure losses."
        ),
        IdentityAction(
            "dmv-id-theft", IdentityActionPhase.RECOVERY, "US",
            "Report ID theft to your state DMV", "State DMV", "~15 min",
            "https://www.usa.gov/motor-vehicle-services",
            "Blocks someone replacing your driver's licence or state ID. Procedure varies by state."
        )
    )

    /** Legacy call-site that returns the US catalog by default. Prefer `grouped(countryCode)`. */
    fun grouped(): List<Pair<IdentityActionPhase, List<IdentityAction>>> = grouped("US")

    /**
     * Region-aware grouping. Returns entries for [countryCode] (2-letter ISO) plus any
     * worldwide entries. Unknown codes fall back to the US catalog so the screen always
     * has something to show.
     */
    fun grouped(countryCode: String): List<Pair<IdentityActionPhase, List<IdentityAction>>> {
        val region = countryCode.uppercase()
        val perRegion: Map<String, List<IdentityAction>> = mapOf(
            "US" to all,
            "IN" to indiaActions,
            "WW" to emptyList()
        )
        val fallback = if (perRegion.containsKey(region)) region else "US"
        val applicable = (perRegion[fallback] ?: emptyList()) + (perRegion["WW"] ?: emptyList())
        return IdentityActionPhase.values().map { phase ->
            phase to applicable.filter { it.phase == phase }
        }
    }

    /** India-specific identity-protection actions mirroring the US flow. */
    val indiaActions: List<IdentityAction> = listOf(
        IdentityAction("aadhaar-biometric-lock", IdentityActionPhase.URGENT_FREEZE, "IN",
            "Lock Aadhaar biometric at UIDAI", "UIDAI", "~5 min",
            "https://resident.uidai.gov.in/aadhaar-lockunlock",
            "Prevents anyone from using your Aadhaar biometric for KYC until you unlock it. Free. Needs your Aadhaar number + registered mobile."
        ),
        IdentityAction("aadhaar-online-lock", IdentityActionPhase.URGENT_FREEZE, "IN",
            "Lock Aadhaar online services", "UIDAI", "~5 min",
            "https://myaadhaar.uidai.gov.in/",
            "Blocks e-KYC + Virtual ID requests until unlocked. Separate from biometric lock."
        ),
        IdentityAction("cibil-freeze", IdentityActionPhase.URGENT_FREEZE, "IN",
            "Freeze CIBIL report", "TransUnion CIBIL", "~15 min",
            "https://www.cibil.com/",
            "India's largest credit bureau. Freeze prevents new loan accounts in your name."
        ),
        IdentityAction("experian-india-freeze", IdentityActionPhase.URGENT_FREEZE, "IN",
            "Freeze Experian India report", "Experian India", "~15 min",
            "https://www.experian.in/",
            "Second-largest Indian credit bureau. Must freeze separately from CIBIL + CRIF + Equifax."
        ),
        IdentityAction("crif-highmark-freeze", IdentityActionPhase.URGENT_FREEZE, "IN",
            "Freeze CRIF High Mark report", "CRIF High Mark", "~15 min",
            "https://www.crifhighmark.com/",
            "Lender-focused bureau. Still used by many NBFCs and microfinance institutions."
        ),
        IdentityAction("equifax-india-freeze", IdentityActionPhase.URGENT_FREEZE, "IN",
            "Freeze Equifax India report", "Equifax India", "~15 min",
            "https://www.equifax.co.in/",
            "Fourth Indian bureau. Required for complete coverage."
        ),
        IdentityAction("hibp-notify-india", IdentityActionPhase.MONITORING, "IN",
            "Enroll in Have I Been Pwned notifications", "Have I Been Pwned", "~2 min",
            "https://haveibeenpwned.com/NotifyMe",
            "Global breach feed. Covers Indian breaches too (BigBasket, JustDial, Zomato, Dr Lal PathLabs, etc.)."
        ),
        IdentityAction("aadhaar-history", IdentityActionPhase.MONITORING, "IN",
            "Review Aadhaar authentication history", "UIDAI", "~10 min",
            "https://resident.uidai.gov.in/aadhaar-auth-history",
            "Shows every e-KYC + biometric auth against your Aadhaar in the last 6 months. Spot unexpected activity early."
        ),
        IdentityAction("trai-dnd-register", IdentityActionPhase.MONITORING, "IN",
            "Register with DND (Do Not Disturb)", "TRAI", "~5 min",
            "https://www.ncpr.dnd.gov.in/",
            "Blocks promotional calls + SMS. SMS 'START 0' to 1909 for full block."
        ),
        IdentityAction("income-tax-portal", IdentityActionPhase.MONITORING, "IN",
            "Register / check your Income Tax e-filing account", "Income Tax Department", "~15 min",
            "https://www.incometax.gov.in/",
            "Enables 2FA on your PAN. Prevents fraudulent returns in your name."
        ),
        IdentityAction("cybercrime-india", IdentityActionPhase.RECOVERY, "IN",
            "File a cybercrime report", "National Cyber Crime Reporting Portal", "~20 min",
            "https://cybercrime.gov.in/",
            "Official MHA portal. Covers financial fraud, impersonation, cyber-stalking. Call 1930 for financial fraud < 24 hours old."
        ),
        IdentityAction("ncu-financial", IdentityActionPhase.RECOVERY, "IN",
            "Call 1930 — Cyber financial fraud helpline", "I4C / Cyber Crime Coordination Centre", "~5 min (call)",
            "https://cybercrime.gov.in/webform/helpline.aspx",
            "Freeze fraudulent UPI / net-banking transactions in minutes if you call within the golden hour (first 60 min)."
        ),
        IdentityAction("nchelpline", IdentityActionPhase.RECOVERY, "IN",
            "Call 1800-11-4000 — National Consumer Helpline", "Department of Consumer Affairs", "~15 min (call)",
            "https://consumerhelpline.gov.in/",
            "For non-fraud consumer disputes — unauthorized charges, service denial, warranty issues."
        ),
        IdentityAction("local-police-india", IdentityActionPhase.RECOVERY, "IN",
            "File a local police FIR", "Local police", "~60 min",
            "https://digitalpolice.gov.in/",
            "Bring your cybercrime.gov.in acknowledgement + bank statement. FIR is required for insurance / chargeback claims above ₹10k."
        )
    )
}

/** Tracks locally-marked "done" actions. */
object IdentityActionsProgress {

    private const val PREFS = "identity_actions_prefs_v1"
    private const val KEY_DONE = "done_ids"
    private const val KEY_REMINDERS = "reminders_json"

    /** Yearly cadence for Premium renewal reminders. */
    const val REMINDER_YEARS: Long = 1

    // Cache the MasterKey to avoid the Keystore round-trip on every prefs() call.
    @Volatile private var cachedMasterKey: MasterKey? = null

    private fun prefs(ctx: Context): SharedPreferences {
        val master = cachedMasterKey ?: synchronized(this) {
            cachedMasterKey ?: MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { cachedMasterKey = it }
        }
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun done(ctx: Context): Set<String> {
        val raw = prefs(ctx).getString(KEY_DONE, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            // Fallback for prefs written by the earlier comma-join format — read once,
            // discard any id containing a comma (hardcoded ids never do), rewrite as JSON.
            raw.split(",").filter { it.isNotEmpty() && !it.contains(",") }.toSet()
        }
    }

    fun toggle(ctx: Context, id: String) {
        val current = done(ctx).toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        val arr = JSONArray().apply { current.sorted().forEach { put(it) } }
        prefs(ctx).edit().putString(KEY_DONE, arr.toString()).apply()
    }

    fun isDone(ctx: Context, id: String): Boolean = done(ctx).contains(id)

    // --- Premium: yearly renewal reminders -----------------------------------

    fun reminders(ctx: Context): Map<String, Long> {
        val raw = prefs(ctx).getString(KEY_REMINDERS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap<String, Long> { obj.keys().forEach { k -> put(k, obj.getLong(k)) } }
        }.getOrDefault(emptyMap())
    }

    fun reminderAt(ctx: Context, id: String): Long? = reminders(ctx)[id]

    fun setReminder(ctx: Context, id: String, title: String, nowMillis: Long = System.currentTimeMillis()) {
        val fireAt = nowMillis + REMINDER_YEARS * 365 * 24L * 60 * 60 * 1000
        val current = reminders(ctx).toMutableMap()
        current[id] = fireAt
        persistReminders(ctx, current)
        val data = WorkData.Builder()
            .putString("action_id", id)
            .putString("title", title)
            .build()
        val req = OneTimeWorkRequestBuilder<IdentityReminderWorker>()
            .setInitialDelay(REMINDER_YEARS * 365, TimeUnit.DAYS)
            .setInputData(data)
            .addTag(tag(id))
            .build()
        WorkManager.getInstance(ctx.applicationContext).enqueue(req)
    }

    fun clearReminder(ctx: Context, id: String) {
        val updated = reminders(ctx).toMutableMap().apply { remove(id) }
        persistReminders(ctx, updated)
        WorkManager.getInstance(ctx.applicationContext).cancelAllWorkByTag(tag(id))
    }

    private fun tag(id: String) = "identity_reminder_$id"

    private fun persistReminders(ctx: Context, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs(ctx).edit().putString(KEY_REMINDERS, obj.toString()).apply()
    }

    fun clearAll(ctx: Context) {
        reminders(ctx).keys.forEach { clearReminder(ctx, it) }
        prefs(ctx).edit().remove(KEY_DONE).remove(KEY_REMINDERS).apply()
    }
}
