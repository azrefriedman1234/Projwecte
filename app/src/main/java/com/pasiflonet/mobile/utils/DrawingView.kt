package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val currentRects = mutableListOf<RectF>()
    private val paint = Paint().apply {
        color = 0x80FF0000.toInt()
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
    
    // גבולות התמונה האמיתיים (מחושב מבחוץ)
    private var validBounds = RectF(0f, 0f, 0f, 0f)

    fun setValidBounds(bounds: RectF) {
        this.validBounds = bounds
    }

    // החזרת ריבועים מנורמלים (0.0-1.0) ביחס לתמונה
    fun getRectsRelative(imageBounds: RectF): List<BlurRect> {
        val result = mutableListOf<BlurRect>()
        if (imageBounds.width() <= 0 || imageBounds.height() <= 0) return result
        
        for (r in currentRects) {
            // חיתוך הריבוע לגבולות התמונה (Clipping)
            val left = Math.max(r.left, imageBounds.left)
            val top = Math.max(r.top, imageBounds.top)
            val right = Math.min(r.right, imageBounds.right)
            val bottom = Math.min(r.bottom, imageBounds.bottom)
            
            if (right > left && bottom > top) {
                // נרמול ל-0..1
                val relLeft = (left - imageBounds.left) / imageBounds.width()
                val relRight = (right - imageBounds.left) / imageBounds.width()
                val relTop = (top - imageBounds.top) / imageBounds.height()
                val relBottom = (bottom - imageBounds.top) / imageBounds.height()
                
                result.add(BlurRect(relLeft, relTop, relRight, relBottom))
            }
        }
        return result
    }

    val rects: List<BlurRect> // תאימות לאחור
        get() = getRectsRelative(validBounds)

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
                val left = Math.min(startX, currentX)
                val right = Math.max(startX, currentX)
                val top = Math.min(startY, currentY)
                val bottom = Math.max(startY, currentY)
                
                if (Math.abs(right - left) > 10 && Math.abs(bottom - top) > 10) {
                    currentRects.add(RectF(left, top, right, bottom))
                }
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // ציור ריבועים
        for (rect in currentRects) {
            canvas.drawRect(rect, paint)
            canvas.drawRect(rect, borderPaint)
        }
        // ציור תוך כדי גרירה
        if (isDrawing) {
            val l = Math.min(startX, currentX)
            val r = Math.max(startX, currentX)
            val t = Math.min(startY, currentY)
            val b = Math.max(startY, currentY)
            canvas.drawRect(l, t, r, b, paint)
        }
    }
}
