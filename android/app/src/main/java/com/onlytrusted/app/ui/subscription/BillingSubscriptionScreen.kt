// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.subscription

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.SuccessDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

// Blue chip matches Settings' icon-row style for parity with iOS Color.settingsBlue.
private val settingsBlue = Color(0xFF2266F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingSubscriptionScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    // Re-read on every recomposition so that returning from Upgrade (which flips the
    // SharedPreferences-backed state) picks up the new values.
    val planName = SubscriptionState.planName(context)
    val pricing = SubscriptionState.pricingSummary(context)
    val isActive = SubscriptionState.isActive(context)
    val lastFour = SubscriptionState.lastFour(context)
    val nextDate = SubscriptionState.nextBillingDate(context)

    var manageSheet by remember { mutableStateOf(false) }
    var paymentSheet by remember { mutableStateOf(false) }
    var historySheet by remember { mutableStateOf(false) }
    var restoreNotice by remember { mutableStateOf<String?>(null) }

    val manageState = rememberModalBottomSheetState()
    val paymentState = rememberModalBottomSheetState()
    val historyState = rememberModalBottomSheetState()

    Scaffold(containerColor = MidnightBackground) { padding ->
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
                        Brush.verticalGradient(
                            listOf(
                                MidnightBackground,
                                MidnightBackground.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Header(onBack = onBack)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                CurrentPlanCard(
                planName = planName,
                pricing = pricing,
                isActive = isActive,
                nextBilling = SubscriptionState.formatDate(nextDate)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SubscriptionNavRow(
                    icon = Icons.Filled.Receipt,
                    title = "Manage Subscription",
                    subtitle = "Update plan or cancel",
                    onClick = { manageSheet = true }
                )
                SubscriptionNavRow(
                    icon = Icons.Filled.CreditCard,
                    title = "Payment Method",
                    subtitle = "Visa •••• $lastFour",
                    onClick = { paymentSheet = true }
                )
                SubscriptionNavRow(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = "Billing History",
                    subtitle = "View past invoices",
                    onClick = { historySheet = true }
                )
                SubscriptionNavRow(
                    icon = Icons.Filled.ShoppingBag,
                    title = "Restore Purchase",
                    subtitle = "Restore your premium access",
                    onClick = {
                        restoreNotice = if (SubscriptionState.isActive(context)) {
                            "Your Premium access was already active on this device."
                        } else {
                            "No previous purchases were found on this Google account."
                        }
                    }
                )
            }
            }
        }

        if (manageSheet) {
            ModalBottomSheet(
                onDismissRequest = { manageSheet = false },
                sheetState = manageState,
                containerColor = MidnightElevated
            ) {
                ManageSheetContent(onDismiss = { manageSheet = false })
            }
        }
        if (paymentSheet) {
            ModalBottomSheet(
                onDismissRequest = { paymentSheet = false },
                sheetState = paymentState,
                containerColor = MidnightElevated
            ) {
                PaymentSheetContent(lastFour = lastFour)
            }
        }
        if (historySheet) {
            ModalBottomSheet(
                onDismissRequest = { historySheet = false },
                sheetState = historyState,
                containerColor = MidnightElevated
            ) {
                HistorySheetContent()
            }
        }
        if (restoreNotice != null) {
            AlertDialog(
                onDismissRequest = { restoreNotice = null },
                title = { Text("Restore Purchase", color = PrimaryTextDark) },
                text = { Text(restoreNotice ?: "", color = SecondaryTextDark) },
                confirmButton = {
                    TextButton(onClick = { restoreNotice = null }) {
                        Text("OK", color = BrandCyanDark)
                    }
                },
                containerColor = MidnightElevated
            )
        }
    }
}

// =============================================================
//  Header
// =============================================================

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(onClick = onBack, color = Color.Transparent) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "Back",
                tint = BrandCyanDark,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            "Billing & Subscription",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default,
            color = PrimaryTextDark
        )
    }
}

// =============================================================
//  Current plan card
// =============================================================

@Composable
private fun CurrentPlanCard(
    planName: String,
    pricing: String,
    isActive: Boolean,
    nextBilling: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MidnightElevated,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BrandCyanDark.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Glowing gold crown badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(
                            elevation = 14.dp,
                            shape = CircleShape,
                            ambientColor = subGold,
                            spotColor = subGold
                        )
                        .border(3.dp, subGold, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = subGold,
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                ambientColor = subGold,
                                spotColor = subGold
                            )
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Current Plan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = SecondaryTextDark
                    )
                    Text(
                        planName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyanDark
                    )
                    Text(
                        pricing,
                        fontSize = 15.sp,
                        color = PrimaryTextDark
                    )
                }

                // Active capsule
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(
                            color = if (isActive) SuccessDark else Color(0xFFEF4444),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (isActive) "Active" else "Inactive",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Divider(color = BrandCyanDark.copy(alpha = 0.15f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Next billing date",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SecondaryTextDark
                )
                Text(
                    nextBilling,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextDark
                )
            }
        }
    }
}

// =============================================================
//  Nav row
// =============================================================

@Composable
private fun SubscriptionNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = settingsBlue.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        settingsBlue.copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = settingsBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextDark
                )
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = SecondaryTextDark
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = BrandCyanDark,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// =============================================================
//  Sheets
// =============================================================

@Composable
private fun ManageSheetContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Manage Subscription",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTextDark
        )
        Text(
            "Subscriptions are managed in Google Play. We'll open your subscriptions page in Play Store.",
            fontSize = 14.sp,
            color = SecondaryTextDark
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/account/subscriptions")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                onDismiss()
            },
            shape = RoundedCornerShape(999.dp),
            color = BrandCyanDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    "Open Google Play Subscriptions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MidnightBackground
                )
            }
        }
    }
}

@Composable
private fun PaymentSheetContent(lastFour: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Payment Method",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTextDark
        )
        Text(
            "Visa •••• $lastFour",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryTextDark
        )
        Text(
            "Your payment method is managed by your Google account. Update it from the Play Store.",
            fontSize = 14.sp,
            color = SecondaryTextDark
        )
    }
}

@Composable
private fun HistorySheetContent() {
    val entries = remember { SubscriptionState.invoiceHistory() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Billing History",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTextDark
        )
        entries.forEach { entry ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MidnightBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            entry.dateString,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTextDark
                        )
                        Text(
                            entry.description,
                            fontSize = 12.sp,
                            color = SecondaryTextDark
                        )
                    }
                    Text(
                        entry.amountString,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandCyanDark
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            Text(
                "No past invoices yet.",
                fontSize = 14.sp,
                color = TertiaryTextDark
            )
        }
    }
}
