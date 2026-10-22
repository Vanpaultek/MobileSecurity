// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.geofence

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * One trusted geofence: name + lat/lon + radius. The id doubles as the GeofencingClient
 * request id so broadcast events can map back to the entry.
 */
@Serializable
data class TrustedGeofence(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Geo-fenced Auto-Lock + Alert (Elite). Mirrors the iOS `GeofenceState`.
 *
 * Persistence lives in EncryptedSharedPreferences because the alert-contact id + owner
 * name could leak intent if the device is seized before the alert fires. The geofence
 * list itself isn't sensitive but travels in the same blob for simplicity.
 */
object GeofenceState {

    const val MAX_GEOFENCES = 5
    const val MIN_RADIUS_METERS: Float = 100f
    const val MAX_RADIUS_METERS: Float = 1_000f

    private const val PREFS = "geofence_prefs_v1"
    private const val KEY_LOCATIONS = "locations"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SMS_ENABLED = "sms_enabled"
    private const val KEY_SHARE_LOCATION = "share_location"
    private const val KEY_ALERT_CONTACT_ID = "alert_contact_id"
    private const val KEY_OWNER_NAME = "owner_name"

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun geofences(ctx: Context): List<TrustedGeofence> {
        val raw = prefs(ctx).getString(KEY_LOCATIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<TrustedGeofence>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun persist(ctx: Context, list: List<TrustedGeofence>) {
        val encoded = json.encodeToString(list)
        prefs(ctx).edit().putString(KEY_LOCATIONS, encoded).apply()
    }

    /**
     * Append a geofence. Returns `null` if we're already at the 5-location cap or the
     * name is blank. Caller is responsible for re-registering with [GeofenceManager].
     */
    fun addGeofence(
        ctx: Context,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float
    ): TrustedGeofence? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = geofences(ctx).toMutableList()
        if (current.size >= MAX_GEOFENCES) return null
        val clamped = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val fence = TrustedGeofence(
            name = trimmed,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = clamped
        )
        current.add(fence)
        persist(ctx, current)
        return fence
    }

    fun removeGeofence(ctx: Context, id: String) {
        val next = geofences(ctx).filter { it.id != id }
        persist(ctx, next)
        if (next.isEmpty() && isEnabled(ctx)) {
            setEnabled(ctx, false)
        }
    }

    fun updateRadius(ctx: Context, id: String, radiusMeters: Float) {
        val clamped = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val next = geofences(ctx).map { if (it.id == id) it.copy(radiusMeters = clamped) else it }
        persist(ctx, next)
    }

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun smsAlertEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SMS_ENABLED, true) // default ON per brief

    fun setSmsAlertEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SMS_ENABLED, value).apply()
    }

    fun shareLocationInAlert(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SHARE_LOCATION, false) // privacy-safe default

    fun setShareLocationInAlert(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SHARE_LOCATION, value).apply()
    }

    fun alertContactId(ctx: Context): Long? =
        prefs(ctx).getLong(KEY_ALERT_CONTACT_ID, -1L).takeIf { it > 0 }

    fun setAlertContactId(ctx: Context, id: Long?) {
        prefs(ctx).edit().apply {
            if (id == null) remove(KEY_ALERT_CONTACT_ID) else putLong(KEY_ALERT_CONTACT_ID, id)
        }.apply()
    }

    fun ownerName(ctx: Context): String =
        prefs(ctx).getString(KEY_OWNER_NAME, null).orEmpty()

    fun setOwnerName(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_OWNER_NAME, name).apply()
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
