// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.identity

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
            "equifax-freeze", IdentityActionPhase.URGENT_FREEZE,
            "Freeze credit at Equifax", "Equifax", "~5 min",
            "https://www.equifax.com/personal/credit-report-services/credit-freeze/",
            "Free. Takes effect within one business day. Reversible with a PIN."
        ),
        IdentityAction(
            "experian-freeze", IdentityActionPhase.URGENT_FREEZE,
            "Freeze credit at Experian", "Experian", "~5 min",
            "https://www.experian.com/freeze/center.html",
            "Free. Must be done separately from the other two bureaus."
        ),
        IdentityAction(
            "transunion-freeze", IdentityActionPhase.URGENT_FREEZE,
            "Freeze credit at TransUnion", "TransUnion", "~5 min",
            "https://www.transunion.com/credit-freeze",
            "Free. Keep the PIN somewhere safer than this app's screen."
        ),
        IdentityAction(
            "innovis-freeze", IdentityActionPhase.URGENT_FREEZE,
            "Freeze at Innovis (4th bureau)", "Innovis", "~5 min",
            "https://www.innovis.com/securityFreeze/index",
            "Smaller bureau that some lenders still pull from. Skipped by most guides."
        ),
        IdentityAction(
            "chex-freeze", IdentityActionPhase.URGENT_FREEZE,
            "Freeze at ChexSystems", "ChexSystems", "~5 min",
            "https://www.chexsystems.com/security-freeze/place-freeze",
            "Blocks fraudulent checking/savings account openings in your name."
        ),

        // --- Monitoring ---
        IdentityAction(
            "hibp-notify", IdentityActionPhase.MONITORING,
            "Enroll in Have I Been Pwned notifications", "Have I Been Pwned", "~2 min",
            "https://haveibeenpwned.com/NotifyMe",
            "Separate from OnlyTrusted's own Breach Watch — gives you Troy Hunt's full feed with explicit breach names."
        ),
        IdentityAction(
            "irs-ip-pin", IdentityActionPhase.MONITORING,
            "Get an IRS Identity Protection PIN", "IRS", "~15 min",
            "https://www.irs.gov/identity-theft-fraud-scams/get-an-identity-protection-pin",
            "Blocks someone else filing a fraudulent tax return in your name. Needs ID.me verification."
        ),
        IdentityAction(
            "ssa-mysocialsecurity", IdentityActionPhase.MONITORING,
            "Open your my Social Security account", "Social Security Administration", "~15 min",
            "https://www.ssa.gov/myaccount/",
            "Prevents a thief from creating one in your name first and redirecting your benefits."
        ),
        IdentityAction(
            "ssa-eservices-block", IdentityActionPhase.MONITORING,
            "Block online SSN changes (eServices block)", "Social Security Administration", "~10 min (phone call)",
            "https://www.ssa.gov/pubs/EN-05-10220.pdf",
            "Call 1-800-772-1213 after you've opened the account above to lock out online changes."
        ),
        IdentityAction(
            "annualcreditreport", IdentityActionPhase.MONITORING,
            "Pull free weekly credit reports", "AnnualCreditReport.com", "~10 min",
            "https://www.annualcreditreport.com/",
            "The only federally authorised free report site. Weekly reports from all 3 bureaus are now permanent."
        ),

        // --- Recovery ---
        IdentityAction(
            "ftc-identitytheft", IdentityActionPhase.RECOVERY,
            "File an FTC identity theft report", "FTC", "~20 min",
            "https://www.identitytheft.gov/",
            "This report is what banks and lenders ask for to reverse fraudulent charges. Save the PDF."
        ),
        IdentityAction(
            "fraud-alert-one-call", IdentityActionPhase.RECOVERY,
            "Place a 1-year fraud alert (one-call does all three bureaus)",
            "Equifax / Experian / TransUnion", "~5 min",
            "https://www.equifax.com/personal/credit-report-services/credit-fraud-alerts/",
            "Lenders must verify your identity before opening new credit. One bureau notifies the others."
        ),
        IdentityAction(
            "police-report", IdentityActionPhase.RECOVERY,
            "File a local police report", "Local law enforcement", "~45 min",
            "https://www.usa.gov/local-governments",
            "Bring your FTC report. Some banks require a police report before refunding six-figure losses."
        ),
        IdentityAction(
            "dmv-id-theft", IdentityActionPhase.RECOVERY,
            "Report ID theft to your state DMV", "State DMV", "~15 min",
            "https://www.usa.gov/motor-vehicle-services",
            "Blocks someone replacing your driver's licence or state ID. Procedure varies by state."
        )
    )

    fun grouped(): List<Pair<IdentityActionPhase, List<IdentityAction>>> =
        IdentityActionPhase.values().map { phase ->
            phase to all.filter { it.phase == phase }
        }
}

/** Tracks locally-marked "done" actions. */
object IdentityActionsProgress {

    private const val PREFS = "identity_actions_prefs_v1"
    private const val KEY_DONE = "done_ids"

    private fun prefs(ctx: Context): SharedPreferences {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun done(ctx: Context): Set<String> =
        prefs(ctx).getString(KEY_DONE, "")?.split(",")?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    fun toggle(ctx: Context, id: String) {
        val current = done(ctx).toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        prefs(ctx).edit().putString(KEY_DONE, current.sorted().joinToString(",")).apply()
    }

    fun isDone(ctx: Context, id: String): Boolean = done(ctx).contains(id)

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().remove(KEY_DONE).apply()
    }
}
