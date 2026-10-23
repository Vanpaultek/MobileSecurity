// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.smartlinkguard

import java.net.URI
import java.util.Locale

/**
 * Smart Link Guard — platform-agnostic URL safety rules engine.
 *
 * The Swift twin lives at `ios/OnlyTrusted/Sources/SmartLinkGuard/SmartLinkGuardRules.swift`
 * and must be kept in sync (same rule set, same shortener/allowlist/suspicious-TLD data,
 * same verdict logic). Both run 100% on-device — no network calls, no cloud lookups.
 *
 * Rule inventory (shared with iOS):
 *   1. Scheme check — flag non-HTTPS (http://, ftp://, etc.) as "insecure".
 *   2. Known shortener — 50+ domains that hide the real destination.
 *   3. Suspicious TLD — notorious scammer TLDs (.zip, .mov, .xyz, .top, etc.).
 *   4. Punycode / IDN — host containing `xn--` → possible homograph attack.
 *   5. IP literal — host is an IPv4/IPv6 literal (no DNS, used by phishers to dodge brand).
 *   6. Known-good allowlist — ~200 top legit domains → "verified allowlist".
 *   7. Typo-squat — Levenshtein ≤ 2 vs. an allowlist entry → "possible typosquat of X".
 *   8. Suspicious path keywords — /login, /verify, /secure, /account, /wallet, /paypal,
 *      /signin combined with a non-allowlisted or shortened host.
 */

enum class URLVerdict {
    Safe,
    Caution,
    Danger
}

data class URLSafetyResult(
    val url: String,
    val verdict: URLVerdict,
    val reasons: List<String>
) {
    val badgeText: String get() = when (verdict) {
        URLVerdict.Safe -> "Safe"
        URLVerdict.Caution -> "Caution"
        URLVerdict.Danger -> "Danger"
    }
}

object SmartLinkGuardRules {

    /** Optional injection point for the Offline Threat Intel database. The app
     *  wires this to `ThreatIntelDatabase.isPhishingHost` so a bundled APWG match
     *  bumps the verdict to Danger. Left null in tests + non-elite flows. */
    @Volatile
    var phishingHostLookup: ((String) -> Boolean)? = null

    // --- Public API -----------------------------------------------------------

    /** Run every heuristic over [rawUrl]. Falls back to Danger if the URL is unparseable. */
    fun inspect(rawUrl: String): URLSafetyResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return URLSafetyResult(rawUrl, URLVerdict.Danger, listOf("Empty link."))
        }

        // Pre-normalise: if the user pasted "example.com/foo" without a scheme, we still
        // run the host-based rules but note the missing scheme.
        val normalised = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = runCatching { URI(normalised) }.getOrNull()
            ?: return URLSafetyResult(rawUrl, URLVerdict.Danger, listOf("Can't parse as a URL."))

        val reasons = mutableListOf<String>()
        var worst: URLVerdict = URLVerdict.Safe

        // 1 — scheme
        val scheme = uri.scheme?.lowercase(Locale.US)
        when (scheme) {
            null -> {
                reasons += "Missing scheme — anyone could redirect you anywhere."
                worst = bump(worst, URLVerdict.Caution)
            }
            "https" -> { /* fine */ }
            "http" -> {
                reasons += "Insecure http:// — any Wi-Fi can tamper with the page."
                worst = bump(worst, URLVerdict.Caution)
            }
            "ftp", "telnet", "file" -> {
                reasons += "Legacy $scheme:// scheme — avoid."
                worst = bump(worst, URLVerdict.Caution)
            }
            else -> {
                // tel:, mailto:, sms: etc. — not web links; keep as Safe but note.
            }
        }

        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.")
        if (host.isNullOrEmpty()) {
            reasons += "No host — not a real website address."
            return URLSafetyResult(rawUrl, URLVerdict.Danger, reasons)
        }

        // 5 — IP literal
        if (isIpLiteral(host)) {
            reasons += "Direct IP address — real sites use their domain name."
            worst = bump(worst, URLVerdict.Danger)
        }

        // 4 — punycode / IDN
        if (host.contains("xn--")) {
            reasons += "Punycode detected — possible homograph (lookalike) attack."
            worst = bump(worst, URLVerdict.Danger)
        }

        // Compute the eTLD+1 for allowlist / typosquat checks.
        val etldPlusOne = eTLDPlusOne(host)

        // 6 — known-good allowlist (exact or www. match)
        val allowlistHit = etldPlusOne in ALLOWLIST
        if (allowlistHit) {
            reasons += "Verified allowlist — matches a known legitimate domain."
            // Allowlist alone can't lift a Danger (e.g. an http:// bank), but it can
            // keep the verdict at Safe when nothing else fires.
        }

        // 2 — known shortener
        val shortenerHit = etldPlusOne in SHORTENERS
        if (shortenerHit) {
            reasons += "Shortened link — the real destination is hidden."
            worst = bump(worst, URLVerdict.Caution)
        }

        // 3 — suspicious TLD
        val tld = etldPlusOne.substringAfterLast('.', missingDelimiterValue = "")
        if (tld.isNotEmpty() && tld in SUSPICIOUS_TLDS) {
            reasons += ".$tld is a suspicious top-level domain often used by scammers."
            worst = bump(worst, URLVerdict.Caution)
        }

        // 7 — typo-squat
        if (!allowlistHit) {
            val match = closestAllowlistMatch(etldPlusOne)
            if (match != null) {
                reasons += "Possible typosquat of $match (${match.distance} edit${if (match.distance == 1) "" else "s"} away)."
                worst = bump(worst, URLVerdict.Danger)
            }
        }

        // 7b — Offline Threat Intel (Elite). Only consulted when the main-app has
        // wired `phishingHostLookup`. Any hit forces Danger.
        phishingHostLookup?.let { lookup ->
            if (lookup(etldPlusOne)) {
                reasons += "Host appears in the bundled phishing-URL feed."
                worst = bump(worst, URLVerdict.Danger)
            }
        }

        // 8 — suspicious path keywords
        val path = (uri.rawPath ?: "").lowercase(Locale.US)
        if (path.isNotEmpty()) {
            val keyword = SUSPICIOUS_PATH_KEYWORDS.firstOrNull { path.contains("/$it") }
            if (keyword != null && (!allowlistHit || shortenerHit)) {
                reasons += "Path contains \"$keyword\" on an unverified host — classic phishing shape."
                worst = bump(worst, URLVerdict.Caution)
            }
        }

        if (reasons.isEmpty()) {
            reasons += "No red flags from on-device checks."
        }

        return URLSafetyResult(rawUrl, worst, reasons.toList())
    }

    /** Extract every URL-looking substring from a block of SMS text. */
    fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return URL_REGEX.findAll(text).map { it.value }.toList()
    }

    // --- Internals ------------------------------------------------------------

    private fun bump(current: URLVerdict, next: URLVerdict): URLVerdict =
        if (next.ordinal > current.ordinal) next else current

    private fun isIpLiteral(host: String): Boolean {
        if (host.startsWith("[") && host.endsWith("]")) return true // IPv6
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }

    private fun eTLDPlusOne(host: String): String {
        // Lightweight: last two labels. Good enough for allowlist / typosquat heuristics
        // since every entry in ALLOWLIST is a two-label eTLD+1.
        val parts = host.split('.')
        if (parts.size <= 2) return host
        return parts.takeLast(2).joinToString(".")
    }

    private data class Candidate(val match: String, val distance: Int)

    private fun closestAllowlistMatch(host: String): Candidate? {
        if (host.length < 4) return null
        var best: Candidate? = null
        for (entry in ALLOWLIST) {
            // Only compare lengths close enough that Lev could be ≤ 2 — cheap filter.
            if (kotlin.math.abs(entry.length - host.length) > 2) continue
            if (entry == host) continue
            val d = levenshtein(entry, host, cutoff = 2)
            if (d in 1..2 && (best == null || d < best!!.distance)) {
                best = Candidate(entry, d)
                if (d == 1) break // can't do better than 1 and still be different
            }
        }
        return best
    }

    /** Levenshtein distance with an early-exit cutoff. */
    private fun levenshtein(a: String, b: String, cutoff: Int): Int {
        val m = a.length
        val n = b.length
        if (kotlin.math.abs(m - n) > cutoff) return cutoff + 1
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > cutoff) return cutoff + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    // --- Data: shortener list, suspicious TLDs, allowlist ---------------------

    // Kept intentionally generous so the rules engine stays useful without a cloud update.
    internal val SHORTENERS: Set<String> = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "buff.ly", "is.gd", "soo.gd",
        "s.id", "ow.ly", "cutt.ly", "rb.gy", "shorturl.at", "lnkd.in", "tiny.cc",
        "bl.ink", "clck.ru", "x.co", "db.tt", "qr.ae", "adf.ly", "mcaf.ee",
        "po.st", "u.to", "j.mp", "trib.al", "yourls.org", "shorte.st", "zpr.io",
        "chl.li", "fb.me", "youtu.be", "snip.ly", "sptfy.com", "amzn.to", "l.ead.me",
        "linktr.ee", "shortly.app", "surl.li", "mcaf.in", "v.gd", "shrunken.com",
        "rebrand.ly", "ouo.io", "shrtco.de", "4a.gs", "7.ly", "9nl.me", "a.gy",
        "aa.cx", "ad.vu", "ad7.biz", "al.ly", "bc.vc", "bitly.com", "bitly.ws",
        "cli.gs", "da.gd", "doi.co", "fff.to", "flic.kr", "hyperurl.co", "ity.im",
        "kck.st", "lnk.to", "mcaf.ly", "mz.cm", "n9.cl", "nyti.ms", "p.ost.im",
        "post.ly", "qpon.in", "redd.it", "reduceurl.com", "s2r.co", "sgb.gl",
        "shar.es", "shrten.io", "shrt.li", "sl.ink", "snurl.com", "spoti.fi",
        "spr.ly", "sq.ly", "ssu.rl", "swll.to", "sz.ag", "t.ly", "tcrn.ch",
        "tgr.ph", "tiny.pl", "tinylink.net", "tinyme.so", "tnw.co", "twt.si",
        "u.nu", "upfurl.com", "urlz.fr", "urly.it", "vk.cc", "vlst.co", "wa.me",
        "wp.me", "xoxo.gl", "y2u.be", "zc.vg", "zi.pe", "zws.im", "cs.pn",
        "nyp.st", "wapo.st", "ebay.us", "amzn.com", "apple.co", "msft.it"
    )

    internal val SUSPICIOUS_TLDS: Set<String> = setOf(
        "zip", "mov", "xyz", "top", "click", "loan", "party", "gq", "tk", "ml", "cf", "ga",
        "country", "stream", "download", "racing", "science", "work", "men", "date",
        "accountant", "faith", "review", "trade", "win", "cricket", "biz"
    )

    internal val SUSPICIOUS_PATH_KEYWORDS: List<String> = listOf(
        "login", "verify", "secure", "account", "wallet", "paypal", "signin",
        "sign-in", "update-password", "reset-password", "unlock"
    )

    /** ~200 top legit eTLD+1s. Keep in sync with SmartLinkGuardRules.swift. */
    internal val ALLOWLIST: Set<String> = setOf(
        // Big tech
        "google.com", "youtube.com", "apple.com", "icloud.com", "microsoft.com",
        "live.com", "outlook.com", "office.com", "office365.com", "bing.com",
        "amazon.com", "aws.amazon.com", "meta.com", "facebook.com", "instagram.com",
        "whatsapp.com", "threads.net", "x.com", "twitter.com", "linkedin.com",
        "github.com", "gitlab.com", "bitbucket.org", "stackoverflow.com", "reddit.com",
        "pinterest.com", "tiktok.com", "snapchat.com", "discord.com", "slack.com",
        "zoom.us", "dropbox.com", "box.com", "notion.so", "figma.com", "canva.com",
        "openai.com", "chatgpt.com", "anthropic.com", "claude.ai", "huggingface.co",
        "nvidia.com", "intel.com", "amd.com", "oracle.com", "ibm.com", "sap.com",
        "salesforce.com", "atlassian.com", "jira.com", "trello.com", "zendesk.com",
        "intercom.com", "hubspot.com", "mailchimp.com", "sendgrid.com", "twilio.com",
        "stripe.com", "square.com", "squareup.com", "shopify.com", "etsy.com",
        "ebay.com", "walmart.com", "target.com", "costco.com", "bestbuy.com",
        "homedepot.com", "lowes.com", "ikea.com", "wayfair.com",

        // Banks / finance (US-centric; extend per locale as needed)
        "chase.com", "bankofamerica.com", "wellsfargo.com", "citibank.com", "citi.com",
        "capitalone.com", "usbank.com", "pnc.com", "truist.com", "ally.com",
        "discover.com", "americanexpress.com", "amex.com", "paypal.com", "venmo.com",
        "cashapp.com", "zellepay.com", "robinhood.com", "fidelity.com", "schwab.com",
        "vanguard.com", "coinbase.com", "kraken.com", "binance.com", "gemini.com",

        // Carriers / telco
        "att.com", "verizon.com", "verizonwireless.com", "t-mobile.com", "sprint.com",
        "uscellular.com", "cricketwireless.com", "metrobyt-mobile.com", "boostmobile.com",
        "mintmobile.com", "visible.com", "spectrum.net", "xfinity.com", "comcast.com",
        "cox.com", "optimum.com",

        // Shipping / travel
        "ups.com", "fedex.com", "usps.com", "dhl.com", "uber.com", "lyft.com",
        "doordash.com", "grubhub.com", "ubereats.com", "airbnb.com", "booking.com",
        "expedia.com", "kayak.com", "hotels.com", "united.com", "delta.com",
        "aa.com", "southwest.com", "jetblue.com", "alaskaair.com",

        // News / media
        "nytimes.com", "wsj.com", "washingtonpost.com", "bbc.com", "bbc.co.uk",
        "cnn.com", "reuters.com", "apnews.com", "theverge.com", "techcrunch.com",
        "arstechnica.com", "wired.com", "bloomberg.com", "ft.com", "economist.com",

        // Government / health
        "irs.gov", "usa.gov", "ssa.gov", "medicare.gov", "cdc.gov", "nih.gov",
        "fda.gov", "state.gov", "whitehouse.gov",

        // Education / reference
        "wikipedia.org", "wikimedia.org", "khanacademy.org", "coursera.org", "edx.org",

        // Dev / tools
        "cloudflare.com", "digitalocean.com", "linode.com", "vercel.com", "netlify.com",
        "heroku.com", "render.com", "fly.io", "docker.com", "npmjs.com", "pypi.org",
        "rubygems.org", "gradle.org", "maven.org", "jetbrains.com", "android.com",
        "developer.apple.com", "mozilla.org", "firefox.com", "chromium.org",

        // Internal
        "vanpaultek.com", "57million.com", "onlytrusted.app", "splntr.app"
    )

    // RFC-3986ish URL regex, intentionally greedy on the host side so phishing links
    // ending with punctuation like "...paypal.com." or ")" are still captured.
    private val URL_REGEX = Regex(
        """\b((?:https?://|ftp://|www\.)[^\s<>"']+)""",
        RegexOption.IGNORE_CASE
    )
}
