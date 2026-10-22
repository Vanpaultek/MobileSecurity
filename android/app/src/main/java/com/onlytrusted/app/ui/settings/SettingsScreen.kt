// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onlytrusted.app.OnlyTrustedApp
import com.onlytrusted.app.export.DataExporter
import com.onlytrusted.app.export.EvidenceExporter
import com.onlytrusted.app.security.AppLockManager
import com.onlytrusted.app.ui.lock.BiometricPrompter
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark
import kotlinx.coroutines.launch

// Blue + Purple used by the Settings icon chips (match iOS neon mockup).
private val settingsBlue   = Color(0xFF2266F0)
private val settingsPurple = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit = {},
    onOpenPosture: () -> Unit = {},
    onOpenShieldLog: () -> Unit = {},
    onOpenLegal: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenBilling: () -> Unit = {},
    onOpenUpgrade: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as OnlyTrustedApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app) }
    })
    val state by vm.state.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmPanic by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var lastExport by remember { mutableStateOf<(() -> Unit)?>(null) }
    var themeSheetOpen by remember { mutableStateOf(false) }
    val themeSheetState = rememberModalBottomSheetState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { DataExporter.exportTo(context, uri) }
                .onSuccess { snackbarHostState.showSnackbar("Exported $it rows.") }
                .onFailure { exportError = it.message ?: "unknown error" }
        }
    }
    val evidenceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { EvidenceExporter.exportTo(context, uri) }
                .onSuccess { snackbarHostState.showSnackbar("Evidence pack ready.") }
                .onFailure { exportError = it.message ?: "unknown error" }
        }
    }

    if (exportError != null) {
        com.onlytrusted.app.ui.common.ErrorStateView(
            message = "We couldn't complete the export. ${exportError ?: ""}",
            onTryAgain = lastExport?.let { work -> { exportError = null; work() } },
            onGoBack = { exportError = null }
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pinned top header — opaque, extends to status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MidnightBackground,
                                MidnightBackground.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextDark
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // --- Protection (iOS-aligned: BLUE gradient chips) ---
            SectionHeader("Protection")
            SettingsCard {
                SettingsNavRow(
                    icon = Icons.Filled.Shield,
                    tint = settingsBlue,
                    title = "Blocking & Unknown",
                    onClick = onOpenPermissions
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Notifications,
                    tint = settingsBlue,
                    title = "Notifications",
                    onClick = onOpenNotifications
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Phone,
                    tint = settingsBlue,
                    title = "Call Handling",
                    onClick = onOpenPermissions
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.AutoMirrored.Filled.Message,
                    tint = settingsBlue,
                    title = "Text Handling",
                    onClick = onOpenPermissions
                )
            }

            // --- Premium (BLUE chips, with the Upgrade row using gold tint) ---
            SectionHeader("Premium")
            SettingsCard {
                SettingsNavRow(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    tint = settingsBlue,
                    title = "Reports",
                    onClick = onOpenReports
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.CreditCard,
                    tint = settingsBlue,
                    title = "Billing & Subscription",
                    onClick = onOpenBilling
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.EmojiEvents,
                    tint = subGold,
                    title = "Upgrade to Premium",
                    onClick = onOpenUpgrade
                )
            }

            // --- Appearance (iOS-aligned: PURPLE gradient chips) ---
            SectionHeader("Appearance")
            SettingsCard {
                SettingsNavRow(
                    icon = Icons.Filled.Palette,
                    tint = settingsPurple,
                    title = "App Theme",
                    value = "Dark (System)",
                    onClick = { themeSheetOpen = true }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Language,
                    tint = settingsPurple,
                    title = "Language",
                    value = "English",
                    onClick = {
                        val intent = Intent(Settings.ACTION_LOCALE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.TextFields,
                    tint = settingsPurple,
                    title = "Text Size",
                    value = "System",
                    onClick = {
                        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            // --- Security & data (Android-preserved functionality) ---
            SectionHeader("Security & data")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Filled.Lock,
                    tint = BrandAccentDark,
                    title = "App Lock",
                    detail = "Require biometric or device PIN to open.",
                    checked = state.appLockEnabled,
                    onChange = { enable ->
                        if (enable && !BiometricPrompter.canAuthenticate(context)) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Set up a device PIN, pattern, or biometric first."
                                )
                            }
                            return@ToggleRow
                        }
                        vm.setAppLock(enable)
                    }
                )
                RowDivider()
                ToggleRow(
                    icon = Icons.Filled.Shield,
                    tint = BrandAccentDark,
                    title = "Sensor monitoring",
                    detail = "Alerts when another app uses mic or camera.",
                    checked = state.sensorMonitorEnabled,
                    onChange = vm::setSensorMonitor
                )
                RowDivider()
                ToggleRow(
                    icon = Icons.Filled.Phone,
                    tint = BrandAccentDark,
                    title = "Pocket-dial protection",
                    detail = "Uses proximity and motion to detect pocket.",
                    checked = state.pocketDetectionEnabled,
                    onChange = vm::setPocketDetection
                )
                RowDivider()
                ToggleRow(
                    icon = Icons.Filled.Shield,
                    tint = BrandAccentDark,
                    title = "Screenshot protection",
                    detail = "Blocks screenshots of this app's screens.",
                    checked = state.screenshotProtectionEnabled,
                    onChange = vm::setScreenshotProtection
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.FileUpload,
                    tint = BrandAccentDark,
                    title = "Export my data (JSON)",
                    showChevron = false,
                    onClick = {
                        val action = {
                            exportLauncher.launch("onlytrusted-${System.currentTimeMillis()}.json")
                        }
                        lastExport = action
                        action()
                    }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.FileUpload,
                    tint = BrandAccentDark,
                    title = "Export evidence pack",
                    showChevron = false,
                    onClick = {
                        val action = {
                            evidenceLauncher.launch("evidence-${System.currentTimeMillis()}.json")
                        }
                        lastExport = action
                        action()
                    }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Lock,
                    tint = WarningDark,
                    title = "Panic lock now",
                    titleTint = WarningDark,
                    showChevron = false,
                    onClick = { confirmPanic = true }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Delete,
                    tint = DangerDark,
                    title = "Wipe everything",
                    titleTint = DangerDark,
                    showChevron = false,
                    onClick = { confirmWipe = true }
                )
            }

            // --- Shield watermark (Android-only feature) ---
            SectionHeader("Shield watermark")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Owner tag printed in the on-screen watermark. Short labels work best.",
                        fontSize = 12.sp,
                        color = SecondaryTextDark
                    )
                    OutlinedTextField(
                        value = state.ownerTag,
                        onValueChange = vm::setOwnerTag,
                        singleLine = true,
                        label = { Text("Owner tag") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsNavRow(
                        icon = Icons.Filled.Shield,
                        tint = BrandAccentDark,
                        title = "Shield session log",
                        onClick = onOpenShieldLog
                    )
                }
            }

            // --- More ---
            SectionHeader("More")
            SettingsCard {
                SettingsNavRow(
                    icon = Icons.Filled.Shield,
                    tint = BrandAccentDark,
                    title = "Permission audit",
                    onClick = onOpenPermissions
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Shield,
                    tint = BrandAccentDark,
                    title = "Device posture check",
                    onClick = onOpenPosture
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Policy,
                    tint = BrandAccentDark,
                    title = "Privacy, terms & honest claims",
                    onClick = onOpenLegal
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Info,
                    tint = BrandAccentDark,
                    title = "About OnlyTrusted",
                    onClick = onOpenAbout
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("OnlyTrusted", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SecondaryTextDark)
                Text("© 2026 VanPaulTek Inc · GNU AGPL-3.0", fontSize = 10.sp, color = TertiaryTextDark)
                Text("admin@vanpaultek.com", fontSize = 10.sp, color = TertiaryTextDark)
            }
            }
        }

        if (confirmPanic) {
            AlertDialog(
                onDismissRequest = { confirmPanic = false },
                title = { Text("Panic lock now?") },
                text = {
                    Text("The app will lock immediately and require your biometric or device PIN to reopen.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPanic = false
                        AppLockManager.resetUnlock(context)
                        scope.launch { snackbarHostState.showSnackbar("App locked.") }
                    }) { Text("Lock app") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmPanic = false }) { Text("Cancel") }
                }
            )
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

        if (themeSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { themeSheetOpen = false },
                sheetState = themeSheetState,
                containerColor = MidnightCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "App theme",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryTextDark
                    )
                    Text(
                        "OnlyTrusted is designed dark-first. 'System' follows the device setting.",
                        fontSize = 12.sp,
                        color = SecondaryTextDark
                    )
                    Spacer(Modifier.height(4.dp))
                    RadioOption("System", true)
                    RadioOption("Dark", false)
                    RadioOption("Light", false)
                }
            }
        }
    }
}

// ========== Row primitives ==========

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = SecondaryTextDark,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

/**
 * Gradient icon-chip row. Matches iOS SettingsNavRow (chip style).
 * - 44x44 chip, RoundedCornerShape(11.dp)
 * - LinearGradient from tint → tint.alpha=0.75
 * - Drop shadow in the tint color
 * - White icon on top
 * - Title (16sp semibold), optional value in BrandCyanDark (14sp), optional chevron
 */
@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String? = null,
    titleTint: Color = PrimaryTextDark,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconChip(icon = icon, tint = tint)
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleTint,
                modifier = Modifier.weight(1f)
            )
            if (value != null) {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandCyanDark
                )
            }
            if (showChevron) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = TertiaryTextDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun IconChip(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(11.dp),
                ambientColor = tint,
                spotColor = tint
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(tint, tint.copy(alpha = 0.75f)),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                ),
                shape = RoundedCornerShape(11.dp)
            )
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconChip(icon = icon, tint = tint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
            Text(detail, fontSize = 12.sp, color = SecondaryTextDark)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandCyanDark
            )
        )
    }
}

@Composable
private fun RowDivider() {
    Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 72.dp))
}

@Composable
private fun RadioOption(label: String, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = if (selected) BrandAccentDark else Color.Transparent,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Text(label, fontSize = 14.sp, color = PrimaryTextDark)
    }
}
