// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.privacy

/**
 * Pure, offline analysis of a phone number.
 *
 * Everything here is a deterministic string lookup — no network, no third-party service, no
 * caller-ID database. We only tell the owner what the number itself reveals.
 */
object CallerInsight {

    data class Insight(
        val normalised: String,
        val display: String,
        val country: String?,
        val region: String?,
        val category: Category
    )

    enum class Category {
        OWNER_FAMILY,
        TOLL_FREE,
        PREMIUM_RATE,
        SHORT_CODE,
        INTERNATIONAL,
        LIKELY_LOCAL,
        UNKNOWN
    }

    fun analyse(raw: String, isTrusted: Boolean = false): Insight {
        val normalised = PhoneUtils.normalise(raw)
        if (isTrusted) {
            return Insight(normalised, PhoneUtils.mask(raw), null, null, Category.OWNER_FAMILY)
        }

        val digits = normalised.removePrefix("+")

        // Short codes (3–6 digits in most markets).
        if (digits.length in 3..6) {
            return Insight(normalised, raw, null, null, Category.SHORT_CODE)
        }

        // NANPA: +1 followed by 10 digits
        if (digits.length == 11 && digits.startsWith("1")) {
            val areaCode = digits.substring(1, 4)
            val category = when (areaCode) {
                "800", "833", "844", "855", "866", "877", "888" -> Category.TOLL_FREE
                "900", "976" -> Category.PREMIUM_RATE
                else -> Category.LIKELY_LOCAL
            }
            return Insight(
                normalised = normalised,
                display = PhoneUtils.mask(raw),
                country = "United States / Canada",
                region = NANPA_AREAS[areaCode],
                category = category
            )
        }
        if (digits.length == 10) {
            // Unprefixed NANPA (common in US dial strings).
            val areaCode = digits.substring(0, 3)
            val category = when (areaCode) {
                "800", "833", "844", "855", "866", "877", "888" -> Category.TOLL_FREE
                "900", "976" -> Category.PREMIUM_RATE
                else -> Category.LIKELY_LOCAL
            }
            return Insight(normalised, PhoneUtils.mask(raw),
                "United States / Canada", NANPA_AREAS[areaCode], category)
        }

        // International: find the longest matching country calling code.
        val country = COUNTRY_CODES.entries
            .filter { digits.startsWith(it.key) }
            .maxByOrNull { it.key.length }
        if (country != null) {
            return Insight(normalised, PhoneUtils.mask(raw), country.value, null, Category.INTERNATIONAL)
        }

        return Insight(normalised, PhoneUtils.mask(raw), null, null, Category.UNKNOWN)
    }

    fun humanLabel(category: Category): String = when (category) {
        Category.OWNER_FAMILY -> "Family (trusted)"
        Category.TOLL_FREE -> "Toll-free (US/Canada 8xx)"
        Category.PREMIUM_RATE -> "Premium-rate number — expensive to call back"
        Category.SHORT_CODE -> "Short code (SMS campaign / shortcode service)"
        Category.INTERNATIONAL -> "International call"
        Category.LIKELY_LOCAL -> "Local number"
        Category.UNKNOWN -> "Unrecognised format"
    }

    /** North American Numbering Plan — area code → state/province abbreviation. */
    private val NANPA_AREAS: Map<String, String> = mapOf(
        "201" to "NJ", "202" to "DC", "203" to "CT", "205" to "AL", "206" to "WA",
        "207" to "ME", "208" to "ID", "209" to "CA", "210" to "TX", "212" to "NY",
        "213" to "CA", "214" to "TX", "215" to "PA", "216" to "OH", "217" to "IL",
        "218" to "MN", "219" to "IN", "224" to "IL", "225" to "LA", "228" to "MS",
        "229" to "GA", "231" to "MI", "234" to "OH", "239" to "FL", "240" to "MD",
        "248" to "MI", "251" to "AL", "252" to "NC", "253" to "WA", "254" to "TX",
        "256" to "AL", "260" to "IN", "262" to "WI", "267" to "PA", "269" to "MI",
        "270" to "KY", "276" to "VA", "281" to "TX", "301" to "MD", "302" to "DE",
        "303" to "CO", "304" to "WV", "305" to "FL", "307" to "WY", "308" to "NE",
        "309" to "IL", "310" to "CA", "312" to "IL", "313" to "MI", "314" to "MO",
        "315" to "NY", "316" to "KS", "317" to "IN", "318" to "LA", "319" to "IA",
        "320" to "MN", "321" to "FL", "323" to "CA", "325" to "TX", "330" to "OH",
        "334" to "AL", "336" to "NC", "337" to "LA", "339" to "MA", "347" to "NY",
        "351" to "MA", "352" to "FL", "360" to "WA", "361" to "TX", "386" to "FL",
        "401" to "RI", "402" to "NE", "404" to "GA", "405" to "OK", "406" to "MT",
        "407" to "FL", "408" to "CA", "409" to "TX", "410" to "MD", "412" to "PA",
        "413" to "MA", "414" to "WI", "415" to "CA", "417" to "MO", "419" to "OH",
        "423" to "TN", "424" to "CA", "425" to "WA", "432" to "TX", "434" to "VA",
        "435" to "UT", "440" to "OH", "443" to "MD", "469" to "TX", "470" to "GA",
        "475" to "CT", "478" to "GA", "479" to "AR", "480" to "AZ", "484" to "PA",
        "501" to "AR", "502" to "KY", "503" to "OR", "504" to "LA", "505" to "NM",
        "507" to "MN", "508" to "MA", "509" to "WA", "510" to "CA", "512" to "TX",
        "513" to "OH", "515" to "IA", "516" to "NY", "517" to "MI", "518" to "NY",
        "520" to "AZ", "530" to "CA", "540" to "VA", "541" to "OR", "551" to "NJ",
        "559" to "CA", "561" to "FL", "562" to "CA", "563" to "IA", "570" to "PA",
        "571" to "VA", "573" to "MO", "574" to "IN", "575" to "NM", "580" to "OK",
        "585" to "NY", "586" to "MI", "601" to "MS", "602" to "AZ", "603" to "NH",
        "605" to "SD", "606" to "KY", "607" to "NY", "608" to "WI", "609" to "NJ",
        "610" to "PA", "612" to "MN", "614" to "OH", "615" to "TN", "616" to "MI",
        "617" to "MA", "618" to "IL", "619" to "CA", "620" to "KS", "623" to "AZ",
        "626" to "CA", "630" to "IL", "631" to "NY", "636" to "MO", "641" to "IA",
        "646" to "NY", "650" to "CA", "651" to "MN", "660" to "MO", "661" to "CA",
        "662" to "MS", "667" to "MD", "678" to "GA", "682" to "TX", "701" to "ND",
        "702" to "NV", "703" to "VA", "704" to "NC", "706" to "GA", "707" to "CA",
        "708" to "IL", "712" to "IA", "713" to "TX", "714" to "CA", "715" to "WI",
        "716" to "NY", "717" to "PA", "718" to "NY", "719" to "CO", "720" to "CO",
        "724" to "PA", "727" to "FL", "731" to "TN", "732" to "NJ", "734" to "MI",
        "740" to "OH", "754" to "FL", "757" to "VA", "760" to "CA", "762" to "GA",
        "763" to "MN", "765" to "IN", "770" to "GA", "772" to "FL", "773" to "IL",
        "774" to "MA", "775" to "NV", "781" to "MA", "785" to "KS", "786" to "FL",
        "801" to "UT", "802" to "VT", "803" to "SC", "804" to "VA", "805" to "CA",
        "806" to "TX", "808" to "HI", "810" to "MI", "812" to "IN", "813" to "FL",
        "814" to "PA", "815" to "IL", "816" to "MO", "817" to "TX", "818" to "CA",
        "828" to "NC", "830" to "TX", "831" to "CA", "832" to "TX", "843" to "SC",
        "845" to "NY", "847" to "IL", "848" to "NJ", "850" to "FL", "856" to "NJ",
        "857" to "MA", "858" to "CA", "859" to "KY", "860" to "CT", "862" to "NJ",
        "863" to "FL", "864" to "SC", "865" to "TN", "870" to "AR", "878" to "PA",
        "901" to "TN", "903" to "TX", "904" to "FL", "906" to "MI", "907" to "AK",
        "908" to "NJ", "909" to "CA", "910" to "NC", "912" to "GA", "913" to "KS",
        "914" to "NY", "915" to "TX", "916" to "CA", "917" to "NY", "918" to "OK",
        "919" to "NC", "920" to "WI", "925" to "CA", "928" to "AZ", "931" to "TN",
        "936" to "TX", "937" to "OH", "940" to "TX", "941" to "FL", "947" to "MI",
        "949" to "CA", "952" to "MN", "954" to "FL", "956" to "TX", "970" to "CO",
        "971" to "OR", "972" to "TX", "973" to "NJ", "978" to "MA", "979" to "TX",
        "980" to "NC", "984" to "NC", "985" to "LA", "989" to "MI"
    )

    /** ITU-T E.164 country calling codes (common subset). */
    private val COUNTRY_CODES: Map<String, String> = mapOf(
        "1" to "United States / Canada",
        "7" to "Russia / Kazakhstan",
        "20" to "Egypt",
        "27" to "South Africa",
        "30" to "Greece",
        "31" to "Netherlands",
        "32" to "Belgium",
        "33" to "France",
        "34" to "Spain",
        "36" to "Hungary",
        "39" to "Italy",
        "40" to "Romania",
        "41" to "Switzerland",
        "43" to "Austria",
        "44" to "United Kingdom",
        "45" to "Denmark",
        "46" to "Sweden",
        "47" to "Norway",
        "48" to "Poland",
        "49" to "Germany",
        "51" to "Peru",
        "52" to "Mexico",
        "54" to "Argentina",
        "55" to "Brazil",
        "56" to "Chile",
        "57" to "Colombia",
        "58" to "Venezuela",
        "60" to "Malaysia",
        "61" to "Australia",
        "62" to "Indonesia",
        "63" to "Philippines",
        "64" to "New Zealand",
        "65" to "Singapore",
        "66" to "Thailand",
        "81" to "Japan",
        "82" to "South Korea",
        "84" to "Vietnam",
        "86" to "China",
        "90" to "Turkey",
        "91" to "India",
        "92" to "Pakistan",
        "93" to "Afghanistan",
        "94" to "Sri Lanka",
        "95" to "Myanmar",
        "98" to "Iran",
        "211" to "South Sudan",
        "212" to "Morocco",
        "213" to "Algeria",
        "216" to "Tunisia",
        "218" to "Libya",
        "220" to "Gambia",
        "221" to "Senegal",
        "233" to "Ghana",
        "234" to "Nigeria",
        "254" to "Kenya",
        "255" to "Tanzania",
        "256" to "Uganda",
        "260" to "Zambia",
        "263" to "Zimbabwe",
        "351" to "Portugal",
        "352" to "Luxembourg",
        "353" to "Ireland",
        "354" to "Iceland",
        "358" to "Finland",
        "359" to "Bulgaria",
        "370" to "Lithuania",
        "371" to "Latvia",
        "372" to "Estonia",
        "380" to "Ukraine",
        "381" to "Serbia",
        "385" to "Croatia",
        "420" to "Czechia",
        "421" to "Slovakia",
        "591" to "Bolivia",
        "593" to "Ecuador",
        "595" to "Paraguay",
        "598" to "Uruguay",
        "673" to "Brunei",
        "852" to "Hong Kong",
        "853" to "Macau",
        "855" to "Cambodia",
        "856" to "Laos",
        "880" to "Bangladesh",
        "886" to "Taiwan",
        "960" to "Maldives",
        "961" to "Lebanon",
        "962" to "Jordan",
        "963" to "Syria",
        "964" to "Iraq",
        "965" to "Kuwait",
        "966" to "Saudi Arabia",
        "967" to "Yemen",
        "968" to "Oman",
        "970" to "Palestine",
        "971" to "United Arab Emirates",
        "972" to "Israel",
        "973" to "Bahrain",
        "974" to "Qatar",
        "975" to "Bhutan",
        "977" to "Nepal",
        "992" to "Tajikistan",
        "993" to "Turkmenistan",
        "994" to "Azerbaijan",
        "995" to "Georgia",
        "996" to "Kyrgyzstan",
        "998" to "Uzbekistan"
    )
}
