// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.identity

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

/** Identity Protection catalog (FREE). Mirrors iOS `IdentityProtectionScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityProtectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    if (!com.onlytrusted.app.ui.subscription.SubscriptionState.hasPremium(context)) {
        com.onlytrusted.app.ui.subscription.PremiumGatedScaffold(
            title = "Identity Protection",
            featureName = "Identity Protection",
            description = "Checklist of credit freezes, fraud alerts, HIBP enrollment, FTC report, and SSA account block. Each row deep-links to the official self-service portal.",
            onBack = onBack
        )
        return
    }

    var done by remember { mutableStateOf(IdentityActionsProgress.done(context)) }
    var reminders by remember { mutableStateOf(IdentityActionsProgress.reminders(context)) }
    var showMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val isPremium = com.onlytrusted.app.ui.subscription.SubscriptionState.hasPremium(context)

    val total = IdentityActionsCatalog.all.size
    val completed = done.size

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identity Protection", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "More", tint = BrandCyanDark)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Reset all checkmarks") },
                                onClick = {
                                    showMenu = false
                                    showResetDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderRow(Icons.Filled.VerifiedUser, "Identity Protection", BrandAccentDark)
                    Text(
                        "A checklist of credit freezes, fraud alerts, and ID-theft reports " +
                            "you can file yourself. Each row deep-links to the official self-" +
                            "service portal.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                    Text(
                        "We don't contact any bureau for you. Checkmarks live only on this device.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandAccentDark
                    )
                }
            }

            // Progress
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Progress", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$completed / $total",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAccentDark
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else completed.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandAccentDark,
                        trackColor = TertiaryTextDark
                    )
                }
            }

            // Phase sections
            IdentityActionsCatalog.grouped().forEach { (phase, actions) ->
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                phase.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTextDark
                            )
                            Text(phase.subtitle, fontSize = 11.sp, color = SecondaryTextDark)
                        }
                        actions.forEach { action ->
                            ActionRow(
                                action = action,
                                isDone = done.contains(action.id),
                                isPremium = isPremium,
                                reminderOn = reminders.containsKey(action.id),
                                onToggle = {
                                    IdentityActionsProgress.toggle(context, action.id)
                                    done = IdentityActionsProgress.done(context)
                                },
                                onOpen = { openUrl(context, action.url) },
                                onToggleReminder = {
                                    if (reminders.containsKey(action.id)) {
                                        IdentityActionsProgress.clearReminder(context, action.id)
                                    } else {
                                        IdentityActionsProgress.setReminder(context, action.id, action.title)
                                    }
                                    reminders = IdentityActionsProgress.reminders(context)
                                }
                            )
                        }
                    }
                }
            }

            // How it works
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderRow(Icons.Filled.Info, "What this is (and isn't)", SecondaryTextDark)
                    Text(
                        "This is a visibility and shortcut tool. Tapping \"Open\" launches the " +
                            "agency's own website or app. Nothing on your device is shared with " +
                            "us or with any bureau.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                    Text(
                        "If you want a paid monitoring service to do these calls for you, pick " +
                            "a provider (Aura, IdentityIQ, LifeLock) directly — we don't resell " +
                            "any of them.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all your checkmarks?", color = PrimaryTextDark) },
            text = {
                Text(
                    "This clears every \"done\" marker from this device. You won't lose any " +
                        "work you've already filed with the bureaus or agencies.",
                    color = SecondaryTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    IdentityActionsProgress.clearAll(context)
                    done = emptySet()
                    showResetDialog = false
                }) {
                    Text("Reset", color = BrandAccentDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = TertiaryTextDark)
                }
            },
            containerColor = MidnightElevated
        )
    }
}

@Composable
private fun ActionRow(
    action: IdentityAction,
    isDone: Boolean,
    isPremium: Boolean,
    reminderOn: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onToggleReminder: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (isDone) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = if (isDone) "Done" else "Not done",
                tint = if (isDone) BrandAccentDark else SecondaryTextDark,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onToggle)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    action.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDone) SecondaryTextDark else PrimaryTextDark,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    "${action.organisation} · ${action.timeEstimate}",
                    fontSize = 11.sp, color = SecondaryTextDark
                )
                Text(action.notes, fontSize = 11.sp, color = SecondaryTextDark)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 34.dp)
        ) {
            TextButton(
                onClick = onOpen,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.OpenInNew, null, tint = BrandCyanDark, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
                Text("Open ${action.organisation}", color = BrandCyanDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            if (isPremium) {
                TextButton(
                    onClick = onToggleReminder,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        if (reminderOn) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                        null,
                        tint = if (reminderOn) BrandAccentDark else SecondaryTextDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        if (reminderOn) "Reminder set" else "Remind yearly",
                        color = if (reminderOn) BrandAccentDark else SecondaryTextDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(icon: ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = tint) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.padding(8.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun openUrl(ctx: Context, url: String) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (_: Exception) {}
}
