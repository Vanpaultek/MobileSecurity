// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.BrandPrimaryTeal
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen error state matching the 14-screen neon mockup:
 * big neon sad-cloud illustration with angry eyebrows, slit eyes, frown, and two
 * cyan stars; title + message; "Try Again" and "Go Back" pill buttons.
 */
@Composable
fun ErrorStateView(
    title: String = "Something went wrong",
    message: String = "We couldn't complete this action. Please try again.",
    onTryAgain: (() -> Unit)? = null,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D2433), MidnightBackground),
                    radius = 1200f
                )
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.weight(1f))

            SadCloudIllustration(modifier = Modifier.size(220.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = title,
                    color = PrimaryTextDark,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    color = SecondaryTextDark,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (onTryAgain != null) {
                    TryAgainPill(onClick = onTryAgain)
                }
                GoBackPill(onClick = onGoBack)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TryAgainPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // Heavy cyan outer glow: 48dp halo + tighter 24dp halo.
            .shadow(
                elevation = 48.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = BrandCyanDark.copy(alpha = 0.35f),
                spotColor = BrandCyanDark.copy(alpha = 0.35f)
            )
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = BrandCyanDark.copy(alpha = 0.65f),
                spotColor = BrandCyanDark.copy(alpha = 0.65f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(BrandCyanDark, BrandAccentDark)
                )
            )
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Try Again",
            color = MidnightBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun GoBackPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = BrandCyanDark.copy(alpha = 0.25f),
                spotColor = BrandCyanDark.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Transparent)
            .border(1.5.dp, BrandCyanDark.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Go Back",
            color = BrandCyanDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

// ---------- Sad cloud + stars illustration ----------

@Composable
private fun SadCloudIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { invisibleToUser() }) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cloudCy = h * 0.42f

        // Cloud silhouette: base capsule + three bumps.
        val cloudPath = Path().apply {
            // Base capsule (rounded rectangle)
            val baseW = w * 0.78f
            val baseH = h * 0.26f
            val baseLeft = cx - baseW / 2f
            val baseTop = (cloudCy + h * 0.06f) - baseH / 2f
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = baseLeft,
                    top = baseTop,
                    right = baseLeft + baseW,
                    bottom = baseTop + baseH,
                    cornerRadius = CornerRadius(baseH / 2f)
                )
            )
            // Left bump
            val lD = w * 0.36f
            addOval(
                Rect(
                    offset = Offset((cx - w * 0.22f) - lD / 2f, (cloudCy - h * 0.02f) - lD / 2f),
                    size = Size(lD, lD)
                )
            )
            // Center bump (tallest)
            val cD = w * 0.46f
            addOval(
                Rect(
                    offset = Offset(cx - cD / 2f, (cloudCy - h * 0.10f) - cD / 2f),
                    size = Size(cD, cD)
                )
            )
            // Right bump
            val rD = w * 0.34f
            addOval(
                Rect(
                    offset = Offset((cx + w * 0.22f) - rD / 2f, (cloudCy - h * 0.03f) - rD / 2f),
                    size = Size(rD, rD)
                )
            )
        }

        // Outer glow (two passes, decreasing width, increasing alpha).
        drawPath(
            path = cloudPath,
            color = BrandCyanDark.copy(alpha = 0.35f),
            style = Stroke(width = w * 0.09f)
        )
        drawPath(
            path = cloudPath,
            color = BrandCyanDark.copy(alpha = 0.6f),
            style = Stroke(width = w * 0.045f)
        )
        // Cloud fill: vertical gradient cyan -> teal.
        drawPath(
            path = cloudPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    BrandCyanDark.copy(alpha = 0.7f),
                    BrandPrimaryTeal.copy(alpha = 0.9f)
                ),
                startY = cloudCy - h * 0.25f,
                endY = cloudCy + h * 0.25f
            )
        )

        // Face geometry
        val eyeY = cloudCy
        val eyebrowY = eyeY - h * 0.045f
        val eyeOffset = w * 0.095f

        // Left eyebrow (angry diagonal)
        drawLine(
            color = Color.White,
            start = Offset(cx - eyeOffset - w * 0.035f, eyebrowY + h * 0.012f),
            end = Offset(cx - eyeOffset + w * 0.025f, eyebrowY - h * 0.015f),
            strokeWidth = 3.5f * (w / 220f),
            cap = StrokeCap.Round
        )
        // Right eyebrow (angry diagonal, mirrored)
        drawLine(
            color = Color.White,
            start = Offset(cx + eyeOffset + w * 0.035f, eyebrowY + h * 0.012f),
            end = Offset(cx + eyeOffset - w * 0.025f, eyebrowY - h * 0.015f),
            strokeWidth = 3.5f * (w / 220f),
            cap = StrokeCap.Round
        )

        // Vertical slit eyes (4x14 rounded rectangles, scaled with illustration).
        val eyeW = 4f * (w / 220f)
        val eyeH = 14f * (w / 220f)
        val eyeTop = eyeY + h * 0.025f - eyeH / 2f
        drawPath(
            path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = cx - eyeOffset - eyeW / 2f,
                        top = eyeTop,
                        right = cx - eyeOffset + eyeW / 2f,
                        bottom = eyeTop + eyeH,
                        cornerRadius = CornerRadius(eyeW / 2f)
                    )
                )
            },
            color = Color.White
        )
        drawPath(
            path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = cx + eyeOffset - eyeW / 2f,
                        top = eyeTop,
                        right = cx + eyeOffset + eyeW / 2f,
                        bottom = eyeTop + eyeH,
                        cornerRadius = CornerRadius(eyeW / 2f)
                    )
                )
            },
            color = Color.White
        )

        // Frown arc (opens upward). Arc center ~ (cx, cloudCy + 0.11h), r = 0.08w,
        // from 200° to 340° => use arcTo on a square rect.
        val frownR = w * 0.08f
        val frownCy = cloudCy + h * 0.11f
        val frownRect = Rect(
            offset = Offset(cx - frownR, frownCy - frownR),
            size = Size(frownR * 2f, frownR * 2f)
        )
        val frownPath = Path().apply {
            arcTo(
                rect = frownRect,
                startAngleDegrees = 200f,
                sweepAngleDegrees = 140f,
                forceMoveTo = true
            )
        }
        drawPath(
            path = frownPath,
            color = Color.White,
            style = Stroke(width = 3f * (w / 220f), cap = StrokeCap.Round)
        )

        // Two 5-point cyan stars with glow below the cloud.
        val starSize = w * 0.11f
        val starY = h * 0.84f
        drawNeonStar(centerX = cx - w * 0.12f, centerY = starY, size = starSize)
        drawNeonStar(centerX = cx + w * 0.12f, centerY = starY, size = starSize)
    }
}

/** Draw a filled 5-point star with a cyan glow, centered at (centerX, centerY). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeonStar(
    centerX: Float,
    centerY: Float,
    size: Float
) {
    val starPath = starPath(centerX, centerY, size)
    // Glow pass
    drawPath(
        path = starPath,
        color = BrandCyanDark.copy(alpha = 0.5f),
        style = Stroke(width = size * 0.4f)
    )
    // Fill
    drawPath(path = starPath, color = BrandCyanDark)
}

private fun starPath(cx: Float, cy: Float, size: Float): Path {
    val outer = size / 2f
    val inner = outer * 0.42f
    val pointCount = 5
    val path = Path()
    for (i in 0 until pointCount * 2) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = -Math.PI.toFloat() / 2f + i.toFloat() * Math.PI.toFloat() / pointCount.toFloat()
        val x = cx + radius * cos(angle)
        val y = cy + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
