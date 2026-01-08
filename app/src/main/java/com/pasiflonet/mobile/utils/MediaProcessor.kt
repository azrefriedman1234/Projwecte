package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
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
        Log.d("MediaProcessor", "Starting safe process. Video=$isVideo")

        // תמונות: שימוש במנוע הגרפי הבטוח (Native)
        if (!isVideo) {
            try {
                processImageSafe(context, inputPath, outputPath, blurRects, logoUri, logoRelX, logoRelY, logoRelW)
                onComplete(true)
            } catch (t: Throwable) { // תופס גם OutOfMemoryError!
                Log.e("MediaProcessor", "CRITICAL: Processing failed/OOM. Fallback to copy.", t)
                fallbackCopy(inputPath, outputPath, onComplete)
            }
            return
        }

        // --- וידאו (FFmpeg) ---
        // אם אין עריכות - מעתיקים
        if (blurRects.isEmpty() && logoUri == null) {
            fallbackCopy(inputPath, outputPath, onComplete)
            return
        }

        // הכנת לוגו לוידאו
        var logoPath: String? = null
        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val file = File(context.cacheDir, "ffmpeg_logo_temp.png")
                val out = FileOutputStream(file)
                // הקטנת לוגו אם הוא ענק למניעת קריסת FFmpeg
                val scaledLogo = if (bitmap.width > 500) Bitmap.createScaledBitmap(bitmap, 500, (500f/bitmap.width*bitmap.height).toInt(), true) else bitmap
                scaledLogo.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush(); out.close()
                logoPath = file.absolutePath
            } catch (e: Exception) {}
        }

        // פקודת FFmpeg
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
            cmd.append("[1:v]scale=trunc(iw*$scaleFactor/2)*2:-2[logo];${stream}[logo]overlay=trunc(W*$x/100/2)*2:trunc(H*$y/100/2)*2")
        } else {
            if (blurRects.isNotEmpty()) cmd.setLength(cmd.length - 1) else cmd.append("null")
        }

        cmd.append("\" -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a copy \"$outputPath\"")

        try {
            FFmpegKit.executeAsync(cmd.toString()) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) onComplete(true)
                else fallbackCopy(inputPath, outputPath, onComplete)
            }
        } catch (e: Exception) { fallbackCopy(inputPath, outputPath, onComplete) }
    }

    // --- מנוע תמונה בטוח מפני קריסות זיכרון ---
    private fun processImageSafe(
        context: Context, inputPath: String, outputPath: String,
        blurRects: List<BlurRect>, logoUri: Uri?,
        relX: Float, relY: Float, relW: Float
    ) {
        // 1. חישוב גודל אופטימלי (Downsampling) כדי לא לפוצץ זיכרון
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(inputPath, options)
        
        // מגבילים לרזולוציה של בערך 2000x2000 (מספיק לטלגרם, חוסך המון זיכרון)
        options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
        options.inJustDecodeBounds = false
        options.inMutable = true
        
        var bitmap = BitmapFactory.decodeFile(inputPath, options) ?: throw Exception("Failed to decode image")

        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // 2. טשטוש
        if (blurRects.isNotEmpty()) {
            val paint = Paint()
            for (rect in blurRects) {
                val left = (rect.left * w).toInt()
                val top = (rect.top * h).toInt()
                val right = (rect.right * w).toInt()
                val bottom = (rect.bottom * h).toInt()
                
                if (right > left && bottom > top) {
                    val roi = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                    val small = Bitmap.createScaledBitmap(roi, Math.max(1, roi.width/15), Math.max(1, roi.height/15), true)
                    val pixelated = Bitmap.createScaledBitmap(small, roi.width, roi.height, false)
                    canvas.drawBitmap(pixelated, left.toFloat(), top.toFloat(), paint)
                    roi.recycle(); small.recycle(); pixelated.recycle()
                }
            }
        }

        // 3. לוגו
        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val logoBmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (logoBmp != null) {
                    val finalLogoW = (w * relW).toInt()
                    val ratio = logoBmp.height.toFloat() / logoBmp.width.toFloat()
                    val finalLogoH = (finalLogoW * ratio).toInt()
                    val scaledLogo = Bitmap.createScaledBitmap(logoBmp, finalLogoW, finalLogoH, true)
                    
                    canvas.drawBitmap(scaledLogo, w * relX, h * relY, null)
                    if (scaledLogo != logoBmp) scaledLogo.recycle()
                    logoBmp.recycle()
                }
            } catch (e: Exception) { Log.e("MediaProcessor", "Logo draw error", e) }
        }

        // 4. שמירה
        val outStream = FileOutputStream(outputPath)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outStream) // איכות 85% חוסכת זיכרון ומהירה יותר
        outStream.flush()
        outStream.close()
        bitmap.recycle()
    }

    private fun fallbackCopy(input: String, output: String, callback: (Boolean) -> Unit) {
        try {
            File(input).copyTo(File(output), overwrite = true)
            callback(true)
        } catch (e: Exception) { callback(false) }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
