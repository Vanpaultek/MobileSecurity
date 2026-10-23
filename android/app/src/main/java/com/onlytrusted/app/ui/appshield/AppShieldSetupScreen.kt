// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.appshield

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.SuccessDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

/**
 * Elite-tier setup wizard for Auto Privacy Shield — Phase 2 (Android edition).
 *
 * Caller gates on `SubscriptionState.hasElite`; this composable assumes the user is Elite.
 *
 * The screen is intentionally honest: it does NOT claim OnlyTrusted blocks other apps.
 * Instead it walks the user through Google's Digital Wellbeing / Focus Mode (the consumer-safe
 * surface for app-limit controls) and lets the user keep an informational reminder list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShieldSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var watched by remember { mutableStateOf(AppShieldState.watchedPackages(context)) }
    var walkthroughDone by remember {
        mutableStateOf(AppShieldState.wellbeingWalkthroughDone(context))
    }
    var newPackage by remember { mutableStateOf("") }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "App Shield",
                        color = PrimaryTextDark,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = BrandCyanDark)
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
            // 1. Honest explainer
            Card {
                CardHeader(Icons.Filled.Warning, "What this actually does", tint = WarningDark)
                Text(
                    "True app blocking on Android requires Device Owner provisioning (ADB + factory reset). " +
                        "An ordinary Play Store app can't do that. The strongest consumer path is Google's own " +
                        "Digital Wellbeing / Focus Mode, which uses the same system APIs behind a privileged UI.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "OnlyTrusted keeps an informational reminder list below. You add the apps to Focus Mode " +
                        "yourself; Wellbeing enforces the restriction.",
                    color = TertiaryTextDark,
                    fontSize = 12.sp
                )
            }

            // 2. Digital Wellbeing hand-off
            Card {
                CardHeader(Icons.Filled.Shield, "Open Digital Wellbeing")
                Text(
                    "Google's Digital Wellbeing app is the user-facing wrapper around Android's app-limit " +
                        "APIs. Launch it, then set per-app daily timers or add apps to Focus Mode.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (openDigitalWellbeing(context)) {
                            walkthroughDone = true
                            AppShieldState.setWellbeingWalkthroughDone(context, true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandAccentDark,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Open Digital Wellbeing", fontWeight = FontWeight.SemiBold)
                }
                if (walkthroughDone) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Walkthrough opened. Add your apps to Focus Mode inside Wellbeing.",
                            color = SuccessDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 3. Guided checklist
            Card {
                CardHeader(Icons.Filled.Info, "Inside Digital Wellbeing")
                ChecklistItem("1.", "Tap Focus mode (or Dashboard → app timer).")
                ChecklistItem("2.", "Tap Add apps and pick the apps you want blocked.")
                ChecklistItem("3.", "Set a schedule or turn Focus mode on now.")
                ChecklistItem("4.", "Come back here if you want to adjust the reminder list.")
            }

            // 4. Informational reminder list
            Card {
                CardHeader(Icons.Filled.Add, "Reminder list (informational)")
                Text(
                    "Add the app package names you want restricted so OnlyTrusted can remind you to " +
                        "configure them in Wellbeing. This list is a note to yourself — OnlyTrusted does NOT " +
                        "enforce it.",
                    color = SecondaryTextDark,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPackage,
                        onValueChange = { newPackage = it },
                        singleLine = true,
                        label = { Text("com.example.app") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (newPackage.isNotBlank()) {
                                AppShieldState.addWatchedPackage(context, newPackage)
                                watched = AppShieldState.watchedPackages(context)
                                newPackage = ""
                            }
                        }
                    ) {
                        Text("Add", color = BrandAccentDark)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (watched.isEmpty()) {
                    Text(
                        "No apps on your reminder list yet.",
                        color = TertiaryTextDark,
                        fontSize = 12.sp
                    )
                } else {
                    watched.forEach { pkg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = BrandCyanDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                pkg,
                                color = PrimaryTextDark,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                AppShieldState.removeWatchedPackage(context, pkg)
                                watched = AppShieldState.watchedPackages(context)
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove $pkg",
                                    tint = SecondaryTextDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Current status footer
            Card {
                CardHeader(Icons.Filled.Info, "Current status")
                if (walkthroughDone && watched.isNotEmpty()) {
                    Text(
                        "Guided setup opened. ${watched.size} app${if (watched.size == 1) "" else "s"} on your reminder list — enforced by Digital Wellbeing, not by OnlyTrusted.",
                        color = SuccessDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (watched.isNotEmpty()) {
                    Text(
                        "${watched.size} app${if (watched.size == 1) "" else "s"} on your reminder list. Tap Open Digital Wellbeing above to configure the actual restriction.",
                        color = WarningDark,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        "Not configured. Add apps to your reminder list and open Digital Wellbeing.",
                        color = SecondaryTextDark,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Attempt to launch Digital Wellbeing. We try the private settings intent first (works on most
 * Pixel + stock OEM builds), then a generic Settings fallback so the button never crashes.
 */
private fun openDigitalWellbeing(context: android.content.Context): Boolean {
    // Primary: Google's Wellbeing settings activity. The action name has shipped since 2019.
    val wellbeing = Intent("com.google.android.apps.wellbeing.WELLBEING_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(wellbeing)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    } catch (_: SecurityException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}

// ---- UI helpers --------------------------------------------------------------

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MidnightCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, tint: Color = BrandAccentDark) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = PrimaryTextDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChecklistItem(num: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(BrandCyanDark.copy(alpha = 0.18f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = BrandCyanDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.size(10.dp))
        Text(text, color = SecondaryTextDark, fontSize = 13.sp)
    }
}
