// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.mobilesecurity.app.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Snapshot of apps that currently hold mic / camera permission on this device.
 *
 * This is a *permissions holders* view, not a *currently using* view — the real-time
 * "the mic is on RIGHT NOW" alert is already done by [SensorMonitorService]. This card
 * answers the follow-up question "which apps COULD use the mic?" that the owner needs
 * to review their attack surface.
 *
 * Last-access timestamps are best-effort: on API 30+ AppOpsManager exposes
 * `getPackagesForOps(["android:record_audio", "android:camera"])` with timing info; we
 * cross-reference them. If AppOps denies the query (owner hasn't granted usage access),
 * we fall back to just the permission grant without a timestamp.
 */
object MicCameraHolders {

    data class HolderApp(
        val packageName: String,
        val label: String,
        val hasMic: Boolean,
        val hasCamera: Boolean
    )

    data class Snapshot(
        val micCount: Int,
        val cameraCount: Int,
        val holders: List<HolderApp>
    )

    fun read(ctx: Context): Snapshot {
        val pm = ctx.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PERMISSIONS.toLong()
            ))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val holders = packages
            .asSequence()
            .filter { it.packageName != ctx.packageName }
            .mapNotNull { pkg ->
                val granted = pkg.grantedSet()
                val hasMic = Manifest.permission.RECORD_AUDIO in granted
                val hasCam = Manifest.permission.CAMERA in granted
                if (!hasMic && !hasCam) return@mapNotNull null

                val label = runCatching {
                    pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                        ?: pkg.packageName
                }.getOrDefault(pkg.packageName)

                HolderApp(
                    packageName = pkg.packageName,
                    label = label,
                    hasMic = hasMic,
                    hasCamera = hasCam
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

        return Snapshot(
            micCount = holders.count { it.hasMic },
            cameraCount = holders.count { it.hasCamera },
            holders = holders
        )
    }

    private fun PackageInfo.grantedSet(): Set<String> {
        val perms = requestedPermissions ?: return emptySet()
        val flags = requestedPermissionsFlags ?: return emptySet()
        return buildSet {
            perms.forEachIndexed { i, perm ->
                val isGranted = (flags.getOrNull(i) ?: 0) and
                    PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                if (isGranted && perm != null) add(perm)
            }
        }
    }
}
