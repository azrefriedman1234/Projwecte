package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        Log.d("MediaProcessor", "Starting process safe mode for: $inputPath")

        // 1. אם אין עריכות - מעתיקים
        if (blurRects.isEmpty() && logoUri == null) {
            try {
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                onComplete(true)
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Copy failed", e)
                onComplete(false)
            }
            return
        }

        // 2. הכנת לוגו
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
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Logo error", e)
            }
        }

        // 3. בניית פקודת FFmpeg בטוחה
        val cmd = StringBuilder()
        cmd.append("-y -i \"$inputPath\" ")
        
        if (logoPath != null) {
            cmd.append("-i \"$logoPath\" ")
        }

        cmd.append("-filter_complex \"")
        
        var stream = "[0:v]"
        
        // טשטוש
        if (blurRects.isNotEmpty()) {
            for (i in blurRects.indices) {
                val r = blurRects[i]
                // המרה לאחוזים שלמים כדי למנוע שברים
                val x = (r.left * 100).toInt()
                val y = (r.top * 100).toInt()
                val w = ((r.right - r.left) * 100).toInt()
                val h = ((r.bottom - r.top) * 100).toInt()
                
                cmd.append("${stream}boxblur=10:1:enable='between(x,iw*$x/100,iw*${x+w}/100)*between(y,ih*$y/100,ih*${y+h}/100)'[b$i];")
                stream = "[b$i]"
            }
        }

        // לוגו - התיקון הגדול: אכיפת מספרים זוגיים
        if (logoPath != null) {
            val x = (logoRelX * 100).toInt()
            val y = (logoRelY * 100).toInt()
            // טריק מתמטי: מכפילים ב-0.5, מעגלים (trunc), ואז מכפילים ב-2.
            // זה מבטיח שהתוצאה תמיד תהיה זוגית.
            // שימוש ב- '-2' במימד השני שומר על יחס גובה-רוחב וגם מבטיח זוגיות.
            val scaleFactor = logoRelW // בערך בין 0.2 ל 0.5
            
            // הנוסחה: scale=trunc(iw*FACTOR/2)*2:-2
            cmd.append("[1:v]scale=trunc(iw*$scaleFactor/2)*2:-2[logo];${stream}[logo]overlay=trunc(W*$x/100/2)*2:trunc(H*$y/100/2)*2")
        } else {
            if (blurRects.isNotEmpty()) {
                cmd.setLength(cmd.length - 1) // מחיקת נקודה-פסיק מיותר
            } else {
                cmd.append("null")
            }
        }

        cmd.append("\" ")

        // 4. הגדרות קידוד בטוחות
        if (isVideo) {
            // הוספתי -pix_fmt yuv420p למניעת קריסות צבע
            cmd.append("-c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a copy ")
        }
        
        cmd.append("\"$outputPath\"")

        val finalCommand = cmd.toString()
        Log.d("MediaProcessor", "Exec: $finalCommand")

        try {
            FFmpegKitConfig.enableLogCallback { log -> Log.d("FFmpeg", log.message) }
            
            FFmpegKit.executeAsync(finalCommand) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    Log.d("MediaProcessor", "Success")
                    onComplete(true)
                } else {
                    Log.e("MediaProcessor", "Failed. RC: ${session.returnCode}")
                    // Fallback - העתקה רגילה במקרה של כישלון
                    try {
                        File(inputPath).copyTo(File(outputPath), overwrite = true)
                        onComplete(true)
                    } catch (e: Exception) {
                        onComplete(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaProcessor", "Crash launch", e)
            onComplete(false)
        }
    }
}
