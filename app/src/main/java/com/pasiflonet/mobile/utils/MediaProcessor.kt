package com.pasiflonet.mobile.utils

import android.content.Context
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
        rects: List<BlurRect>,
        logoUri: Uri?,
        lX: Float, lY: Float, lScale: Float,
        onComplete: (Boolean) -> Unit
    ) {
        // אם אין עריכות בכלל (בלי טשטוש ובלי לוגו), פשוט מעתיקים את הקובץ
        if (rects.isEmpty() && logoUri == null) {
            try {
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
            return
        }

        // הכנת לוגו זמני אם צריך
        var logoPath: String? = null
        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val tempLogo = File(context.cacheDir, "temp_logo.png")
                val outputStream = FileOutputStream(tempLogo)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                logoPath = tempLogo.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // בניית פקודת FFmpeg
        val cmd = StringBuilder()
        cmd.append("-y -i \"$inputPath\" ") // קלט ראשי

        if (logoPath != null) {
            cmd.append("-i \"$logoPath\" ") // קלט לוגו
        }

        cmd.append("-filter_complex \"")
        
        // 1. שלב הטשטוש (Blur)
        var lastStream = "[0:v]" // הזרם הנוכחי שאנחנו עובדים עליו
        
        rects.forEachIndexed { index, rect ->
            // המרה מקואורדינטות יחסיות (0.0-1.0) לפיקסלים
            // x, y, w, h
            val blurCmd = "boxblur=10:1:enable='between(t,0,10000)':enable='between(x,iw*${rect.left},iw*${rect.right})*between(y,ih*${rect.top},ih*${rect.bottom})'"
            // הערה: הדרך היעילה יותר ב-FFmpeg היא להשתמש ב-delogo או crop+blur+overlay
            // לצורך פשטות ויציבות נשתמש ב-delogo שמיועד בדיוק להסרת לוגואים/טשטוש אזורים
            // delogo=x=10:y=10:w=100:h=100
            
            // שיטה יציבה: delogo
            val nextStream = "[v${index+1}]"
            cmd.append("$lastStream delogo=x=iw*${rect.left}:y=ih*${rect.top}:w=iw*${rect.right-rect.left}:h=ih*${rect.bottom-rect.top} $nextStream;")
            lastStream = nextStream
        }

        // 2. שלב הלוגו (Overlay)
        if (logoPath != null) {
            // חישוב גודל הלוגו יחסית למסך (בהנחה שהמסך הוא 1080p לצורך פרופורציה, או שימוש ב-scale2ref)
            // נשתמש ברוחב יחסי: נניח שהלוגו צריך להיות 20% מרוחב המסך כפול הסקייל
            val scaleFilter = "[1:v]scale=iw*${lScale}:-1[logo];" 
            cmd.append(scaleFilter)
            
            val overlayCmd = "$lastStream[logo]overlay=x=W*${lX}:y=H*${lY}[out]"
            cmd.append(overlayCmd)
        } else {
            // אם אין לוגו, הזרם האחרון הוא הפלט
            cmd.append("${lastStream}null[out]")
        }

        cmd.append("\" -map \"[out]\" ")
        
        // הגדרות קידוד (מהירות vs איכות)
        if (isVideo) {
            cmd.append("-c:v libx264 -preset ultrafast -crf 23 -c:a copy ")
        } else {
            cmd.append("-q:v 2 ") // איכות תמונה גבוהה
        }

        cmd.append("\"$outputPath\"")

        Log.d("FFMPEG", "Command: $cmd")

        // הרצה אסינכרונית
        FFmpegKit.executeAsync(cmd.toString()) { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                Log.d("FFMPEG", "Success!")
                onComplete(true)
            } else {
                Log.e("FFMPEG", "Failed: ${session.failStackTrace}")
                onComplete(false)
            }
        }
    }
}
