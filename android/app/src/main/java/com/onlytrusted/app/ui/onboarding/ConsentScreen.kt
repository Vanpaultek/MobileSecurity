// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.onboarding

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
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.privacy.OnboardingState
import com.onlytrusted.app.ui.legal.LegalScreen
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(onConfirmed: () -> Unit) {
    val context = LocalContext.current
    var isAdult by remember { mutableStateOf(false) }
    var ownsDevice by remember { mutableStateOf(false) }
    var understandsLimits by remember { mutableStateOf(false) }
    var legalSheetSection by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canContinue = isAdult && ownsDevice && understandsLimits

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
                    .size(80.dp)
                    .background(BrandAccentDark.copy(alpha = 0.15f), RoundedCornerShape(50))
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = BrandAccentDark,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                "Please confirm",
                color = PrimaryTextDark,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "This app is for people who need strong privacy and safety.",
                color = SecondaryTextDark,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ConsentRow(
                        title = "I am an adult",
                        detail = "I am 18 years or older.",
                        checked = isAdult,
                        onChange = { isAdult = it }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    ConsentRow(
                        title = "I am the device owner",
                        detail = "This is my personal device.",
                        checked = ownsDevice,
                        onChange = { ownsDevice = it }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    ConsentRow(
                        title = "I have read and understood the honest limits",
                        detail = "I understand what this app can and cannot do.",
                        checked = understandsLimits,
                        onChange = { understandsLimits = it }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { legalSheetSection = "Privacy policy" }) { Text("Privacy Policy") }
                Text("|", color = TertiaryTextDark)
                TextButton(onClick = { legalSheetSection = "Terms of use" }) { Text("Terms of Use") }
                Text("|", color = TertiaryTextDark)
                TextButton(onClick = { legalSheetSection = "Honest claims" }) { Text("Honest Claims") }
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
                    OnboardingState.accept(context, isAdult, ownsDevice, understandsLimits)
                    onConfirmed()
                },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I confirm and continue")
            }
        }

        val currentSheetSection = legalSheetSection
        if (currentSheetSection != null) {
            ModalBottomSheet(
                onDismissRequest = { legalSheetSection = null },
                sheetState = sheetState
            ) {
                LegalScreen(initialSectionTitle = currentSheetSection)
            }
        }
    }
}

@Composable
private fun ConsentRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = PrimaryTextDark,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(detail, color = SecondaryTextDark, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
