// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * bar are all baked into the SplashHero PNG. No native shape overlays or animated bar.
 * We just paint the image at Crop content-scale for the fixed splash duration and then
 * hand off via [onDone].
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
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
