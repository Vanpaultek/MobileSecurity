package com.mobilesecurity.app.ui.lock

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

@Composable
fun AppLockGate(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        if (!BiometricPrompter.canAuthenticate(activity)) {
            error = "No biometric or device PIN set up. Add one in system settings to use App Lock."
            return@LaunchedEffect
        }
        attempted = true
        BiometricPrompter.prompt(
            activity = activity,
            title = "Unlock MobileSecurity",
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
        Button(
            onClick = {
                val activity = context as? FragmentActivity ?: return@Button
                error = null
                attempted = true
                BiometricPrompter.prompt(
                    activity = activity,
                    title = "Unlock MobileSecurity",
                    subtitle = "Use your biometric or device PIN",
                    onSuccess = onUnlocked,
                    onFailure = { error = it }
                )
            },
            modifier = Modifier.padding(top = 24.dp)
        ) { Text(if (attempted) "Try again" else "Unlock") }
    }
}
