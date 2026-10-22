// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.simswap

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import com.onlytrusted.app.ui.theme.TertiaryTextDark

/**
 * Full-screen non-dismissable alarm shown when the SIM identity hash drifts from the
 * stored baseline. Rendered as a `Dialog` so it covers the entire app window with
 * `usePlatformDefaultWidth = false` and `dismissOnBackPress = false`.
 *
 * CTAs:
 *   1. "Lock app now" — invokes `onLock`, which calls `AppLockManager.resetUnlock()`.
 *   2. "Call my carrier" — opens `tel:` for the stored carrier number (default 611).
 *   3. "I did this swap on purpose" — overwrites the baseline and dismisses.
 */
@Composable
fun SimSwapAlarmDialog(
    alarm: SimSwapState.Alarm,
    onLock: () -> Unit,
    onAcknowledge: () -> Unit
) {
    val context = LocalContext.current
    val carrierNumber = SimSwapState.carrierNumber(context)

    Dialog(
        onDismissRequest = { /* non-dismissable */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightBackground),
            color = MidnightBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                Spacer(Modifier.height(8.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .background(DangerDark.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.SimCard,
                        contentDescription = null,
                        tint = DangerDark,
                        modifier = Modifier.size(60.dp)
                    )
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = DangerDark,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.BottomEnd)
                    )
                }

                Text(
                    "SIM change detected",
                    color = PrimaryTextDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "If this wasn't you, lock the app, call your carrier, and check your " +
                        "bank + email accounts for intrusion. SIM-swap attacks happen within " +
                        "minutes.",
                    color = SecondaryTextDark,
                    fontSize = 14.sp
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidnightElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Previous: ${alarm.oldHashShort}…",
                            color = PrimaryTextDark,
                            fontSize = 12.sp
                        )
                        Text(
                            "Current: ${alarm.newHashShort}… · ${alarm.currentDisplay}",
                            color = DangerDark,
                            fontSize = 12.sp
                        )
                        Text(
                            "Fingerprints are SHA-256 truncated. Full SIM identity is kept only " +
                                "in EncryptedSharedPreferences.",
                            color = TertiaryTextDark,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onLock,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.size(10.dp))
                    Text("Lock app now", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { dialCarrier(context, carrierNumber) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.size(10.dp))
                    Text("Call my carrier ($carrierNumber)", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onAcknowledge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I did this swap on purpose", color = SecondaryTextDark)
                }
            }
        }
    }
}

private fun dialCarrier(context: Context, number: String) {
    val cleaned = number.filter { it == '+' || it.isDigit() }
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
