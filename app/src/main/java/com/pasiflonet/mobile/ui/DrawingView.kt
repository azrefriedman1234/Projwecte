package com.pasiflonet.mobile.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// נתוני מלבן טשטוש יחסיים (0.0 עד 1.0)
data class BlurRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 20f), 0f)
    }
    private val blurFill = Paint().apply {
        color = 0x55000000 // שחור חצי שקוף להמחיש טשטוש
        style = Paint.Style.FILL
    }

    var isBlurMode = false
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false
    
    // רשימת המלבנים שנשמרו (בקואורדינטות יחסיות)
    val rects = mutableListOf<BlurRect>()

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
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                // שמירה יחסית לגודל התצוגה
                if (width > 0 && height > 0) {
                    val left = Math.min(startX, currentX) / width
                    val right = Math.max(startX, currentX) / width
                    val top = Math.min(startY, currentY) / height
                    val bottom = Math.max(startY, currentY) / height
                    rects.add(BlurRect(left, top, right, bottom))
                }
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // ציור המלבנים השמורים
        rects.forEach { r ->
            canvas.drawRect(r.left * width, r.top * height, r.right * width, r.bottom * height, blurFill)
            canvas.drawRect(r.left * width, r.top * height, r.right * width, r.bottom * height, paint)
        }
        // ציור המלבן שכרגע מצוייר
        if (isDrawing) {
            val left = Math.min(startX, currentX)
            val right = Math.max(startX, currentX)
            val top = Math.min(startY, currentY)
            val bottom = Math.max(startY, currentY)
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
    
    fun clear() {
        rects.clear()
        invalidate()
    }
}
