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
        logoUri: Uri?
    ) {
        val options = BitmapFactory.Options().apply { inMutable = true }
        val original = BitmapFactory.decodeFile(inputPath, options) ?: return
        
        val canvas = Canvas(original)
        val bitmapWidth = original.width
        val bitmapHeight = original.height

        // 1. ביצוע טשטוש (Pixelation) על האזורים שנבחרו
        val blurPaint = Paint().apply { isAntiAlias = true }
        
        blurRects.forEach { relative ->
            // המרה מקואורדינטות מסך (0.0 עד 1.0) לפיקסלים אמיתיים בביטמפ
            val left = (relative.rect.left * bitmapWidth).toInt().coerceAtLeast(0)
            val top = (relative.rect.top * bitmapHeight).toInt().coerceAtLeast(0)
            val right = (relative.rect.right * bitmapWidth).toInt().coerceAtMost(bitmapWidth)
            val bottom = (relative.rect.bottom * bitmapHeight).toInt().coerceAtMost(bitmapHeight)

            val width = right - left
            val height = bottom - top

            if (width > 10 && height > 10) {
                // גזירת החלק המיועד לטשטוש
                val subset = Bitmap.createBitmap(original, left, top, width, height)
                // הקטנה משמעותית והגדלה חזרה יוצרת אפקט פיקסליזציה (טשטוש)
                val pixelated = Bitmap.createScaledBitmap(subset, width / 20 + 1, height / 20 + 1, false)
                val finalBlur = Bitmap.createScaledBitmap(pixelated, width, height, true)
                
                canvas.drawBitmap(finalBlur, left.toFloat(), top.toFloat(), blurPaint)
            }
        }

        // 2. הוספת סימן מים (Watermark)
        logoUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val logo = BitmapFactory.decodeStream(stream)
                    if (logo != null) {
                        // חישוב גודל לוגו (נניח 15% מרוחב התמונה)
                        val logoW = (bitmapWidth * 0.15).toInt()
                        val logoH = (logo.height * logoW) / logo.width
                        val scaledLogo = Bitmap.createScaledBitmap(logo, logoW, logoH, true)
                        
                        // ציור הלוגו בפינה הימנית העליונה (עם מרווח של 20 פיקסלים)
                        canvas.drawBitmap(scaledLogo, (bitmapWidth - logoW - 20).toFloat(), 20f, null)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 3. שמירת התוצאה
        FileOutputStream(File(outputPath)).use { out ->
            original.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }
}
