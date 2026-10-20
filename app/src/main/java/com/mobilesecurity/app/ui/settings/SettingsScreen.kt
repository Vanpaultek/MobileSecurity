package com.mobilesecurity.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobilesecurity.app.MobileSecurityApp
import com.mobilesecurity.app.export.DataExporter
import com.mobilesecurity.app.export.EvidenceExporter
import com.mobilesecurity.app.ui.lock.BiometricPrompter
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit = {},
    onOpenPosture: () -> Unit = {},
    onOpenShieldLog: () -> Unit = {},
    onOpenLegal: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as MobileSecurityApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app) }
    })
    val state by vm.state.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmWipe by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching { DataExporter.exportTo(context, uri) }
                .map { "Exported $it rows to the file you chose." }
                .getOrElse { "Export failed: ${it.message ?: "unknown error"}" }
            snackbarHostState.showSnackbar(message)
        }
    }

    val evidenceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching { EvidenceExporter.exportTo(context, uri) }
                .map { "Evidence pack ready. Share only with your carrier, bank, or law enforcement — never with the suspected caller." }
                .getOrElse { "Evidence export failed: ${it.message ?: "unknown error"}" }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingToggle(
            title = "Mic + camera monitoring",
            subtitle = "Alerts you when another app accesses your microphone or camera.",
            checked = state.sensorMonitorEnabled,
            onChange = vm::setSensorMonitor
        )
        SettingToggle(
            title = "Pocket-dial protection",
            subtitle = "Uses proximity and motion to detect when the phone is in your pocket.",
            checked = state.pocketDetectionEnabled,
            onChange = vm::setPocketDetection
        )
        SettingToggle(
            title = "Screenshot protection (this app only)",
            subtitle = "Prevents screenshots and screen recording of this app's screens. " +
                "Other apps' screens are controlled by those apps.",
            checked = state.screenshotProtectionEnabled,
            onChange = vm::setScreenshotProtection
        )
        SettingToggle(
            title = "App lock",
            subtitle = "Require your biometric or device PIN to open MobileSecurity.",
            checked = state.appLockEnabled,
            onChange = { enable ->
                if (enable && !BiometricPrompter.canAuthenticate(context)) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "Set up a device PIN, pattern, or biometric in system settings first " +
                                "— otherwise App Lock would permanently lock you out."
                        )
                    }
                    return@SettingToggle
                }
                vm.setAppLock(enable)
            }
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Shield watermark", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Owner tag printed in the on-screen watermark. Short labels work best — " +
                        "your initials or a nickname (max 24 chars).",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = state.ownerTag,
                    onValueChange = vm::setOwnerTag,
                    singleLine = true,
                    label = { Text("Owner tag") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onOpenShieldLog,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Shield session log") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("System permissions", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Some features need you to pick this app in system settings:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open app settings") }
                OutlinedButton(
                    onClick = {
                        val intent = Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Set as default caller ID & spam app") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Data", style = MaterialTheme.typography.titleLarge)
                Text(
                    "All event logs and the block list are stored encrypted on this device. " +
                        "The app does not use the internet and does not sync to any cloud.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = {
                        exportLauncher.launch(
                            "mobilesecurity-${System.currentTimeMillis()}.json"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export my data (JSON)") }
                OutlinedButton(
                    onClick = {
                        evidenceLauncher.launch(
                            "evidence-${System.currentTimeMillis()}.json"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export evidence pack (for carrier / bank / police)") }
                TextButton(onClick = { vm.clearAllEvents() }) {
                    Text("Clear event history")
                }
                TextButton(onClick = { confirmWipe = true }) {
                    Text("Wipe everything")
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("More", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Permission audit") }
                OutlinedButton(
                    onClick = onOpenPosture,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Device posture check") }
                OutlinedButton(
                    onClick = onOpenLegal,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Privacy, terms & honest claims") }
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("About & attributions") }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wipe everything?") },
            text = {
                Text(
                    "This will delete every event, every blocked number, every trusted contact, " +
                        "and every setting stored by this app. It cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    vm.wipeEverything()
                    scope.launch { snackbarHostState.showSnackbar("Everything wiped.") }
                }) { Text("Wipe") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            }
        )
    }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
