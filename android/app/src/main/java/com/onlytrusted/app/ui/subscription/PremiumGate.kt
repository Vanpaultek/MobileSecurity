// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.subscription

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.AppBackdropScaffold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark

/**
 * Wrap any Premium-gated screen's content in this composable. If the user is on Free,
 * renders a single upsell card instead of the real content (the owning screen's own
 * Scaffold / top-bar / navigation stays intact around it).
 *
 * Deep-link safety: Settings routes still allow Free users to open the destination so
 * they can see what they'd unlock. The gate shows the upsell card the moment the
 * screen's body composes.
 */
@Composable
fun PremiumGate(
    featureName: String,
    description: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    if (SubscriptionState.hasPremium(context)) {
        content()
    } else {
        PremiumUpsellCard(featureName = featureName, description = description)
    }
}

/**
 * Full-screen replacement scaffold that gated feature screens can render for Free users.
 * Includes the standard TopAppBar + Back button + AppBackdrop so the top-level screens
 * (BreachWatchScreen, AliasesScreen, IdentityProtectionScreen, PrivacyShieldDashboard)
 * can just early-return this and avoid building their real state at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumGatedScaffold(
    title: String,
    featureName: String,
    description: String,
    onBack: () -> Unit
) {
    AppBackdropScaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PremiumUpsellCard(featureName = featureName, description = description)
        }
    }
}

/** Full-screen upsell shown instead of the gated feature when the user is on Free. */
@Composable
private fun PremiumUpsellCard(featureName: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MidnightElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        null,
                        tint = subGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "$featureName — Premium",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryTextDark
                    )
                }
                Text(
                    description,
                    fontSize = 13.sp,
                    color = SecondaryTextDark
                )
                Text(
                    "This feature is included with the Premium plan (\$4.99/yr). " +
                        "Open Settings → Upgrade to Premium to unlock it.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
                Text(
                    "Your Free plan still includes trusted contacts, call & SMS " +
                        "handling, Blocking & Unknown, Data Removal Scan, and all " +
                        "device-level settings.",
                    fontSize = 11.sp,
                    color = SecondaryTextDark
                )
            }
        }
    }
}
