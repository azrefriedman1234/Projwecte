package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.pasiflonet.mobile.ui.BlurRect
import java.io.File
import java.io.FileOutputStream

object MediaProcessor {
    fun processImage(
        context: Context,
        inputPath: String,
        outputPath: String,
        blurRects: List<BlurRect>,
        logoUri: Uri?,
        logoX: Float,
        logoY: Float
    ) {
        val options = BitmapFactory.Options().apply { inMutable = true }
        val original = BitmapFactory.decodeFile(inputPath, options) ?: return
        val canvas = Canvas(original)
        val bW = original.width
        val bH = original.height

        blurRects.forEach { relative ->
            val left = (relative.rect.left * bW).toInt().coerceAtLeast(0)
            val top = (relative.rect.top * bH).toInt().coerceAtLeast(0)
            val right = (relative.rect.right * bW).toInt().coerceAtMost(bW)
            val bottom = (relative.rect.bottom * bH).toInt().coerceAtMost(bH)
            val w = right - left
            val h = bottom - top
            if (w > 5 && h > 5) {
                val subset = Bitmap.createBitmap(original, left, top, w, h)
                val pixelated = Bitmap.createScaledBitmap(subset, w/20 + 1, h/20 + 1, false)
                val finalBlur = Bitmap.createScaledBitmap(pixelated, w, h, true)
                canvas.drawBitmap(finalBlur, left.toFloat(), top.toFloat(), null)
            }
        }

        logoUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val logo = BitmapFactory.decodeStream(stream)
                    if (logo != null) {
                        val targetLogoW = (bW * 0.15).toInt()
                        val targetLogoH = (logo.height * targetLogoW) / logo.width
                        val scaledLogo = Bitmap.createScaledBitmap(logo, targetLogoW, targetLogoH, true)
                        canvas.drawBitmap(scaledLogo, logoX * bW, logoY * bH, null)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        FileOutputStream(File(outputPath)).use { out ->
            original.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }
}
