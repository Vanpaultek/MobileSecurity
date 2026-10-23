// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.appshield

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark

/**
 * Elite upsell dialog shown when a non-Elite user taps the "App Shield (Elite)" row or
 * the Phase 1 Privacy Shield dashboard's "Set up" CTA on the Selected Apps card.
 *
 * Dialog shape matches the other Elite upsells in `SettingsScreen.kt`.
 */
@Composable
fun AppShieldUpsellDialog(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "App Shield is an Elite feature",
                color = PrimaryTextDark,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Pick the apps you want restricted and OnlyTrusted will walk you through " +
                    "Google's Digital Wellbeing / Focus Mode so the restriction is enforced " +
                    "at the OS level. True app-blocking on Android requires Device Owner " +
                    "provisioning (ADB + factory reset), so Wellbeing is the only consumer " +
                    "path. Elite tier.",
                color = SecondaryTextDark
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text("Upgrade to Elite", color = subGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = SecondaryTextDark)
            }
        },
        containerColor = MidnightCard
    )
}
