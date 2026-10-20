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
package com.mobilesecurity.app.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobilesecurity.app.MobileSecurityApp

@Composable
fun DashboardScreen(
    onPanic: () -> Unit = {},
    onGoToVault: () -> Unit = {},
    onGoToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as MobileSecurityApp
    val vm: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer { DashboardViewModel(app) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val runtime by vm.runtimeState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.refreshRuntime()
        vm.verifyAuditChain()
    }
    LaunchedEffect(state.trustedContactCount) {
        vm.setHasAnyTrustedContact(state.trustedContactCount > 0)
    }

    val steps = buildList {
        add(QuickStep("Add yourself + family to the vault", runtime.hasAnyTrustedContact, onGoToVault))
        add(QuickStep(
            "Make MobileSecurity the default caller-ID & spam app",
            runtime.isDefaultCallScreener
        ) {
            val intent = Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        })
        add(QuickStep("Turn on Trusted-only calls", runtime.trustedOnlyCalls) {
            vm.setTrustedOnlyCalls(true)
        })
        add(QuickStep("Turn on App Lock", runtime.appLockEnabled, onGoToSettings))
    }
    val setupDone = steps.all { it.done }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderCard(
            setupDone = setupDone,
            auditOk = runtime.auditChainIntact,
            onPanic = {
                vm.triggerPanicLock(context)
                onPanic()
            }
        )

        if (!setupDone) {
            QuickSetupCard(steps = steps)
        }

        TrustedModeCard(
            calls = runtime.trustedOnlyCalls,
            sms = runtime.trustedOnlySms,
            contactCount = state.trustedContactCount,
            onToggleCalls = vm::setTrustedOnlyCalls,
            onToggleSms = vm::setTrustedOnlySms,
            onManageVault = onGoToVault
        )

        ShieldOverlayCard(
            canDraw = runtime.canDrawOverlays,
            ownerTag = runtime.ownerTag,
            onStart = {
                if (vm.startShield()) {
                    // overlay started; no additional UI feedback needed — it's visible on screen
                }
            },
            onStop = vm::stopShield,
            onRequestPermission = vm::openOverlayPermission,
            onConfigureTag = onGoToSettings
        )

        SensorOwnershipCard(
            micMuted = runtime.sensorPrivacy.micMuted,
            cameraMuted = runtime.sensorPrivacy.cameraMuted,
            onOpenPrivacy = {
                val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniStat(Modifier.weight(1f), Icons.Filled.Mic, state.micEvents24h, "Mic uses")
            MiniStat(Modifier.weight(1f), Icons.Filled.CameraAlt, state.cameraEvents24h, "Camera uses")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniStat(Modifier.weight(1f), Icons.Filled.Block, state.blockedCalls24h, "Calls blocked")
            MiniStat(Modifier.weight(1f), Icons.Filled.Sms, state.flaggedSms24h, "Spam SMS")
        }
    }
}

@Composable
private fun HeaderCard(
    setupDone: Boolean,
    auditOk: Boolean?,
    onPanic: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null)
                Text(
                    if (setupDone) "You're protected" else "Finish setup to be protected",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                if (setupDone)
                    "Mic and camera belong to you. Trusted-only is on. Audit chain " +
                        (if (auditOk == false) "tampered." else "intact.")
                else "Complete the Quick Setup below to switch on the full protections.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onPanic,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text("Panic lock", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private data class QuickStep(
    val label: String,
    val done: Boolean,
    val action: () -> Unit
)

@Composable
private fun QuickSetupCard(steps: List<QuickStep>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick setup", style = MaterialTheme.typography.titleLarge)
            steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (step.done) Icons.Filled.CheckCircle
                            else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (step.done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        step.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                    if (!step.done) {
                        TextButton(onClick = step.action) {
                            Text("Do it")
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustedModeCard(
    calls: Boolean,
    sms: Boolean,
    contactCount: Int,
    onToggleCalls: (Boolean) -> Unit,
    onToggleSms: (Boolean) -> Unit,
    onManageVault: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, contentDescription = null)
                Text(
                    "Trusted-only mode",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                TextButton(onClick = onManageVault) { Text("Vault →") }
            }
            Text("$contactCount trusted contact(s).", style = MaterialTheme.typography.bodyMedium)
            SwitchRow("Silence calls not in vault", calls, onToggleCalls)
            SwitchRow("Flag SMS not in vault", sms, onToggleSms)
            if (contactCount == 0) {
                Text(
                    "Add family first — turning this on with an empty vault will silence everyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SensorOwnershipCard(
    micMuted: Boolean?,
    cameraMuted: Boolean?,
    onOpenPrivacy: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Verified, contentDescription = null)
                Text(
                    "Mic & camera ownership",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            SensorRow("Mic", micMuted)
            SensorRow("Camera", cameraMuted)
            TextButton(onClick = onOpenPrivacy) {
                Text("Open system Privacy settings")
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SensorRow(label: String, muted: Boolean?) {
    val (text, tint) = when (muted) {
        true -> "muted — hardware off" to MaterialTheme.colorScheme.primary
        false -> "available to granted apps" to MaterialTheme.colorScheme.outline
        null -> "state unknown" to MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tint)
        Text("$label: $text",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ShieldOverlayCard(
    canDraw: Boolean,
    ownerTag: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit,
    onConfigureTag: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null)
                Text(
                    "Shield watermark",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                "Paints a diagonal watermark across the whole screen so any screenshot or " +
                    "screen recording someone takes while this is on carries your tag and a " +
                    "unique session code. Match the code to the Shield log if a leak appears.",
                style = MaterialTheme.typography.bodyMedium
            )
            when {
                !canDraw -> {
                    Text(
                        "Allow MobileSecurity to draw over other apps so the watermark can appear " +
                            "on top of your video-call or messaging app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant overlay permission")
                    }
                }
                ownerTag.isBlank() -> {
                    Text(
                        "Set an owner tag (your initials or a nickname) so you can tell your " +
                            "sessions apart from everyone else's.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onConfigureTag, modifier = Modifier.fillMaxWidth()) {
                        Text("Set owner tag in Settings")
                    }
                }
                else -> {
                    Text(
                        "Tag: $ownerTag",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                            Text("Start Shield")
                        }
                        TextButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Text("Stop")
                        }
                    }
                    Text(
                        "Tip: this app cannot encrypt a cellular call — carriers own that layer. " +
                            "For end-to-end encrypted voice/video, use Signal or WhatsApp, then " +
                            "turn Shield on so the screen itself is still watermarked.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: Int,
    label: String
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null)
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Unspecified)
        }
    }
}
