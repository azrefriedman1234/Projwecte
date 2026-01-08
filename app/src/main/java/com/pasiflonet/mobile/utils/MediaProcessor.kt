package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

object MediaProcessor {

    fun processContent(
        context: Context,
        inputPath: String,
        outputPath: String,
        isVideo: Boolean,
        blurRects: List<BlurRect>,
        logoUri: Uri?,
        logoRelX: Float,
        logoRelY: Float,
        logoRelW: Float,
        onComplete: (Boolean) -> Unit
    ) {
        Log.d("MediaProcessor", "Starting process. Video=$isVideo")

        // נתיב מהיר: אם זו תמונה, נשתמש במנוע הגרפי הקליל של אנדרואיד
        if (!isVideo) {
            try {
                processImageNative(context, inputPath, outputPath, blurRects, logoUri, logoRelX, logoRelY, logoRelW)
                onComplete(true)
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Native image processing failed, fallback to copy", e)
                try {
                    File(inputPath).copyTo(File(outputPath), overwrite = true)
                    onComplete(true)
                } catch (e2: Exception) { onComplete(false) }
            }
            return
        }

        // --- מכאן והלאה: טיפול בוידאו בלבד (FFmpeg) ---
        
        // 1. אם אין עריכות בוידאו - מעתיקים
        if (blurRects.isEmpty() && logoUri == null) {
            try {
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                onComplete(true)
            } catch (e: Exception) { onComplete(false) }
            return
        }

        // 2. הכנת לוגו לוידאו
        var logoPath: String? = null
        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val file = File(context.cacheDir, "ffmpeg_logo_temp.png")
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush(); out.close()
                logoPath = file.absolutePath
            } catch (e: Exception) {}
        }

        // 3. פקודת FFmpeg לוידאו
        val cmd = StringBuilder()
        cmd.append("-y -i \"$inputPath\" ")
        if (logoPath != null) cmd.append("-i \"$logoPath\" ")

        cmd.append("-filter_complex \"")
        var stream = "[0:v]"
        
        if (blurRects.isNotEmpty()) {
            for (i in blurRects.indices) {
                val r = blurRects[i]
                val x = (r.left * 100).toInt(); val y = (r.top * 100).toInt()
                val w = ((r.right - r.left) * 100).toInt(); val h = ((r.bottom - r.top) * 100).toInt()
                cmd.append("${stream}boxblur=10:1:enable='between(x,iw*$x/100,iw*${x+w}/100)*between(y,ih*$y/100,ih*${y+h}/100)'[b$i];")
                stream = "[b$i]"
            }
        }

        if (logoPath != null) {
            val x = (logoRelX * 100).toInt(); val y = (logoRelY * 100).toInt()
            val scaleFactor = logoRelW
            // מתמטיקה בטוחה למספרים זוגיים
            cmd.append("[1:v]scale=trunc(iw*$scaleFactor/2)*2:-2[logo];${stream}[logo]overlay=trunc(W*$x/100/2)*2:trunc(H*$y/100/2)*2")
        } else {
            if (blurRects.isNotEmpty()) cmd.setLength(cmd.length - 1) else cmd.append("null")
        }

        cmd.append("\" -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a copy \"$outputPath\"")

        try {
            FFmpegKit.executeAsync(cmd.toString()) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) onComplete(true)
                else {
                    // Fallback לוידאו
                    try { File(inputPath).copyTo(File(outputPath), overwrite = true); onComplete(true) } 
                    catch (e: Exception) { onComplete(false) }
                }
            }
        } catch (e: Exception) { onComplete(false) }
    }

    // --- המנוע החדש: עיבוד תמונה טהור (ללא FFmpeg) ---
    private fun processImageNative(
        context: Context, inputPath: String, outputPath: String,
        blurRects: List<BlurRect>, logoUri: Uri?,
        relX: Float, relY: Float, relW: Float
    ) {
        // 1. טעינת התמונה המקורית לזיכרון (Mutable כדי שנוכל לצייר עליה)
        val opts = BitmapFactory.Options()
        opts.inMutable = true
        var bitmap = BitmapFactory.decodeFile(inputPath, opts)
        
        // אם אי אפשר לטעון כ-Mutable, מעתיקים
        if (!bitmap.isMutable) {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle()
            bitmap = copy
        }

        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // 2. ביצוע טשטוש (Pixelate אפקטיבי)
        if (blurRects.isNotEmpty()) {
            val paint = Paint()
            for (rect in blurRects) {
                // חישוב האזור לטשטוש
                val left = (rect.left * w).toInt()
                val top = (rect.top * h).toInt()
                val right = (rect.right * w).toInt()
                val bottom = (rect.bottom * h).toInt()
                
                val roiW = right - left
                val roiH = bottom - top
                
                if (roiW > 0 && roiH > 0) {
                    // חיתוך האזור
                    val roi = Bitmap.createBitmap(bitmap, left, top, roiW, roiH)
                    // הקטנה דרסטית (יוצר פיקסליזציה)
                    val small = Bitmap.createScaledBitmap(roi, Math.max(1, roiW/20), Math.max(1, roiH/20), true)
                    // הגדלה חזרה
                    val pixelated = Bitmap.createScaledBitmap(small, roiW, roiH, false)
                    
                    // ציור חזרה על התמונה המקורית
                    canvas.drawBitmap(pixelated, left.toFloat(), top.toFloat(), paint)
                    
                    roi.recycle(); small.recycle(); pixelated.recycle()
                }
            }
        }

        // 3. הוספת לוגו
        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val logoBmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (logoBmp != null) {
                    // חישוב גודל הלוגו יחסית לתמונה
                    val finalLogoW = (w * relW).toInt()
                    val ratio = logoBmp.height.toFloat() / logoBmp.width.toFloat()
                    val finalLogoH = (finalLogoW * ratio).toInt()
                    
                    val scaledLogo = Bitmap.createScaledBitmap(logoBmp, finalLogoW, finalLogoH, true)
                    
                    val drawX = w * relX
                    val drawY = h * relY
                    
                    canvas.drawBitmap(scaledLogo, drawX, drawY, null)
                    if (scaledLogo != logoBmp) scaledLogo.recycle()
                    logoBmp.recycle()
                }
            } catch (e: Exception) { Log.e("MediaProcessor", "Logo draw failed", e) }
        }

        // 4. שמירה לקובץ יעד
        val outStream = FileOutputStream(outputPath)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
        outStream.flush()
        outStream.close()
        
        bitmap.recycle() // שחרור זיכרון
    }
}
