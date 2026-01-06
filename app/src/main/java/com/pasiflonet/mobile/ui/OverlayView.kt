package com.pasiflonet.mobile.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val rects = mutableListOf<RectF>()
    private var currentRect: RectF? = null
    private var mode = Mode.ADD // ADD / REMOVE

    enum class Mode { ADD, REMOVE }

    fun getNormalizedRects(width: Int, height: Int): List<FloatArray> {
        return rects.map { floatArrayOf(it.left / width, it.top / height, it.width() / width, it.height() / height) }
    }

    fun undo() {
        if (rects.isNotEmpty()) rects.removeLast()
        invalidate()
    }

    fun clearAll() {
        rects.clear()
        invalidate()
    }

    // Stub for touch: add real onTouchEvent for drag/resize
    override fun onDraw(canvas: Canvas) {
        val paint = Paint().apply { color = Color.argb(100, 0, 0, 255); style = Paint.Style.FILL }
        rects.forEach { canvas.drawRect(it, paint) }
    }
}
