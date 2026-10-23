// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.breachwatch

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

/**
 * Pure-functional lookup for Breach Watch (FREE tier). Mirrors iOS `BreachLookup`.
 *
 * Reads `assets/threatintel/breach_hashes.v1.txt` once (lazily on first call), caches
 * the 8-char prefixes in an in-memory [Set], then exposes [isEmailBreached]. Zero
 * network calls.
 *
 * The bundled file keeps 10-char prefixes (used by the Elite Threat Intel database);
 * we truncate to 8 so a 10-char prefix match is still a valid 8-char match.
 */
object BreachLookup {

    @Volatile
    private var prefixes: Set<String>? = null

    /** Trim + lowercase + strip internal whitespace. */
    fun normalise(email: String): String =
        email.trim().lowercase(Locale.ROOT).filter { !it.isWhitespace() }

    fun isEmailBreached(ctx: Context, email: String): Boolean {
        val normalised = normalise(email)
        if (normalised.isEmpty() || !normalised.contains("@")) return false
        val set = ensureLoaded(ctx)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalised.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString(separator = "") { "%02x".format(it) }
        val needle = hex.substring(0, 8)
        return set.contains(needle)
    }

    private fun ensureLoaded(ctx: Context): Set<String> {
        val cached = prefixes
        if (cached != null) return cached
        synchronized(this) {
            val again = prefixes
            if (again != null) return again
            val built = runCatching {
                ctx.applicationContext.assets.open("threatintel/breach_hashes.v1.txt")
                    .bufferedReader().use { reader ->
                        reader.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .mapNotNull {
                                val trunc = it.take(8).lowercase(Locale.ROOT)
                                trunc.takeIf { s -> s.length == 8 }
                            }
                            .toHashSet()
                    }
            }.getOrElse { emptySet() }
            prefixes = built
            return built
        }
    }
}
