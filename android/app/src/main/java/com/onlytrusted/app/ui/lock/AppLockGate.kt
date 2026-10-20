// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.onlytrusted.app.security.AppLockManager

@Composable
fun AppLockGate(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }
    val canAuth = remember { BiometricPrompter.canAuthenticate(context) }
    val lockEnabled = remember { AppLockManager.isEnabled(context) }

    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        if (!canAuth) {
            error = if (lockEnabled) {
                "This device no longer has a biometric or PIN set up. Disable App Lock below " +
                    "to recover, then re-enable it after setting one up in system settings."
            } else {
                "Panic lock engaged. Tap below to return to the app."
            }
            return@LaunchedEffect
        }
        attempted = true
        BiometricPrompter.prompt(
            activity = activity,
            title = "Unlock OnlyTrusted",
            subtitle = "Use your biometric or device PIN",
            onSuccess = onUnlocked,
            onFailure = { error = it }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Locked", style = MaterialTheme.typography.headlineMedium)
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (canAuth) {
            Button(
                onClick = {
                    val activity = context as? FragmentActivity ?: return@Button
                    error = null
                    attempted = true
                    BiometricPrompter.prompt(
                        activity = activity,
                        title = "Unlock OnlyTrusted",
                        subtitle = "Use your biometric or device PIN",
                        onSuccess = onUnlocked,
                        onFailure = { error = it }
                    )
                },
                modifier = Modifier.padding(top = 24.dp)
            ) { Text(if (attempted) "Try again" else "Unlock") }
        } else {
            // No biometric / PIN enrolled. Two cases:
            //  - App Lock was explicitly on → disable it so the owner isn't stuck.
            //  - App Lock was never on (e.g. Panic lock) → just let them back in.
            Button(
                onClick = {
                    if (lockEnabled) {
                        AppLockManager.setEnabled(context, false)
                    }
                    AppLockManager.resetUnlock(context)
                    onUnlocked()
                },
                modifier = Modifier.padding(top = 24.dp)
            ) { Text(if (lockEnabled) "Disable App Lock and continue" else "Return to app") }
        }
    }
}
