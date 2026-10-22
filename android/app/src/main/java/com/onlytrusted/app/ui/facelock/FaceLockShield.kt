// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.facelock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onlytrusted.app.ui.lock.BiometricPrompter
import com.onlytrusted.app.ui.subscription.SubscriptionState
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark

/**
 * Composable shield that attaches the Face-Change Shoulder-Surfing Lock monitor to any
 * sensitive content composable. No-op unless the user is on Elite AND has an enrolled face
 * AND at least one of the two lock toggles is on.
 *
 * ```
 * FaceLockShield {
 *     SensitiveContent()
 * }
 * ```
 *
 * On `WalkedAway`, we overlay a soft blur card so the layout stays visible but the data
 * doesn't. On `LockStranger` / `LockMultiple`, we show a full-screen lock card that
 * requires a biometric / device-credential tap via [BiometricPrompter] before the content
 * is re-revealed.
 */
@Composable
fun FaceLockShield(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    val elite = remember { SubscriptionState.hasElite(context) }
    val enrolled = remember { FaceLockState.isEnrolled(context) }
    val anyToggle = remember {
        FaceLockState.lockWhenOwnerLeaves(context) ||
            FaceLockState.lockWhenStrangerJoins(context)
    }
    val eligible = elite && enrolled && anyToggle

    if (!eligible) {
        content()
        return
    }

    val monitor = remember { FaceLockMonitor(lifecycleOwner) }
    val decision by monitor.decision

    DisposableEffect(monitor) {
        onDispose { monitor.shutdown() }
    }
    LaunchedEffect(Unit) {
        monitor.start(context)
    }

    Box(Modifier.fillMaxSize()) {
        content()
        when (decision) {
            FaceLockMonitor.Decision.WalkedAway ->
                if (FaceLockState.blurOnExit(context)) {
                    SoftBlurOverlay()
                }
            FaceLockMonitor.Decision.LockStranger,
            FaceLockMonitor.Decision.LockMultiple ->
                HardLockOverlay(
                    headline = if (decision == FaceLockMonitor.Decision.LockMultiple)
                        "Someone else is looking"
                    else "Unknown face detected",
                    detail = if (decision == FaceLockMonitor.Decision.LockMultiple)
                        "Two or more faces were visible to the front camera. Content is hidden until you confirm it's safe."
                    else "The face in front of the camera doesn't match the enrolled owner.",
                    onUnlock = {
                        if (activity != null) {
                            BiometricPrompter.prompt(
                                activity = activity,
                                title = "Confirm it's you",
                                subtitle = "Re-reveal sensitive content",
                                onSuccess = { monitor.acknowledgeUnlock() },
                                onFailure = { /* keep locked */ }
                            )
                        } else {
                            monitor.acknowledgeUnlock()
                        }
                    }
                )
            else -> Unit
        }
    }
}

@Composable
private fun SoftBlurOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = BrandAccentDark,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "Hidden while you're away",
                color = PrimaryTextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Look back at the camera to unblur.",
                color = SecondaryTextDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun HardLockOverlay(
    headline: String,
    detail: String,
    onUnlock: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = DangerDark,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                headline,
                color = PrimaryTextDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                detail,
                color = SecondaryTextDark,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
            )
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Unlock with biometric", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
