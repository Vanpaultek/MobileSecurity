// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.trackerfinder

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * On-device, encrypted store for BLE tracker sightings observed by Tracker Finder (Pro).
 *
 * Reuses the AES-256-GCM EncryptedSharedPreferences pattern from HP1 Duress and
 * HP3 Watchdog. All MAC-adjacent identifiers are SHA-256-hashed with a per-install
 * salt before persistence so the stored file never contains a raw MAC.
 *
 * Storage shape:
 *  • Backing prefs: `tracker_finder_prefs_v1` (EncryptedSharedPreferences).
 *  • Key: `tracker_finder_sessions_v1` — JSON array of [Sighting] (`macHash`, `type`,
 *    `rssi`, `timestamp`, `sessionId`, optional `locationHash`).
 *  • Salt key: `tracker_finder_salt_v1` — random 32-byte base64 string, created once
 *    per install.
 *
 * "Following" classifier (mirrors the brief verbatim):
 *   3+ sightings across 3+ distinct scan sessions over a window of > 15 minutes.
 */
object TrackerFinderStore {

    private const val PREFS = "tracker_finder_prefs_v1"
    private const val KEY_SIGHTINGS = "tracker_finder_sessions_v1"
    private const val KEY_SALT = "tracker_finder_salt_v1"
    private const val KEY_CONTINUOUS = "tracker_finder.continuous_enabled.v1"
    private const val KEY_LAST_ALERT_HASH = "tracker_finder.last_alert_hash.v1"
    private const val KEY_LAST_ALERT_AT = "tracker_finder.last_alert_at.v1"

    const val FOLLOWING_MIN_SESSIONS = 3
    const val FOLLOWING_MIN_AGE_MS: Long = 15 * 60 * 1000L
    private const val MAX_LOG_AGE_MS: Long = 7 * 24 * 60 * 60 * 1000L

    enum class TrackerType(val displayName: String) {
        APPLE_AIRTAG("Apple AirTag"),
        TILE("Tile"),
        SAMSUNG_SMARTTAG("Samsung SmartTag"),
        UNKNOWN_BLE("Unknown tracker");

        companion object {
            fun fromKey(key: String?): TrackerType =
                entries.firstOrNull { it.name == key } ?: UNKNOWN_BLE
        }
    }

    data class Sighting(
        val id: String,
        val macHash: String,
        val type: TrackerType,
        val rssi: Int,
        val timestamp: Long,
        val sessionId: String,
        val locationHash: String? = null
    )

    /** Aggregated view of a tracker currently classified as a follower. */
    data class FollowerTracker(
        val macHash: String,
        val type: TrackerType,
        val firstSeenMs: Long,
        val lastSeenMs: Long,
        val sessionCount: Int,
        val totalSightings: Int,
        val averageRssi: Int
    ) {
        /** First 8 chars of the hash grouped as `AA·BB·CC·DD`. */
        val shortLabel: String
            get() = macHash.take(8).uppercase().chunked(2).joinToString("·")
    }

    // ---- Toggles ----

    fun isContinuousEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CONTINUOUS, false)

    fun setContinuousEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CONTINUOUS, enabled).apply()
    }

    // ---- Record / query ----

    /**
     * SHA-256 of (per-install salt || raw address). MACs never land on disk directly;
     * the hash is useful for cross-session equality but useless if copied off-device.
     */
    fun hashAddress(ctx: Context, rawAddress: String): String {
        val salt = installSalt(ctx)
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(salt)
            update(rawAddress.toByteArray(Charsets.UTF_8))
        }.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun record(ctx: Context, sighting: Sighting) {
        val all = loadAll(ctx).toMutableList()
        all.add(sighting)
        // Trim log older than 7 days — the "following" rule only looks at >15 min.
        val cutoff = System.currentTimeMillis() - MAX_LOG_AGE_MS
        all.removeAll { it.timestamp < cutoff }
        saveAll(ctx, all)
    }

    fun recentSightings(ctx: Context, limit: Int = 100): List<Sighting> =
        loadAll(ctx).sortedByDescending { it.timestamp }.take(limit)

    /** Trackers meeting the "following" rule. */
    fun followingTrackers(ctx: Context): List<FollowerTracker> {
        val all = loadAll(ctx)
        return all.groupBy { it.macHash }
            .mapNotNull { (hash, group) ->
                if (group.size < FOLLOWING_MIN_SESSIONS) return@mapNotNull null
                val sessions = group.map { it.sessionId }.toSet()
                if (sessions.size < FOLLOWING_MIN_SESSIONS) return@mapNotNull null
                val first = group.minOf { it.timestamp }
                val last = group.maxOf { it.timestamp }
                if (last - first < FOLLOWING_MIN_AGE_MS) return@mapNotNull null
                val avg = (group.sumOf { it.rssi }.toDouble() / group.size).toInt()
                val latestType = group.maxByOrNull { it.timestamp }?.type ?: TrackerType.UNKNOWN_BLE
                FollowerTracker(
                    macHash = hash,
                    type = latestType,
                    firstSeenMs = first,
                    lastSeenMs = last,
                    sessionCount = sessions.size,
                    totalSightings = group.size,
                    averageRssi = avg
                )
            }
            .sortedByDescending { it.sessionCount }
    }

    fun clear(ctx: Context) {
        saveAll(ctx, emptyList())
    }

    // ---- Alert de-dupe ----

    /**
     * Returns true if we should post a "possible follower" notification for [hash] now;
     * returns false (and leaves state unchanged) if we recently nagged for the same one.
     */
    fun shouldAlertAndMark(ctx: Context, hash: String): Boolean {
        val now = System.currentTimeMillis()
        val lastHash = prefs(ctx).getString(KEY_LAST_ALERT_HASH, null)
        val lastAt = prefs(ctx).getLong(KEY_LAST_ALERT_AT, 0L)
        if (lastHash == hash && now - lastAt < 60 * 60 * 1000L) return false
        prefs(ctx).edit()
            .putString(KEY_LAST_ALERT_HASH, hash)
            .putLong(KEY_LAST_ALERT_AT, now)
            .apply()
        return true
    }

    // ---- Internals ----

    private fun prefs(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun installSalt(ctx: Context): ByteArray {
        val raw = prefs(ctx).getString(KEY_SALT, null)
        if (raw != null) {
            runCatching { return Base64.decode(raw) }
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit().putString(KEY_SALT, Base64.encode(fresh)).apply()
        return fresh
    }

    private fun loadAll(ctx: Context): List<Sighting> {
        val raw = prefs(ctx).getString(KEY_SIGHTINGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Sighting(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            macHash = o.optString("macHash"),
                            type = TrackerType.fromKey(o.optString("type")),
                            rssi = o.optInt("rssi"),
                            timestamp = o.optLong("timestamp"),
                            sessionId = o.optString("sessionId"),
                            locationHash = o.optString("locationHash", null)
                                .takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveAll(ctx: Context, list: List<Sighting>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("macHash", s.macHash)
                    put("type", s.type.name)
                    put("rssi", s.rssi)
                    put("timestamp", s.timestamp)
                    put("sessionId", s.sessionId)
                    if (s.locationHash != null) put("locationHash", s.locationHash)
                }
            )
        }
        prefs(ctx).edit().putString(KEY_SIGHTINGS, arr.toString()).apply()
    }
}
