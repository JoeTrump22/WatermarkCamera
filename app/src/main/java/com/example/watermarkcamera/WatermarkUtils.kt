package com.example.watermarkcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.min

object WatermarkUtils {

    fun createWatermarkBitmap(
        sourceBitmap: Bitmap,
        timeText: String,
        locationText: String,
        weatherText: String
    ): Bitmap {
        val resultBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val width = resultBitmap.width
        val height = resultBitmap.height

        val textSize = min(width, height) * 0.025f
        val padding = width * 0.04f

        val timePaint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }

        val subTextSize = textSize * 0.8f
        val subPaint = Paint().apply {
            color = Color.WHITE
            this.textSize = subTextSize
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }

        val timeBounds = android.graphics.Rect()
        timePaint.getTextBounds(timeText, 0, timeText.length, timeBounds)
        val timeHeight = timeBounds.height()

        val subBounds = android.graphics.Rect()
        subPaint.getTextBounds(locationText, 0, locationText.length, subBounds)
        val subHeight = subBounds.height()

        val lineSpacing = textSize * 0.5f
        val totalHeight = timeHeight + subHeight * 2 + lineSpacing

        val bgRect = android.graphics.RectF(
            padding * 0.5f,
            height - totalHeight - padding * 1.5f,
            width - padding * 0.5f,
            height - padding * 0.5f
        )

        val bgPaint = Paint().apply {
            color = Color.argb(140, 0, 0, 0)
            isAntiAlias = true
        }

        canvas.drawRoundRect(bgRect, 24f, 24f, bgPaint)

        val textX = padding
        val startY = height - padding * 0.5f

        canvas.drawText(weatherText, textX, startY - subHeight, subPaint)
        canvas.drawText(locationText, textX, startY - subHeight * 2 - lineSpacing * 0.3f, subPaint)
        canvas.drawText(timeText, textX, startY - subHeight * 2 - timeHeight - lineSpacing * 0.6f, timePaint)

        return resultBitmap
    }

    fun createPreviewWatermark(
        timeText: String,
        locationText: String,
        weatherText: String,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val textSize = min(width, height) * 0.028f
        val padding = width * 0.04f

        val timePaint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }

        val subTextSize = textSize * 0.85f
        val subPaint = Paint().apply {
            color = Color.WHITE
            this.textSize = subTextSize
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }

        val timeBounds = android.graphics.Rect()
        timePaint.getTextBounds(timeText, 0, timeText.length, timeBounds)
        val timeHeight = timeBounds.height()

        val subBounds = android.graphics.Rect()
        subPaint.getTextBounds(locationText, 0, locationText.length, subBounds)
        val subHeight = subBounds.height()

        val lineSpacing = textSize * 0.4f
        val totalHeight = timeHeight + subHeight * 2 + lineSpacing * 2

        val bgRect = android.graphics.RectF(
            padding * 0.5f,
            height - totalHeight - padding * 1.2f,
            width - padding * 0.5f,
            height - padding * 0.3f
        )

        val bgPaint = Paint().apply {
            color = Color.argb(140, 0, 0, 0)
            isAntiAlias = true
        }

        canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)

        val textX = padding
        val startY = height - padding * 0.3f

        canvas.drawText(weatherText, textX, startY - subHeight, subPaint)
        canvas.drawText(locationText, textX, startY - subHeight * 2 - lineSpacing * 0.5f, subPaint)
        canvas.drawText(timeText, textX, startY - subHeight * 2 - timeHeight - lineSpacing * 0.8f, timePaint)

        return bitmap
    }
}