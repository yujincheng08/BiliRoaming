package me.iacn.biliroaming.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.style.ReplacementSpan

class StrokeSpan(private val fillColor: Int, private val strokeColor: Int, private val strokeWidth: Float) : ReplacementSpan() {
    private fun fillPaint(paint: Paint): TextPaint =
        TextPaint(paint).apply {
            style = Paint.Style.FILL
            color = fillColor
        }

    private fun stokePaint(paint: Paint): TextPaint =
        TextPaint(paint).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            strokeWidth = this@StrokeSpan.strokeWidth
        }

    override fun getSize(
        p0: Paint,
        p1: CharSequence?,
        p2: Int,
        p3: Int,
        p4: Paint.FontMetricsInt?
    ): Int {
        return p0.measureText(p1, p2, p3).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        text ?: return
        canvas.drawText(text, start, end, x, y.toFloat(), fillPaint(paint))
        if (strokeWidth > 0)
            canvas.drawText(text, start, end, x, y.toFloat(), stokePaint(paint))
    }
}