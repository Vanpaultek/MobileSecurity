// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.breachwatch

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.onlytrusted.app.util.NotificationHelper
import java.text.DateFormat
import java.util.Date

/** Breach Watch (FREE tier). Mirrors iOS `BreachWatchScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreachWatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    if (!com.onlytrusted.app.ui.subscription.SubscriptionState.hasPremium(context)) {
        com.onlytrusted.app.ui.subscription.PremiumGatedScaffold(
            title = "Breach Watch",
            featureName = "Breach Watch",
            description = "Monitor up to 5 of your email addresses against a bundled breach-hash list. Lookups run entirely on-device.",
            onBack = onBack
        )
        return
    }

    var email by remember { mutableStateOf(BreachWatchState.email(context)) }
    var draftEmail by remember { mutableStateOf(email) }
    var userName by remember { mutableStateOf(BreachWatchState.userName(context)) }
    var editing by remember { mutableStateOf(email.isEmpty()) }
    var lastCheck by remember { mutableStateOf(BreachWatchState.lastCheckMillis(context)) }
    var hits by remember { mutableStateOf(BreachWatchState.hits(context)) }
    var additionalEmails by remember { mutableStateOf(BreachWatchState.additionalEmails(context)) }
    var additionalDraft by remember { mutableStateOf("") }
    val isPremium = com.onlytrusted.app.ui.subscription.SubscriptionState.hasPremium(context)

    LaunchedEffect(Unit) {
        if (email.isNotEmpty()) {
            BreachWatchScheduler.scheduleMonthly(context)
        }
    }

    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breach Watch", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
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
                    HeaderRow(
                        icon = Icons.Filled.MarkEmailUnread,
                        title = "Breach Watch",
                        tint = BrandAccentDark
                    )
                    Text(
                        "Monitor one email address against our bundled breach-hash list. All lookups happen on your device — nothing leaves this app.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                    Text(
                        "We rehash and check once a month, and you can tap \"Check now\" any time.",
                        fontSize = 12.sp, color = SecondaryTextDark
                    )
                }
            }

            // Monitored email
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Monitored email", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = PrimaryTextDark
                    )
                    if (editing || email.isEmpty()) {
                        OutlinedTextField(
                            value = draftEmail,
                            onValueChange = { draftEmail = it },
                            singleLine = true,
                            placeholder = { Text("you@example.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    BreachWatchState.saveEmail(context, draftEmail)
                                    email = BreachWatchState.email(context)
                                    if (email.isNotEmpty()) {
                                        editing = false
                                        BreachWatchScheduler.scheduleMonthly(context)
                                        hits = BreachWatchState.runCheck(context)
                                        lastCheck = BreachWatchState.lastCheckMillis(context)
                                    } else {
                                        BreachWatchScheduler.cancel(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandAccentDark,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) { Text("Save") }
                            if (email.isNotEmpty()) {
                                OutlinedButton(onClick = {
                                    draftEmail = email
                                    editing = false
                                }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(email, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = PrimaryTextDark, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                draftEmail = email
                                editing = true
                            }) { Text("Change", color = BrandAccentDark) }
                        }
                    }
                }
            }

            // Additional emails (Premium) — or upsell card for Free
            if (isPremium) {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Additional emails (Premium)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTextDark
                        )
                        Text(
                            "Monitor up to ${BreachWatchState.MAX_EMAILS_PREMIUM} emails in " +
                                "total. Checks run against all of them at once.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                        additionalEmails.forEach { addr ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    addr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryTextDark,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    BreachWatchState.removeAdditionalEmail(context, addr)
                                    additionalEmails = BreachWatchState.additionalEmails(context)
                                }) { Text("Remove", color = DangerDark, fontSize = 12.sp) }
                            }
                        }
                        val canAdd = (1 + additionalEmails.size) < BreachWatchState.MAX_EMAILS_PREMIUM
                        if (canAdd) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = additionalDraft,
                                    onValueChange = { additionalDraft = it },
                                    singleLine = true,
                                    placeholder = { Text("another@example.com") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (BreachWatchState.addAdditionalEmail(context, additionalDraft)) {
                                            additionalDraft = ""
                                            additionalEmails = BreachWatchState.additionalEmails(context)
                                            hits = BreachWatchState.runCheck(context)
                                            lastCheck = BreachWatchState.lastCheckMillis(context)
                                        }
                                    },
                                    enabled = additionalDraft.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandAccentDark,
                                        contentColor = Color.White
                                    )
                                ) { Text("Add") }
                            }
                        } else {
                            Text(
                                "You've reached the ${BreachWatchState.MAX_EMAILS_PREMIUM}-email cap.",
                                fontSize = 11.sp, color = SecondaryTextDark
                            )
                        }
                    }
                }
            } else {
                // Free upsell card
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WorkspacePremium, null, tint = subGold)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Watch up to 5 emails (Premium)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryTextDark
                            )
                        }
                        Text(
                            "Premium (\$4.99/yr) lets you monitor your work, side, and family " +
                                "emails against the same bundled breach list — all on-device.",
                            fontSize = 11.sp, color = SecondaryTextDark
                        )
                    }
                }
            }

            // Optional name
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your name (optional)", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = PrimaryTextDark
                    )
                    Text(
                        "Used only to personalise the remediation steps. Never leaves this device.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            BreachWatchState.setUserName(context, it)
                        },
                        singleLine = true,
                        placeholder = { Text("First name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Last check + "Check now"
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Last check", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = PrimaryTextDark
                    )
                    val label = lastCheck?.let {
                        "Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}."
                    } ?: if (email.isEmpty()) "Save an email to begin monitoring." else "Not yet checked."
                    Text(label, fontSize = 12.sp, color = SecondaryTextDark)
                    Button(
                        onClick = {
                            val before = hits.size
                            val updated = BreachWatchState.runCheck(context)
                            hits = updated
                            lastCheck = BreachWatchState.lastCheckMillis(context)
                            if (updated.size > before) {
                                NotificationHelper.postAlert(
                                    context = context,
                                    title = "Breach Watch — new hit",
                                    body = "Your monitored email appeared in a breach list. " +
                                        "Open Settings → Breach Watch to review.",
                                    tag = "breach_watch_new_hit_manual"
                                )
                            }
                        },
                        enabled = email.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandAccentDark,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (email.isNotEmpty()) "Check now" else "Save an email to check")
                    }
                }
            }

            // Hits / empty state
            if (hits.isNotEmpty()) {
                CardBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = DangerDark)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Your email appears in known breaches",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DangerDark
                            )
                        }
                        hits.forEach { HitBlock(it, userName) }
                    }
                }
            } else if (email.isNotEmpty()) {
                CardBox {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = BrandAccentDark)
                        Column {
                            Text(
                                "No breaches found", fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = BrandAccentDark
                            )
                            Text(
                                "We'll check again at the start of next month.",
                                fontSize = 11.sp, color = SecondaryTextDark
                            )
                        }
                    }
                }
            }

            // Upsell
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = subGold)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Elite — enterprise breach feed",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTextDark
                        )
                    }
                    Text(
                        "Elite will let you monitor up to 10 emails + phone numbers with real-time alerts via the HIBP Enterprise feed. That requires a backend subscription, so it's not available in this build yet.",
                        fontSize = 11.sp, color = SecondaryTextDark
                    )
                }
            }

            // HIBP CC-BY-4.0 attribution — bundled breach_hashes.v1.txt is derived from
            // Troy Hunt's Have I Been Pwned public dataset. Visible attribution is a
            // license requirement for commercial use.
            CardBox {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Data attribution",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SecondaryTextDark
                    )
                    Text(
                        "Breach hashes bundled with this app are derived from the Have I Been " +
                            "Pwned public dataset (Troy Hunt, haveibeenpwned.com) under " +
                            "CC-BY-4.0. The lookup runs entirely on your device.",
                        fontSize = 10.sp, color = TertiaryTextDark
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HitBlock(hit: BreachWatchState.BreachHit, userName: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.03f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(hit.source, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
        Text(
            "First seen ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(hit.firstSeenMillis))}",
            fontSize = 10.sp, color = SecondaryTextDark
        )
        val greeting = if (userName.isNotEmpty()) "$userName, " else ""
        Text(
            "${greeting}Here's what we recommend — OnlyTrusted can't do these for you, so each step is a manual action on the real site.",
            fontSize = 11.sp, color = SecondaryTextDark
        )
        Text("What to do:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
        RemediationRow(Icons.Filled.VpnKey, "Change password on affected site")
        RemediationRow(Icons.Filled.Shield, "Enable 2FA / passkey")
        RemediationRow(Icons.Filled.MarkEmailUnread, "Review account activity")
    }
}

@Composable
private fun RemediationRow(icon: ImageVector, label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                color = BrandAccentDark.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = BrandAccentDark)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryTextDark)
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
