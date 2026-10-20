// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlytrusted.app.OnlyTrustedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppPermissionRow(
    val packageName: String,
    val label: String,
    val mic: Boolean,
    val camera: Boolean,
    val fineLocation: Boolean,
    val sms: Boolean,
    val phone: Boolean,
    val contacts: Boolean
) {
    val sensitiveCount: Int = listOf(mic, camera, fineLocation, sms, phone, contacts).count { it }
}

data class PermissionAuditState(
    val loading: Boolean = true,
    val rows: List<AppPermissionRow> = emptyList(),
    val onlyWithMicOrCam: Boolean = true
)

class PermissionAuditViewModel(private val app: OnlyTrustedApp) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PermissionAuditState())
    val state: StateFlow<PermissionAuditState> = _state.asStateFlow()

    init { refresh() }

    fun setFilter(onlyWithMicOrCam: Boolean) {
        _state.value = _state.value.copy(onlyWithMicOrCam = onlyWithMicOrCam)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val rows = withContext(Dispatchers.Default) { collect(app) }
            _state.value = _state.value.copy(loading = false, rows = rows)
        }
    }

    private fun collect(ctx: Context): List<AppPermissionRow> {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }
        return packages
            .asSequence()
            .filter { it.requestedPermissions != null }
            .filter { it.packageName != ctx.packageName }
            .map { pkg ->
                val granted = pkg.granted()
                AppPermissionRow(
                    packageName = pkg.packageName,
                    label = runCatching {
                        pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                            ?: pkg.packageName
                    }.getOrDefault(pkg.packageName),
                    mic = Manifest.permission.RECORD_AUDIO in granted,
                    camera = Manifest.permission.CAMERA in granted,
                    fineLocation = Manifest.permission.ACCESS_FINE_LOCATION in granted ||
                        Manifest.permission.ACCESS_COARSE_LOCATION in granted,
                    sms = Manifest.permission.RECEIVE_SMS in granted ||
                        Manifest.permission.READ_SMS in granted,
                    phone = Manifest.permission.READ_PHONE_STATE in granted ||
                        Manifest.permission.READ_CALL_LOG in granted,
                    contacts = Manifest.permission.READ_CONTACTS in granted
                )
            }
            .filter { it.sensitiveCount > 0 }
            .sortedWith(compareByDescending<AppPermissionRow> { it.sensitiveCount }.thenBy { it.label.lowercase() })
            .toList()
    }

    private fun PackageInfo.granted(): Set<String> {
        val requested = requestedPermissions ?: return emptySet()
        val flags = requestedPermissionsFlags ?: return emptySet()
        return buildSet {
            requested.forEachIndexed { i, perm ->
                val isGranted = (flags.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                if (isGranted && perm != null) add(perm)
            }
        }
    }
}
