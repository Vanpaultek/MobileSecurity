// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.R
import com.onlytrusted.app.privacy.OnboardingState
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.WarningDark

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(136.dp)
                    .background(BrandAccentDark.copy(alpha = 0.12f), RoundedCornerShape(50))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.brand_logo),
                    contentDescription = "OnlyTrusted",
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Text(
                "OnlyTrusted",
                color = PrimaryTextDark,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "A safer, quieter phone starts here.",
                color = SecondaryTextDark,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    BenefitRow(Icons.Filled.Groups, "Allow only trusted contacts")
                    HorizontalDivider(Modifier.padding(start = 68.dp, end = 20.dp))
                    BenefitRow(Icons.Filled.NotificationsOff, "Silence or block everyone else")
                    HorizontalDivider(Modifier.padding(start = 68.dp, end = 20.dp))
                    BenefitRow(Icons.Filled.FactCheck, "Keep a local audit log")
                    HorizontalDivider(Modifier.padding(start = 68.dp, end = 20.dp))
                    BenefitRow(Icons.Filled.VerifiedUser, "Your data never leaves this device")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = WarningDark,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Honest limits Android imposes",
                            color = PrimaryTextDark,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HonestLimitBullet("A normal Android app cannot hardware-mute the mic or camera; the system toggles in Quick Settings do that.")
                    HonestLimitBullet("You cannot stop the other side of a call from recording their own device.")
                    HonestLimitBullet("Full screenshot blocking across the whole phone needs a hardened OS or root.")
                    HonestLimitBullet("Carrier and network-level tracking is outside any app's control.")
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MidnightCard)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = {
                    OnboardingState.markOnboarded(context)
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get Started")
            }
        }
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(BrandAccentDark.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
        ) {
            Icon(icon, contentDescription = null, tint = BrandAccentDark)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = PrimaryTextDark,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HonestLimitBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp, end = 10.dp)
                .size(5.dp)
                .background(BrandAccentDark, RoundedCornerShape(50))
        )
        Text(
            text,
            color = PrimaryTextDark,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
