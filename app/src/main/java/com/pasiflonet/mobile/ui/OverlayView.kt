package com.pasiflonet.mobile.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

data class BlurRect(val rect: RectF)

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#44FF0000") 
        style = Paint.Style.FILL
    }

    private val rects = mutableListOf<BlurRect>()
    private var currentRect: RectF? = null
    private var startX = 0f
    private var startY = 0f

    fun getRectsRelative(): List<BlurRect> {
        return rects.map { 
            BlurRect(RectF(it.rect.left / width, it.rect.top / height, it.rect.right / width, it.rect.bottom / height))
        }
    }

    fun clear() {
        rects.clear()
        invalidate()
    }
    
    fun undo() {
        if (rects.isNotEmpty()) {
            rects.removeAt(rects.lastIndex)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (r in rects) {
            canvas.drawRect(r.rect, fillPaint)
            canvas.drawRect(r.rect, paint)
        }
        currentRect?.let {
            canvas.drawRect(it, fillPaint)
            canvas.drawRect(it, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentRect = RectF(startX, startY, startX, startY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentRect?.let {
                    it.right = event.x
                    it.bottom = event.y
                    it.sort()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                currentRect?.let {
                    rects.add(BlurRect(it))
                    currentRect = null
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
