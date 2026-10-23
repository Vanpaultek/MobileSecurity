// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.voicebio

import android.content.Context

/**
 * Passive helper that computes similarity between a candidate audio sample and the enrolled
 * voice-print set. Mirrors iOS `VoiceBioMonitor` but exposed as pure functions — Android's
 * TelephonyManager doesn't give us the same "call connected" hook iOS gets from CallKit, so
 * the live-call integration is a follow-up. For now this is called by diagnostic / debug
 * tooling and by the enrollment screen's optional "test match" button.
 */
object VoiceBioMonitor {

    /**
     * Compare [samples] against every enrolled contact and return the best match (contactId,
     * similarity). Returns `null` if nothing is enrolled or the clip is too short for MFCC.
     *
     * A caller treating the match as a positive identification should also check that
     * `similarity >= VoiceBioState.MATCH_THRESHOLD`.
     */
    fun bestMatch(ctx: Context, samples: FloatArray): Match? {
        val descriptor = MFCC.utteranceDescriptor(samples) ?: return null
        val enrolled = VoiceBioState.enrolledContactIds(ctx)
        if (enrolled.isEmpty()) return null

        var best: Match? = null
        for (id in enrolled) {
            val stored = VoiceBioState.descriptor(ctx, id) ?: continue
            val sim = MFCC.cosineSimilarity(stored, descriptor)
            if (best == null || sim > best.similarity) {
                best = Match(contactId = id, similarity = sim)
            }
        }
        return best
    }

    /**
     * Returns `true` if the best similarity across every enrolled vector is below the
     * threshold — the "possibly a cloned voice" signal.
     */
    fun looksLikeDeepfake(ctx: Context, samples: FloatArray): Boolean {
        if (!VoiceBioState.isFeatureEnabled(ctx)) return false
        val match = bestMatch(ctx, samples) ?: return false
        return match.similarity < VoiceBioState.MATCH_THRESHOLD
    }

    data class Match(val contactId: Long, val similarity: Float)
}
