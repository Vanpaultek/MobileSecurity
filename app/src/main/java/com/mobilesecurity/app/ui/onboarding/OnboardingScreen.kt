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
package com.mobilesecurity.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobilesecurity.app.privacy.OnboardingState

@Composable
fun OnboardingScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    var isAdult by remember { mutableStateOf(false) }
    var ownsDevice by remember { mutableStateOf(false) }
    var understandsLimits by remember { mutableStateOf(false) }
    val canContinue = isAdult && ownsDevice && understandsLimits

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MobileSecurity", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This app is a serious privacy tool for the phone owner and their immediate family. " +
                "It is not an entertainment or novelty app. Before you use it, please read and " +
                "confirm the following."
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What this app does", style = MaterialTheme.typography.titleLarge)
                Text("• Keeps your microphone and camera for you — alerts the moment another app uses them.")
                Text("• Protects your family's phone numbers and addresses with layered encryption.")
                Text("• Blocks or silences everyone who is not on your trusted list, when you turn Trusted-only on.")
                Text("• Keeps every piece of data on this device only. No cloud. No ads. No analytics.")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Honest limits", style = MaterialTheme.typography.titleLarge)
                Text("• An app on normal Android cannot hardware-mute the mic/camera. The system " +
                    "mic & camera toggles in Quick Settings do that; this app surfaces and complements them.")
                Text("• You cannot stop the other side of a video call from recording their own device.")
                Text("• Full screenshot blocking across the whole phone requires a hardened OS or root.")
                Text("• Carrier/network tracking is outside any app's control.")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isAdult, onCheckedChange = { isAdult = it })
            Text(
                "I am at least 18 years old (or the legal age of digital consent in my country).",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ownsDevice, onCheckedChange = { ownsDevice = it })
            Text(
                "I own or administer this phone and will only block numbers and intercept calls " +
                    "on devices I am authorised to manage.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = understandsLimits, onCheckedChange = { understandsLimits = it })
            Text(
                "I have read the honest limits above and understand this is a privacy aid, not " +
                    "a guarantee against determined attackers.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = {
                OnboardingState.accept(context, isAdult, ownsDevice, understandsLimits)
                onAccepted()
            },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth()
        ) { Text("I confirm and continue") }

        Text(
            "By continuing you also accept the Privacy Policy and Terms of Use shipped inside the app. " +
                "They can be reviewed any time from Settings → Privacy, terms & honest claims.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
