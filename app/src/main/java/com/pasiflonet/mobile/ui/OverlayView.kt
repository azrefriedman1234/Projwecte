package com.pasiflonet.mobile.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

data class BlurRect(val rect: RectF)

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paths = mutableListOf<Path>()
    private var currentPath = Path()
    private val paint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 60f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        paths.forEach { canvas.drawPath(it, paint) }
        canvas.drawPath(currentPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> currentPath.moveTo(x, y)
            MotionEvent.ACTION_MOVE -> currentPath.lineTo(x, y)
            MotionEvent.ACTION_UP -> {
                paths.add(Path(currentPath))
                currentPath.reset()
            }
        }
        invalidate()
        return true
    }

    fun undo() { if (paths.isNotEmpty()) { paths.removeAt(paths.size - 1); invalidate() } }
    fun clear() { paths.clear(); invalidate() }
    
    fun getBlurRects(): List<BlurRect> {
        return paths.map { path ->
            val rect = RectF()
            path.computeBounds(rect, true)
            // Normalize coordinates relative to view size
            BlurRect(RectF(rect.left / width, rect.top / height, rect.right / width, rect.bottom / height))
        }
    }
}
