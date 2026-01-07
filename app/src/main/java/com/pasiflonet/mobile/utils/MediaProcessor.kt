package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.pasiflonet.mobile.ui.BlurRect
import java.io.File
import java.io.FileOutputStream

object MediaProcessor {
    fun processImage(ctx: Context, inPath: String, outPath: String, rects: List<BlurRect>, logoUri: Uri?, lX: Float, lY: Float, lScale: Float) {
        val opts = BitmapFactory.Options().apply { inMutable = true }
        var original = BitmapFactory.decodeFile(inPath, opts) ?: return
        val canvas = Canvas(original)
        val w = original.width; val h = original.height

        rects.forEach { r ->
            val left = (r.left * w).toInt(); val top = (r.top * h).toInt(); val right = (r.right * w).toInt(); val bottom = (r.bottom * h).toInt()
            if (right > left && bottom > top) {
                val subset = Bitmap.createBitmap(original, left, top, right - left, bottom - top)
                val pixelated = Bitmap.createScaledBitmap(subset, Math.max(1, subset.width/20), Math.max(1, subset.height/20), false)
                val blurred = Bitmap.createScaledBitmap(pixelated, subset.width, subset.height, true)
                canvas.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
            }
        }

        logoUri?.let { uri ->
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val logo = BitmapFactory.decodeStream(stream)
                    if (logo != null) {
                        val baseW = w * 0.2f * lScale
                        val ratio = logo.height.toFloat() / logo.width.toFloat()
                        val scaled = Bitmap.createScaledBitmap(logo, baseW.toInt(), (baseW * ratio).toInt(), true)
                        canvas.drawBitmap(scaled, lX * w, lY * h, null)
                    }
                }
            } catch (e: Exception) {}
        }

        FileOutputStream(File(outPath)).use { out -> original.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    }
}
