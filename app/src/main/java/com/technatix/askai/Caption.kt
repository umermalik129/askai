package com.technatix.askai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Draws a message as a readable caption strip under a screenshot. */
object Caption {

    fun apply(src: Bitmap, text: String): Bitmap {
        val pad = (src.width * 0.035f).coerceAtLeast(20f)
        val textSize = (src.width / 26f).coerceIn(30f, 72f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, (src.width - 2 * pad).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .build()

        val stripHeight = (layout.height + 2 * pad).toInt()
        val out = Bitmap.createBitmap(src.width, src.height + stripHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)

        val divider = Paint().apply { color = 0xFFBDBDBD.toInt() }
        canvas.drawRect(0f, src.height.toFloat(), src.width.toFloat(), src.height + 3f, divider)

        canvas.save()
        canvas.translate(pad, src.height + pad)
        layout.draw(canvas)
        canvas.restore()
        return out
    }
}
