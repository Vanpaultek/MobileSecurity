// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.privacy.ProtectionSettings
import com.onlytrusted.app.security.AppLockManager
import com.onlytrusted.app.services.PocketDetectionService
import com.onlytrusted.app.services.SensorMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val sensorMonitorEnabled: Boolean = true,
    val pocketDetectionEnabled: Boolean = true,
    val screenshotProtectionEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val ownerTag: String = ""
)

class SettingsViewModel(private val app: OnlyTrustedApp) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        SettingsState(
            sensorMonitorEnabled = prefs.getBoolean(KEY_SENSOR, true),
            pocketDetectionEnabled = prefs.getBoolean(KEY_POCKET, true),
            screenshotProtectionEnabled = prefs.getBoolean(KEY_SCREENSHOT, true),
            appLockEnabled = AppLockManager.isEnabled(app),
            ownerTag = ProtectionSettings.ownerTag(app)
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun setAppLock(enabled: Boolean) {
        AppLockManager.setEnabled(app, enabled)
        if (!enabled) AppLockManager.resetUnlock(app)
        _state.value = _state.value.copy(appLockEnabled = enabled)
    }

    fun setOwnerTag(value: String) {
        val cleaned = com.onlytrusted.app.privacy.PhoneUtils.sanitiseOwnerTag(value)
        ProtectionSettings.setOwnerTag(app, cleaned)
        _state.value = _state.value.copy(ownerTag = ProtectionSettings.ownerTag(app))
    }

    fun setSensorMonitor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SENSOR, enabled).apply()
        _state.value = _state.value.copy(sensorMonitorEnabled = enabled)
        if (enabled) SensorMonitorService.start(app) else SensorMonitorService.stop(app)
    }

    fun setPocketDetection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POCKET, enabled).apply()
        _state.value = _state.value.copy(pocketDetectionEnabled = enabled)
        if (enabled) PocketDetectionService.start(app) else PocketDetectionService.stop(app)
    }

    fun setScreenshotProtection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREENSHOT, enabled).apply()
        _state.value = _state.value.copy(screenshotProtectionEnabled = enabled)
    }

    fun clearAllEvents() {
        viewModelScope.launch { app.repository.clearEvents() }
    }

    fun wipeEverything() {
        viewModelScope.launch {
            app.repository.wipeAll()
            app.trustedContacts.wipeAll()
            app.database.shieldSessionDao().deleteAll()
            prefs.edit().clear().apply()
            app.getSharedPreferences("protection_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            AppLockManager.setEnabled(app, false)
            AppLockManager.resetUnlock(app)
            _state.value = SettingsState()
        }
    }

    companion object {
        const val KEY_SENSOR = "sensor_monitor_enabled"
        const val KEY_POCKET = "pocket_detection_enabled"
        const val KEY_SCREENSHOT = "screenshot_protection_enabled"

        fun isSensorMonitorEnabled(app: Application): Boolean =
            app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(KEY_SENSOR, true)

        fun isPocketDetectionEnabled(app: Application): Boolean =
            app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(KEY_POCKET, true)

        fun isScreenshotProtectionEnabled(app: Application): Boolean =
            app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(KEY_SCREENSHOT, true)
    }
}
