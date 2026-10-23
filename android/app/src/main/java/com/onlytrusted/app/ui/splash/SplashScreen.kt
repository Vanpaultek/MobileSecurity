// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.onlytrusted.app.R
import com.onlytrusted.app.ui.theme.MidnightBackground
import kotlinx.coroutines.delay

/**
 * Full-screen cinematic splash — the shield, lakescape, wordmark, tagline, and loading
 * bar are all baked into the SplashHero PNG (941×1672).
 *
 * On tall-aspect Android screens (Pixel 7 is 1080×2400 ≈ 0.45 aspect ratio) the image
 * ratio (0.563) is WIDER than the screen, so `ContentScale.Crop` with `fillMaxSize`
 * crops the horizontal edges and shows only the shield in a tight centre column — the
 * exact "zoomed-in" look reported from device. `ContentScale.Fit` preserves the whole
 * image; any top/bottom letterbox falls on the midnight-background box behind, which
 * matches the image's own black edges so it still reads as a single scene.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val durationMs = 1400L

    LaunchedEffect(Unit) {
        delay(durationMs)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .semantics {
                contentDescription =
                    "OnlyTrusted. A quieter, safer phone for the people who matter."
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_hero),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
