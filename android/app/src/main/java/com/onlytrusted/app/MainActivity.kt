// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onlytrusted.app.privacy.OnboardingState
import com.onlytrusted.app.security.AppLockManager
import com.onlytrusted.app.services.PocketDetectionService
import com.onlytrusted.app.services.SensorMonitorService
import com.onlytrusted.app.ui.lock.AppLockGate
import com.onlytrusted.app.ui.navigation.AppNavigation
import com.onlytrusted.app.ui.onboarding.ConsentScreen
import com.onlytrusted.app.ui.onboarding.OnboardingScreen
import com.onlytrusted.app.ui.settings.SettingsViewModel
import com.onlytrusted.app.ui.splash.SplashScreen
import com.onlytrusted.app.ui.theme.OnlyTrustedTheme
import com.onlytrusted.app.util.PermissionHelper

class MainActivity : FragmentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionsResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotProtection()

        setContent {
            OnlyTrustedTheme {
                var splashDone by remember { mutableStateOf(false) }
                var onboarded by remember { mutableStateOf(OnboardingState.hasOnboarded(this)) }
                var consented by remember { mutableStateOf(OnboardingState.hasAccepted(this)) }
                val lifecycleOwner = LocalLifecycleOwner.current
                var unlocked by remember { mutableStateOf(!AppLockManager.shouldPrompt(this)) }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME &&
                            AppLockManager.shouldPrompt(this@MainActivity)
                        ) {
                            unlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                when {
                    !splashDone -> SplashScreen(onDone = { splashDone = true })
                    !onboarded -> OnboardingScreen(onContinue = { onboarded = true })
                    !consented -> ConsentScreen(onConfirmed = { consented = true })
                    !unlocked -> AppLockGate(onUnlocked = {
                        AppLockManager.markUnlocked(this)
                        unlocked = true
                    })
                    else -> AppNavigation(onPanic = {
                        AppLockManager.resetUnlock(this)
                        unlocked = false
                    })
                }
            }
        }

        requestCorePermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        applyScreenshotProtection()
        maybeStartServices()
    }

    private fun applyScreenshotProtection() {
        if (SettingsViewModel.isScreenshotProtectionEnabled(application)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun requestCorePermissionsIfNeeded() {
        val missing = PermissionHelper.missing(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            onPermissionsResult()
        }
    }

    private fun onPermissionsResult() {
        maybeStartServices()
    }

    private fun maybeStartServices() {
        if (!canPostNotifications()) return

        if (SettingsViewModel.isSensorMonitorEnabled(application) && hasSensorPrerequisites()) {
            SensorMonitorService.start(this)
        }
        if (SettingsViewModel.isPocketDetectionEnabled(application)) {
            PocketDetectionService.start(this)
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasSensorPrerequisites(): Boolean {
        val micOk = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val camOk = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        return micOk || camOk
    }

}
