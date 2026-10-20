package com.mobilesecurity.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Draws a repeating diagonal watermark across the whole view. The text is laid out at ~30°
 * so a screenshot cannot be cropped to hide every copy; even the corners of the frame will
 * contain at least part of a tag.
 */
@SuppressLint("ViewConstructor")
class WatermarkView(
    context: Context,
    private val textLine: String
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)       // light tag — visible on dark content
        textSize = 44f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        isAntiAlias = true
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)             // dark tag — visible on light content
        textSize = 44f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        isAntiAlias = true
    }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Diagonal diameter so stripes still cover the view after rotation.
        val diag = sqrt(w * w + h * h)
        val step = 220f
        val textWidth = paint.measureText(textLine).let { max(it, 1f) }

        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.rotate(-30f)
        canvas.translate(-diag / 2f, -diag / 2f)

        var y = 0f
        var row = 0
        while (y < diag) {
            // Alternate rows are offset so grids line up less obviously.
            val offsetX = if (row % 2 == 0) 0f else textWidth / 2f
            var x = offsetX - diag / 2f
            while (x < diag) {
                canvas.drawText(textLine, x + 2f, y + 2f, shadowPaint)
                canvas.drawText(textLine, x, y, paint)
                x += textWidth + step
            }
            y += step
            row++
        }
        canvas.restore()
    }
}
