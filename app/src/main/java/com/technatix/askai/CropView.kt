package com.technatix.askai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/** Shows a screenshot with a box that can be moved and resized from every side and corner. */
class CropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            box = null
            computeMatrix()
            invalidate()
        }

    private val matrix = Matrix()
    private val inverse = Matrix()
    private val imageRect = RectF()
    private var box: RectF? = null

    private var resizeLeft = false
    private var resizeTop = false
    private var resizeRight = false
    private var resizeBottom = false
    private var moving = false
    private var lastX = 0f
    private var lastY = 0f

    private val density = resources.displayMetrics.density
    private val hitRadius = 24 * density
    private val minSize = 48 * density
    private val handleRadius = 7 * density

    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF6C4DF6.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeMatrix()

    private fun computeMatrix() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val dx = (width - b.width * scale) / 2f
        val dy = (height - b.height * scale) / 2f
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        matrix.invert(inverse)
        imageRect.set(dx, dy, dx + b.width * scale, dy + b.height * scale)
        if (box == null) box = defaultBox()
    }

    private fun defaultBox(): RectF {
        val w = imageRect.width() * 0.84f
        val h = imageRect.height() * 0.4f
        val cx = imageRect.centerX()
        val cy = imageRect.centerY()
        return RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.drawBitmap(b, matrix, null)
        val r = box ?: return
        canvas.save()
        canvas.clipOutRect(r)
        canvas.drawRect(imageRect, dim)
        canvas.restore()
        canvas.drawRect(r, stroke)
        // Corner handles
        handle(canvas, r.left, r.top)
        handle(canvas, r.right, r.top)
        handle(canvas, r.left, r.bottom)
        handle(canvas, r.right, r.bottom)
        // Edge handles
        handle(canvas, r.centerX(), r.top)
        handle(canvas, r.centerX(), r.bottom)
        handle(canvas, r.left, r.centerY())
        handle(canvas, r.right, r.centerY())
    }

    private fun handle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handleFill)
        canvas.drawCircle(x, y, handleRadius, handleRing)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val r = box ?: return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val withinY = y in (r.top - hitRadius)..(r.bottom + hitRadius)
                val withinX = x in (r.left - hitRadius)..(r.right + hitRadius)
                resizeLeft = withinY && abs(x - r.left) <= hitRadius
                resizeRight = withinY && abs(x - r.right) <= hitRadius
                resizeTop = withinX && abs(y - r.top) <= hitRadius
                resizeBottom = withinX && abs(y - r.bottom) <= hitRadius
                moving = !(resizeLeft || resizeRight || resizeTop || resizeBottom) && r.contains(x, y)
                lastX = x
                lastY = y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                lastX = x
                lastY = y
                if (moving) {
                    move(r, dx, dy)
                } else {
                    if (resizeLeft) r.left = (r.left + dx).coerceIn(imageRect.left, r.right - minSize)
                    if (resizeRight) r.right = (r.right + dx).coerceIn(r.left + minSize, imageRect.right)
                    if (resizeTop) r.top = (r.top + dy).coerceIn(imageRect.top, r.bottom - minSize)
                    if (resizeBottom) r.bottom = (r.bottom + dy).coerceIn(r.top + minSize, imageRect.bottom)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moving = false
                resizeLeft = false
                resizeRight = false
                resizeTop = false
                resizeBottom = false
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun move(r: RectF, dx: Float, dy: Float) {
        var mx = dx
        var my = dy
        if (r.left + mx < imageRect.left) mx = imageRect.left - r.left
        if (r.right + mx > imageRect.right) mx = imageRect.right - r.right
        if (r.top + my < imageRect.top) my = imageRect.top - r.top
        if (r.bottom + my > imageRect.bottom) my = imageRect.bottom - r.bottom
        r.offset(mx, my)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** The part of the bitmap inside the box. */
    fun selectedBitmap(): Bitmap {
        val b = bitmap ?: throw IllegalStateException("No bitmap")
        val r = box ?: return b
        val src = RectF(r)
        inverse.mapRect(src)
        val left = src.left.toInt().coerceIn(0, b.width - 1)
        val top = src.top.toInt().coerceIn(0, b.height - 1)
        val w = src.width().toInt().coerceIn(1, b.width - left)
        val h = src.height().toInt().coerceIn(1, b.height - top)
        return Bitmap.createBitmap(b, left, top, w, h)
    }
}
