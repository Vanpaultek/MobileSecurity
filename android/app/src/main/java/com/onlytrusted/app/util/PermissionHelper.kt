// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {

    val coreRuntimePermissions: Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context): List<String> =
        coreRuntimePermissions.filterNot { hasPermission(context, it) }
}
