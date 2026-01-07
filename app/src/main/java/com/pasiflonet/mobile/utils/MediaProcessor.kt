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
        try {
            File(inputPath).copyTo(safeInput, overwrite = true)
        } catch (e: Exception) {
            showToast(context, "❌ Error copying input file")
            onComplete(false)
            return
        }

        // אם אין שום עריכה - מעתיקים ויוצאים
        if (rects.isEmpty() && logoUri == null) {
            try {
                safeInput.copyTo(File(outputPath), overwrite = true)
                onComplete(true)
            } catch (e: Exception) { onComplete(false) }
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

        // בניית פקודה פשוטה ובטוחה יותר
        val cmd = StringBuilder()
        cmd.append("-y -i \"${safeInput.absolutePath}\" ")
        if (logoPath != null) cmd.append("-i \"$logoPath\" ")

        cmd.append("-filter_complex \"")
        
        var currentStream = "[0:v]"
        
        // 1. טשטוש (Blur) - שימוש ב-avgblur או boxblur פשוטים
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            // חישוב קואורדינטות בטוח
            // crop=w:h:x:y
            // x/y הם נקודת ההתחלה, w/h הם הרוחב והגובה
            val w = "iw*${r.right-r.left}"
            val h = "ih*${r.bottom-r.top}"
            val x = "iw*${r.left}"
            val y = "ih*${r.top}"
            
            // פקודה: חתוך את האזור -> טשטש אותו -> הדבק אותו חזרה על המקור
            cmd.append("$currentStream split=2[main][tocrop];[tocrop]crop=$w:$h:$x:$y,avgblur=10[blurred];[main][blurred]overlay=$x:$y $nextStream;")
            currentStream = nextStream
        }

        // 2. לוגו (Overlay)
        if (logoPath != null) {
            // הקטנת הלוגו בהתאם לסקייל
            cmd.append("[1:v]scale=iw*${lScale}:-1[logo];")
            cmd.append("$currentStream[logo]overlay=x=W*${lX}:y=H*${lY}[out]")
        } else {
            cmd.append("${currentStream}null[out]")
        }

        cmd.append("\" -map \"[out]\" ")
        
        if (isVideo) {
            // קידוד מהיר במיוחד לוידאו
            cmd.append("-c:v libx264 -preset ultrafast -crf 28 -c:a copy ")
        } else {
            cmd.append("-q:v 2 ")
        }

        cmd.append("\"$outputPath\"")

        // הרצה
        FFmpegKit.executeAsync(cmd.toString()) { session ->
            safeInput.delete()
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                val logs = session.allLogsAsString
                Log.e("FFMPEG_ERROR", logs)
                showToast(context, "❌ Editing Failed. Check Logs.")
                // הפעם אנחנו לא שולחים את המקור כגיבוי, כדי שתדע שזה נכשל!
                onComplete(false) 
            }
        }
    }
}
