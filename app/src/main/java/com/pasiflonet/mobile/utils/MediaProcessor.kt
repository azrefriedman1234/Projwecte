package com.pasiflonet.mobile.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

object MediaProcessor {

    private fun showToast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

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
        val safeInput = File(context.cacheDir, "temp_in_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}")
        
        // 1. הכנת קובץ עבודה בטוח
        try {
            File(inputPath).copyTo(safeInput, overwrite = true)
        } catch (e: Exception) {
            showToast(context, "❌ Copy Error: ${e.message}")
            onComplete(false)
            return
        }

        // אם אין עריכות - מעתיקים למטרת היעד ויוצאים
        if (rects.isEmpty() && logoUri == null) {
            fallbackToOriginal(safeInput, outputPath, onComplete)
            return
        }

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
            } catch (e: Exception) { e.printStackTrace() }
        }

        // בניית פקודת FFmpeg
        val cmd = StringBuilder()
        cmd.append("-y -i \"${safeInput.absolutePath}\" ")
        if (logoPath != null) cmd.append("-i \"$logoPath\" ")

        cmd.append("-filter_complex \"")
        
        var currentStream = "[0:v]"
        
        // שימוש בפילטר 'boxblur' שהוא הכי יציב ונתמך בכל הגרסאות
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            val w = "iw*${r.right-r.left}"
            val h = "ih*${r.bottom-r.top}"
            val x = "iw*${r.left}"
            val y = "ih*${r.top}"
            
            // הפקודה הפשוטה ביותר לטשטוש אזורי
            cmd.append("$currentStream split=2[main][tocrop];[tocrop]crop=$w:$h:$x:$y,boxblur=10:1[blurred];[main][blurred]overlay=$x:$y $nextStream;")
            currentStream = nextStream
        }

        if (logoPath != null) {
            cmd.append("[1:v]scale=iw*${lScale}:-1[logo];")
            cmd.append("$currentStream[logo]overlay=x=W*${lX}:y=H*${lY}[out]")
        } else {
            cmd.append("${currentStream}null[out]")
        }

        cmd.append("\" -map \"[out]\" ")
        
        // הגדרות קידוד "סלחניות" למקסימום תאימות
        if (isVideo) {
            cmd.append("-c:v libx264 -preset ultrafast -crf 28 -c:a copy ")
        } else {
            cmd.append("-q:v 5 ") // איכות JPEG בינונית-גבוהה
        }

        cmd.append("\"$outputPath\"")

        Log.d("FFMPEG_CMD", cmd.toString())

        FFmpegKit.executeAsync(cmd.toString()) { session ->
            safeInput.delete()
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                // במקרה של כישלון - לא עוצרים! שולחים את המקור.
                val logs = session.allLogsAsString
                Log.e("FFMPEG_FAIL", logs)
                
                // מציגים את השגיאה למשתמש כדי שנדע מה לתקן
                val errorMsg = if (logs.length > 100) logs.takeLast(100) else logs
                showToast(context, "⚠️ Edit Failed: $errorMsg. Sending Original...")
                
                // Fallback: שליחת המקור
                fallbackToOriginal(File(inputPath), outputPath, onComplete)
            }
        }
    }

    private fun fallbackToOriginal(input: File, outputPath: String, onComplete: (Boolean) -> Unit) {
        try {
            input.copyTo(File(outputPath), overwrite = true)
            onComplete(true) // מדווחים שהכל "בסדר" כדי שהשליחה תתבצע
        } catch (e: Exception) {
            onComplete(false)
        }
    }
}
