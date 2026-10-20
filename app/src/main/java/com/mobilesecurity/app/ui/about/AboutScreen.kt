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
package com.mobilesecurity.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobilesecurity.app.BuildConfig

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("About", style = MaterialTheme.typography.headlineMedium)
        Text(
            "MobileSecurity ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "A local-only privacy and anti-nuisance app for Android. " +
                "No cloud. No analytics. No ads. No data collection.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Support", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Email: reddy@vanpaultek.com",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:reddy@vanpaultek.com")
                            putExtra(Intent.EXTRA_SUBJECT,
                                "MobileSecurity ${BuildConfig.VERSION_NAME} support")
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open email client") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Changelog", style = MaterialTheme.typography.titleLarge)
                Text("0.1.0 — Initial release.")
                Text("- Mic + camera usage alerts (notification + vibration).")
                Text("- Call screening with user block list.")
                Text("- SMS filtering with user block list.")
                Text("- Pocket-dial detection and confirmation.")
                Text("- Screenshot protection on this app's screens.")
                Text("- Permission audit of installed apps.")
                Text("- App lock with biometric or device credential.")
                Text("- Encrypted local storage, no network use.")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Open-source attributions", style = MaterialTheme.typography.titleLarge)
                Attribution("AndroidX libraries", "Apache License 2.0", "https://developer.android.com/jetpack/androidx")
                Attribution("Jetpack Compose", "Apache License 2.0", "https://developer.android.com/jetpack/compose")
                Attribution("Material 3 for Compose", "Apache License 2.0", "https://m3.material.io/")
                Attribution("Room", "Apache License 2.0", "https://developer.android.com/training/data-storage/room")
                Attribution("SQLCipher for Android", "BSD-style", "https://github.com/sqlcipher/android-database-sqlcipher")
                Attribution("Kotlin + Coroutines", "Apache License 2.0", "https://kotlinlang.org/")
                Attribution("Accompanist Permissions", "Apache License 2.0", "https://google.github.io/accompanist/")
            }
        }

        Text(
            "No part of this app contacts a network. Attribution links are provided for your reference only " +
                "and must be opened in your own browser if you choose.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun Attribution(name: String, license: String, url: String) {
    Column {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text("License: $license  ·  $url", style = MaterialTheme.typography.bodyMedium)
    }
}
