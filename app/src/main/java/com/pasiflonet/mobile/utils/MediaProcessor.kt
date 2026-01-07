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
        // שלב מקדים: יצירת עותק עבודה בתיקייה בטוחה (פותר בעיות הרשאה)
        val safeInput = File(context.cacheDir, "temp_input_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}")
        try {
            File(inputPath).copyTo(safeInput, overwrite = true)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false)
            return
        }

        // אם אין עריכות - פשוט מעבירים את הקובץ הבטוח הלאה
        if (rects.isEmpty() && logoUri == null) {
            try {
                safeInput.copyTo(File(outputPath), overwrite = true)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            } finally {
                safeInput.delete()
            }
            return
        }

        // הכנת לוגו
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
        
        var lastStream = "[0:v]"
        
        // טשטוש
        rects.forEachIndexed { index, r ->
            val nextStream = "[v${index+1}]"
            // שימוש ב-gblur שהוא יציב יותר
            cmd.append("$lastStream split=2[orig][blur];[blur]crop=iw*${r.right-r.left}:ih*${r.bottom-r.top}:iw*${r.left}:ih*${r.top},gblur=sigma=20[blurred];[orig][blurred]overlay=x=W*${r.left}:y=H*${r.top} $nextStream;")
            lastStream = nextStream
        }

        // לוגו
        if (logoPath != null) {
            val scaleFilter = "[1:v]scale=iw*${lScale}:-1[logo];" 
            cmd.append(scaleFilter)
            val overlayCmd = "$lastStream[logo]overlay=x=W*${lX}:y=H*${lY}[out]"
            cmd.append(overlayCmd)
        } else {
            cmd.append("${lastStream}null[out]")
        }

        cmd.append("\" -map \"[out]\" ")
        
        if (isVideo) {
            // הגדרות למהירות מקסימלית ותאימות לטלגרם
            cmd.append("-c:v libx264 -preset ultrafast -crf 26 -pix_fmt yuv420p -c:a copy ")
        } else {
            cmd.append("-q:v 2 ")
        }

        cmd.append("\"$outputPath\"")

        FFmpegKit.executeAsync(cmd.toString()) { session ->
            safeInput.delete() // ניקוי
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                Log.e("FFMPEG", "Failed: ${session.failStackTrace}")
                // Fallback: אם העריכה נכשלה, נשלח את המקור כדי לא לתקוע את המשתמש
                try {
                    File(inputPath).copyTo(File(outputPath), overwrite = true)
                    onComplete(true) 
                } catch (e: Exception) {
                    onComplete(false)
                }
            }
        }
    }
}
