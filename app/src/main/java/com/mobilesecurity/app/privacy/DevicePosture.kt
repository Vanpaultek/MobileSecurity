/*
 * Copyright (C) 2026 57Million LLC
 *
 * This file is part of MobileSecurity.
 *
 * MobileSecurity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MobileSecurity is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.mobilesecurity.app.privacy

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Read-only snapshot of the owner's own device. We never scan or probe anyone else's device.
 */
object DevicePosture {

    data class Snapshot(
        val adbEnabled: Boolean,
        val developerOptionsOn: Boolean,
        val deviceSecure: Boolean,
        val likelyRooted: Boolean,
        val canInstallFromUnknownSources: Boolean,
        val timestampMs: Long = System.currentTimeMillis(),
        val riskyAppCount: Int = 0,
        val riskyApps: List<RiskyApp> = emptyList()
    ) {
        val issues: List<String> = buildList {
            if (adbEnabled) add("USB debugging (ADB) is on — a plugged-in computer can run commands on your phone.")
            if (developerOptionsOn) add("Developer options are enabled.")
            if (!deviceSecure) add("No lock screen is set — anyone who picks up the phone can open it.")
            if (likelyRooted) add("Device looks rooted. Rooted phones can bypass app sandboxing.")
            if (canInstallFromUnknownSources) add("At least one app is allowed to install software from outside the Play Store.")
            if (riskyAppCount > 0) add("$riskyAppCount app(s) hold both microphone and camera access.")
        }
        val postureOk: Boolean get() = issues.isEmpty()
    }

    data class RiskyApp(val packageName: String, val label: String)

    fun read(ctx: Context): Snapshot {
        val adb = Settings.Global.getInt(
            ctx.contentResolver, Settings.Global.ADB_ENABLED, 0
        ) != 0
        val dev = Settings.Global.getInt(
            ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        val secure = keyguard?.isDeviceSecure == true
        val rooted = detectRoot()
        val unknownOk = canInstallFromUnknownSources(ctx)
        val risky = collectRiskyApps(ctx)
        return Snapshot(
            adbEnabled = adb,
            developerOptionsOn = dev,
            deviceSecure = secure,
            likelyRooted = rooted,
            canInstallFromUnknownSources = unknownOk,
            riskyAppCount = risky.size,
            riskyApps = risky
        )
    }

    private fun detectRoot(): Boolean {
        val suPaths = arrayOf(
            "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su"
        )
        if (suPaths.any { File(it).exists() }) return true

        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) return true

        return runCatching {
            val process = Runtime.getRuntime().exec("which su")
            process.waitFor()
            process.inputStream.bufferedReader().readLine()?.isNotBlank() == true
        }.getOrDefault(false)
    }

    private fun canInstallFromUnknownSources(ctx: Context): Boolean {
        return runCatching {
            // Any installed app that holds REQUEST_INSTALL_PACKAGES AND has been granted
            // "install unknown apps" by the user is a sideload vector.
            val pm = ctx.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                ))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
            packages.any { info ->
                val granted = info.granted()
                android.Manifest.permission.REQUEST_INSTALL_PACKAGES in granted
            }
        }.getOrDefault(false)
    }

    private fun collectRiskyApps(ctx: Context): List<RiskyApp> {
        return runCatching {
            val pm = ctx.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                ))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
            packages
                .filter { it.packageName != ctx.packageName }
                .mapNotNull { pkg ->
                    val granted = pkg.granted()
                    if (android.Manifest.permission.RECORD_AUDIO in granted &&
                        android.Manifest.permission.CAMERA in granted
                    ) {
                        val label = runCatching {
                            pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                                ?: pkg.packageName
                        }.getOrDefault(pkg.packageName)
                        RiskyApp(packageName = pkg.packageName, label = label)
                    } else null
                }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun android.content.pm.PackageInfo.granted(): Set<String> {
        val perms = requestedPermissions ?: return emptySet()
        val flags = requestedPermissionsFlags ?: return emptySet()
        return buildSet {
            perms.forEachIndexed { i, perm ->
                val isGranted = (flags.getOrNull(i) ?: 0) and
                    android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                if (isGranted && perm != null) add(perm)
            }
        }
    }
}
