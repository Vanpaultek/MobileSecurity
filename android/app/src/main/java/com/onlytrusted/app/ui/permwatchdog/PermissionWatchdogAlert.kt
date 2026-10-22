// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.permwatchdog

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.onlytrusted.app.ui.subscription.SubscriptionState
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark

/**
 * Composable gate shown at launch (after the lock gate clears) that, if the user is on Pro
 * and daily audit is enabled, re-evaluates the watchdog and shows an alert if ≥1 app has
 * gained sensitive permissions since the stored baseline.
 *
 * The alert is NOT auto-dismissable — the user must choose one of:
 *   - "Review now"       → opens the Watchdog screen.
 *   - "Later"            → closes the dialog; it will re-arm on next launch.
 *   - "Mark as reviewed" → resets the baseline so the diffs no longer apply.
 */
@Composable
fun PermissionWatchdogLaunchAlert(onOpenWatchdog: () -> Unit) {
    val context = LocalContext.current

    // Gating. Only Pro users with the toggle enabled get audited at launch.
    if (!SubscriptionState.hasHighPremium(context) ||
        !PermissionWatchdogStore.isAuditEnabled(context)
    ) return

    var evaluated by remember { mutableStateOf(false) }
    var diffs by remember { mutableStateOf<List<PermissionWatchdogStore.Diff>>(emptyList()) }
    var showAlert by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = PermissionWatchdogStore.evaluate(context)
        diffs = r.diffs
        showAlert = diffs.isNotEmpty()
        evaluated = true
    }

    if (!evaluated || !showAlert) return

    AlertDialog(
        onDismissRequest = { /* non-dismissable; force a choice */ },
        title = {
            Text(
                "Permission changes detected",
                fontWeight = FontWeight.Bold,
                color = PrimaryTextDark
            )
        },
        text = {
            Text(
                "${diffs.size} app${if (diffs.size == 1) "" else "s"} gained sensitive " +
                    "permissions since your last check. Review what changed?",
                color = SecondaryTextDark
            )
        },
        confirmButton = {
            TextButton(onClick = {
                showAlert = false
                onOpenWatchdog()
            }) {
                Text("Review now", color = BrandAccentDark, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = {
                    PermissionWatchdogStore.resetBaseline(context)
                    showAlert = false
                }) {
                    Text("Mark as reviewed", color = subGold)
                }
                TextButton(onClick = {
                    PermissionWatchdogStore.markDismissedNow(context)
                    showAlert = false
                }) {
                    Text("Later", color = SecondaryTextDark)
                }
            }
        },
        containerColor = MidnightCard
    )
}

/** Non-Compose entry point used for background-scheduled scans (future WorkManager job). */
fun evaluateWatchdogSync(ctx: Context): PermissionWatchdogStore.EvaluationResult =
    PermissionWatchdogStore.evaluate(ctx)
