// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.onlytrusted.app.R

/**
 * Full-screen cinematic backdrop (neon shield over alpine lake) with a dark scrim on
 * top so page content stays readable. Non-interactive. Mirrors the iOS `AppBackdrop`
 * SwiftUI view so inside screens render the same cinematic feel on both platforms.
 *
 * Used as a sibling in [AppBackdropScaffold]. Pages that compose their own root Column
 * (no Scaffold) can call [appBackdropBackground] on that Column's modifier instead.
 */
@Composable
fun AppBackdrop() {
    Box(modifier = Modifier.fillMaxSize().background(MidnightBackground)) {
        Image(
            painter = painterResource(id = R.drawable.app_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to MidnightBackground.copy(alpha = 0.35f),
                            0.4f to MidnightBackground.copy(alpha = 0.55f),
                            0.75f to MidnightBackground.copy(alpha = 0.75f),
                            1.0f to MidnightBackground.copy(alpha = 0.92f)
                        )
                    )
                )
        )
    }
}

/**
 * Modifier form of [AppBackdrop] for pages whose root composable is a Column/Box with
 * `.background(MidnightBackground)` and no Scaffold. Chain order: midnight base →
 * backdrop image (alpha 0.55) → scrim gradient → content.
 */
@Composable
fun Modifier.appBackdropBackground(): Modifier {
    val painter = painterResource(id = R.drawable.app_backdrop)
    return this
        .background(MidnightBackground)
        .paint(painter, contentScale = ContentScale.Crop, alpha = 0.55f)
        .drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to MidnightBackground.copy(alpha = 0.35f),
                        0.4f to MidnightBackground.copy(alpha = 0.55f),
                        0.75f to MidnightBackground.copy(alpha = 0.75f),
                        1.0f to MidnightBackground.copy(alpha = 0.92f)
                    )
                )
            )
        }
}
