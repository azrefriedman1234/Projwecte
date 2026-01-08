package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val currentRects = mutableListOf<BlurRect>()
    private val paint = Paint().apply {
        color = 0x80FF0000.toInt() // אדום חצי שקוף
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    var isBlurMode = false
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false

    val rects: List<BlurRect>
        get() = currentRects.toList()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isBlurMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                isDrawing = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                // שמירת הריבוע באופן יחסי (0.0 עד 1.0)
                if (width > 0 && height > 0) {
                    val left = Math.min(startX, currentX) / width
                    val right = Math.max(startX, currentX) / width
                    val top = Math.min(startY, currentY) / height
                    val bottom = Math.max(startY, currentY) / height
                    
                    // הוספה רק אם הריבוע בגודל סביר
                    if (Math.abs(right - left) > 0.01 && Math.abs(bottom - top) > 0.01) {
                        currentRects.add(BlurRect(left, top, right, bottom))
                    }
                }
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // ציור ריבועים קיימים
        for (rect in currentRects) {
            val l = rect.left * width
            val r = rect.right * width
            val t = rect.top * height
            val b = rect.bottom * height
            canvas.drawRect(l, t, r, b, paint)
            canvas.drawRect(l, t, r, b, borderPaint)
        }

        // ציור הריבוע שכרגע נמתח
        if (isDrawing) {
            val l = Math.min(startX, currentX)
            val r = Math.max(startX, currentX)
            val t = Math.min(startY, currentY)
            val b = Math.max(startY, currentY)
            canvas.drawRect(l, t, r, b, paint)
        }
    }

    fun clear() {
        currentRects.clear()
        invalidate()
    }
    
    fun undo() {
        if (currentRects.isNotEmpty()) {
            currentRects.removeAt(currentRects.lastIndex)
            invalidate()
        }
    }
}
