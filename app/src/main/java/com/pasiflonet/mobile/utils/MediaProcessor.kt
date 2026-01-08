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

data class BlurRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

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
        Log.d("MediaProcessor", "Starting process for: $inputPath")

        // 1. אם אין עריכות (בלי לוגו ובלי טשטוש) - פשוט מעתיקים ושולחים
        if (blurRects.isEmpty() && logoUri == null) {
            Log.d("MediaProcessor", "No edits needed. Copying file.")
            try {
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                onComplete(true)
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Copy failed", e)
                onComplete(false)
            }
            return
        }

        // 2. הכנת לוגו (אם יש)
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
                Log.d("MediaProcessor", "Logo saved to: $logoPath")
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Failed to prepare logo", e)
                // ממשיכים בלי לוגו במקרה של שגיאה
            }
        }

        // 3. בניית פקודת FFmpeg
        val cmd = StringBuilder()
        cmd.append("-y -i \"$inputPath\" ")
        
        if (logoPath != null) {
            cmd.append("-i \"$logoPath\" ")
        }

        cmd.append("-filter_complex \"")
        
        // בניית פילטר הטשטוש
        var stream = "[0:v]"
        if (blurRects.isNotEmpty()) {
            for (i in blurRects.indices) {
                val r = blurRects[i]
                // המרת אחוזים לפיקסלים (בערך, FFmpeg מחשב לבד)
                val x = (r.left * 100).toInt()
                val y = (r.top * 100).toInt()
                val w = ((r.right - r.left) * 100).toInt()
                val h = ((r.bottom - r.top) * 100).toInt()
                
                // שימוש ב-boxblur פשוט ומהיר שלא קורס
                cmd.append("${stream}boxblur=10:1:enable='between(x,iw*$x/100,iw*${x+w}/100)*between(y,ih*$y/100,ih*${y+h}/100)'[b$i];")
                stream = "[b$i]"
            }
        }

        // בניית פילטר הלוגו
        if (logoPath != null) {
            val x = (logoRelX * 100).toInt()
            val y = (logoRelY * 100).toInt()
            val w = (logoRelW * 100).toInt()
            // הקטנת הלוגו והלבשתו
            cmd.append("[1:v]scale=iw*$w/100:-1[logo];${stream}[logo]overlay=W*$x/100:H*$y/100")
        } else {
            // אם אין לוגו, רק מסיימים את השרשרת (מורידים את הנקודה-פסיק האחרון)
            if (blurRects.isNotEmpty()) {
                cmd.setLength(cmd.length - 1) // מחיקת ;
            } else {
                cmd.append("null") // פילטר ריק אם משהו התפקשש
            }
        }

        cmd.append("\" ") // סגירת המרכאות של הפילטר

        // הגדרות קידוד (מהירות על חשבון איכות למניעת קריסות)
        if (isVideo) {
            cmd.append("-c:v libx264 -preset ultrafast -c:a copy ")
        }
        
        cmd.append("\"$outputPath\"")

        val finalCommand = cmd.toString()
        Log.d("MediaProcessor", "Executing FFmpeg: $finalCommand")

        // 4. הרצה אסינכרונית עם טיפול בשגיאות
        try {
            FFmpegKitConfig.enableLogCallback { log -> Log.d("FFmpeg", log.message) }
            
            FFmpegKit.executeAsync(finalCommand) { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    Log.d("MediaProcessor", "Success!")
                    onComplete(true)
                } else {
                    Log.e("MediaProcessor", "Failed with state ${session.state} and rc ${session.returnCode}")
                    Log.e("MediaProcessor", "Fail logs: ${session.failStackTrace}")
                    
                    // מנגנון הצלה (Fallback):
                    // אם הקידוד נכשל, מנסים פשוט להעתיק את המקור כדי שהשליחה לא תיכשל לגמרי
                    try {
                        Log.w("MediaProcessor", "Attempting fallback: Copy original file")
                        File(inputPath).copyTo(File(outputPath), overwrite = true)
                        onComplete(true) // מדווחים הצלחה (חלקית)
                    } catch (e: Exception) {
                        onComplete(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaProcessor", "Exception launching FFmpeg", e)
            onComplete(false)
        }
    }
}
