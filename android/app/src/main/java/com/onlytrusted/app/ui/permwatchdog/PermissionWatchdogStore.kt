// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.permwatchdog

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * App Permission Watchdog — snapshot + diff of OTHER apps' sensitive permissions.
 *
 * Reuses the Duress Mode AES-256-GCM pattern (EncryptedSharedPreferences with
 * `AES256_SIV` key encryption and `AES256_GCM` value encryption). The snapshot
 * is a `Map<packageName, Set<permission>>` keyed by `permission_watchdog_snapshot_v1`.
 *
 * On every `evaluate()` call we rebuild the current snapshot and diff it against
 * the stored baseline. Any app that GAINED a sensitive permission since the baseline
 * is surfaced in the result. Removals are intentionally NOT surfaced — a user granting
 * then revoking is noise; we only care about new attack surface.
 *
 * Privacy posture: everything stays on-device. No network calls. The baseline file is
 * encrypted at rest with a hardware-backed key when available.
 */
object PermissionWatchdogStore {

    private const val PREFS = "permission_watchdog_prefs_v1"
    private const val KEY_SNAPSHOT = "permission_watchdog_snapshot_v1"
    private const val KEY_SNAPSHOT_AT = "permission_watchdog_snapshot_at_v1"
    private const val KEY_AUDIT_ENABLED = "permission_watchdog_audit_enabled_v1"
    private const val KEY_LAST_DISMISSED = "permission_watchdog_last_dismissed_v1"

    /**
     * The sensitive-permission set audited on every pass. Mirrors the brief verbatim.
     * We fold accessibility / device-admin / notification-listener here even though
     * they are usually service-binds (declared, not runtime-grantable) because an app
     * DECLARING them in its manifest is still an attack-surface signal.
     */
    val SENSITIVE_PERMISSIONS: Set<String> = linkedSetOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.QUERY_ALL_PACKAGES,
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_SCAN"
    )

    data class AppPermissions(
        val packageName: String,
        val label: String,
        val permissions: Set<String>
    )

    /** One app that gained ≥1 sensitive permission since the stored baseline. */
    data class Diff(
        val packageName: String,
        val label: String,
        val gained: Set<String>
    )

    data class EvaluationResult(
        val baselineAt: Long,          // ms epoch; 0 if no baseline yet
        val currentApps: List<AppPermissions>,
        val diffs: List<Diff>
    )

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

    // ---- Daily-audit toggle ----

    fun isAuditEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUDIT_ENABLED, true)

    fun setAuditEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUDIT_ENABLED, enabled).apply()
    }

    // ---- Public API ----

    /** True if a baseline has been written at least once. */
    fun hasBaseline(ctx: Context): Boolean =
        prefs(ctx).contains(KEY_SNAPSHOT)

    fun baselineCapturedAt(ctx: Context): Long =
        prefs(ctx).getLong(KEY_SNAPSHOT_AT, 0L)

    /** Enumerate installed third-party apps and the subset of sensitive permissions each requests. */
    fun scanCurrent(ctx: Context): List<AppPermissions> {
        val pm = ctx.applicationContext.packageManager
        @Suppress("DEPRECATION")
        val packages = runCatching {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }.getOrDefault(emptyList())

        val ownPkg = ctx.applicationContext.packageName
        return packages
            .asSequence()
            .filter { it.packageName != ownPkg }
            // Skip pure system packages — they clutter the list without being useful to review.
            // Pre-installed apps the user updated (UPDATED_SYSTEM_APP) still show up.
            .filter { info ->
                val ai = info.applicationInfo ?: return@filter false
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                !isSystem || isUpdatedSystem
            }
            .mapNotNull { info ->
                val requested = info.requestedPermissions?.toSet().orEmpty()
                val sensitive = requested.intersect(SENSITIVE_PERMISSIONS)
                if (sensitive.isEmpty()) null
                else AppPermissions(
                    packageName = info.packageName,
                    label = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName,
                    permissions = sensitive
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Scan the installed apps, diff against the stored baseline. If there is no baseline
     * (first run), seed the baseline silently so the first alert can only happen AFTER
     * the user has at least one chance to see the Watchdog screen in a known state.
     */
    fun evaluate(ctx: Context): EvaluationResult {
        val current = scanCurrent(ctx)
        val stored = loadSnapshot(ctx)
        val baselineAt = baselineCapturedAt(ctx)

        if (stored == null) {
            // First run: write the baseline, surface no diffs. The user sees "Baseline just captured".
            writeSnapshot(ctx, current)
            return EvaluationResult(
                baselineAt = System.currentTimeMillis(),
                currentApps = current,
                diffs = emptyList()
            )
        }

        val diffs = current.mapNotNull { app ->
            val previous = stored[app.packageName].orEmpty()
            val gained = app.permissions - previous
            if (gained.isEmpty()) null
            else Diff(app.packageName, app.label, gained)
        }

        return EvaluationResult(
            baselineAt = baselineAt,
            currentApps = current,
            diffs = diffs
        )
    }

    /** Called by "Mark as reviewed" / "Reset baseline" buttons. */
    fun resetBaseline(ctx: Context) {
        val current = scanCurrent(ctx)
        writeSnapshot(ctx, current)
    }

    fun markDismissedNow(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_LAST_DISMISSED, System.currentTimeMillis()).apply()
    }

    // ---- Internals ----

    private fun writeSnapshot(ctx: Context, apps: List<AppPermissions>) {
        val arr = JSONArray()
        apps.forEach { app ->
            val perms = JSONArray().apply { app.permissions.forEach { put(it) } }
            arr.put(
                JSONObject().apply {
                    put("pkg", app.packageName)
                    put("label", app.label)
                    put("perms", perms)
                }
            )
        }
        prefs(ctx).edit()
            .putString(KEY_SNAPSHOT, arr.toString())
            .putLong(KEY_SNAPSHOT_AT, System.currentTimeMillis())
            .apply()
    }

    /** `null` when no baseline exists; otherwise `packageName → set-of-permissions`. */
    private fun loadSnapshot(ctx: Context): Map<String, Set<String>>? {
        val raw = prefs(ctx).getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pkg = o.optString("pkg")
                    if (pkg.isEmpty()) continue
                    val perms = o.optJSONArray("perms") ?: continue
                    val set = buildSet {
                        for (j in 0 until perms.length()) add(perms.optString(j))
                    }
                    put(pkg, set)
                }
            }
        }.getOrNull()
    }

    /**
     * Pretty-print a permission constant for display. Keeps the trailing short name
     * (e.g. `android.permission.CAMERA` → `CAMERA`).
     */
    fun shortName(permission: String): String =
        permission.substringAfterLast('.')
}
