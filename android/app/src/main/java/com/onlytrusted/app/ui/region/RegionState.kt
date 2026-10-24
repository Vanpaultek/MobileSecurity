// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.region

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone

/**
 * Read-only snapshot of the user's region derived from OS signals. Mirrors iOS
 * `RegionState`. Zero network, zero extra permission beyond READ_PHONE_STATE which
 * SIM-Swap Alarm already uses.
 *
 * Priority: Locale.getDefault().country → fallback "US". SIM country is enriching but
 * not authoritative because travellers often keep their home SIM.
 */
object RegionState {

    data class Snapshot(
        val country: String,
        val countryName: String,
        val timeZoneId: String,
        val languageTag: String,
        val simCountry: String?,
        val simCarrier: String?
    ) {
        val displaySummary: String
            get() {
                val parts = mutableListOf(countryName, timeZoneId, languageTag)
                if (simCountry != null) {
                    val carrierSuffix = simCarrier?.let { " · $it" } ?: ""
                    parts.add("SIM: $simCountry$carrierSuffix")
                }
                return parts.joinToString(" · ")
            }
    }

    fun snapshot(ctx: Context): Snapshot {
        val locale = Locale.getDefault()
        val country = locale.country.ifBlank { "US" }.uppercase()
        val countryName = Locale("", country).displayCountry.ifBlank { country }
        val timeZoneId = TimeZone.getDefault().id
        val languageTag = locale.toLanguageTag()

        val tm = ctx.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simCountry = runCatching { tm?.simCountryIso?.uppercase()?.ifBlank { null } }.getOrNull()
        val simCarrier = runCatching { tm?.simOperatorName?.ifBlank { null } }.getOrNull()

        return Snapshot(
            country = country,
            countryName = countryName,
            timeZoneId = timeZoneId,
            languageTag = languageTag,
            simCountry = simCountry,
            simCarrier = simCarrier
        )
    }
}
