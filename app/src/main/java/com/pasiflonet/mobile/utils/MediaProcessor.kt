package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.pasiflonet.mobile.ui.BlurRect
import java.io.File
import java.io.FileOutputStream

object MediaProcessor {

    // הפונקציה הזו רצה ברקע וצורבת את העריכה על התמונה המקורית
    fun processImage(
        context: Context,
        inputPath: String,
        outputPath: String,
        blurRects: List<BlurRect>,
        logoUri: Uri?,
        logoX_percent: Float, // מיקום יחסי משמאל
        logoY_percent: Float, // מיקום יחסי מלמעלה
        logoScale: Float      // קנה מידה
    ) {
        val options = BitmapFactory.Options().apply { inMutable = true }
        var original = BitmapFactory.decodeFile(inputPath, options) ?: return
        
        // יצירת קנבס לציור על התמונה
        val canvas = Canvas(original)
        val w = original.width
        val h = original.height

        // 1. יישום טשטוש
        blurRects.forEach { r ->
            val left = (r.left * w).toInt()
            val top = (r.top * h).toInt()
            val right = (r.right * w).toInt()
            val bottom = (r.bottom * h).toInt()
            
            if (right > left && bottom > top) {
                // חיתוך האזור, הקטנה (פיקסול), והגדלה חזרה
                val subset = Bitmap.createBitmap(original, left, top, right - left, bottom - top)
                val pixelated = Bitmap.createScaledBitmap(subset, Math.max(1, subset.width / 20), Math.max(1, subset.height / 20), false)
                val blurred = Bitmap.createScaledBitmap(pixelated, subset.width, subset.height, true)
                canvas.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
            }
        }

        // 2. הוספת לוגו
        logoUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val logo = BitmapFactory.decodeStream(stream)
                    if (logo != null) {
                        // חישוב גודל הלוגו יחסית לתמונה המקורית
                        // נניח שגודל בסיס הוא 20% רוחב תמונה * הסקייל מהסליידר
                        val baseWidth = w * 0.2f * logoScale
                        val ratio = logo.height.toFloat() / logo.width.toFloat()
                        val finalW = baseWidth.toInt()
                        val finalH = (baseWidth * ratio).toInt()
                        
                        val scaledLogo = Bitmap.createScaledBitmap(logo, finalW, finalH, true)
                        
                        val drawX = logoX_percent * w
                        val drawY = logoY_percent * h
                        
                        canvas.drawBitmap(scaledLogo, drawX, drawY, null)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 3. שמירה לקובץ חדש
        FileOutputStream(File(outputPath)).use { out ->
            original.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }
}
