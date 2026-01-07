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
            showToast(context, "❌ File Error: ${e.message}")
            onComplete(false)
            return
        }

        // אם אין עריכה - מעבירים הלאה
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

        // --- הבנייה החדשה והבטוחה (רשימת ארגומנטים) ---
        val args = mutableListOf<String>()
        args.add("-y")
        args.add("-i"); args.add(safeInput.absolutePath)
        
        if (logoPath != null) {
            args.add("-i"); args.add(logoPath)
        }

        // בניית מחרוזת הפילטרים (בלי גרשיים מסביב - המערכת תוסיף לבד)
        val filter = StringBuilder()
        var currentStream = "[0:v]"
        
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            val w = "iw*${r.right-r.left}"
            val h = "ih*${r.bottom-r.top}"
            val x = "iw*${r.left}"
            val y = "ih*${r.top}"
            
            // שרשור פילטרים עם נקודה-פסיק
            filter.append("$currentStream split=2[main][tocrop];[tocrop]crop=$w:$h:$x:$y,boxblur=10:1[blurred];[main][blurred]overlay=$x:$y $nextStream;")
            currentStream = nextStream
        }

        // הוספת לוגו או סיום שרשרת
        if (logoPath != null) {
            filter.append("[1:v]scale=iw*${lScale}:-1[logo];")
            filter.append("$currentStream[logo]overlay=x=W*${lX}:y=H*${lY}[final]")
        } else {
            // שימוש ב-scale כפילטר "דמי" במקום null כדי למנוע בעיות תאימות
            filter.append("${currentStream}scale=iw:ih[final]")
        }

        args.add("-filter_complex"); args.add(filter.toString())
        args.add("-map"); args.add("[final]")
        
        // הגדרות קידוד
        if (isVideo) {
            args.add("-c:v"); args.add("libx264")
            args.add("-preset"); args.add("ultrafast")
            args.add("-crf"); args.add("28")
            args.add("-c:a"); args.add("copy")
        } else {
            args.add("-q:v"); args.add("5")
        }

        args.add(outputPath)

        // שימוש בפקודה הבטוחה: executeWithArgumentsAsync
        FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { session ->
            safeInput.delete()
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                val logs = session.allLogsAsString
                val errorMsg = if (logs.length > 200) logs.takeLast(200) else logs
                
                showToast(context, "❌ Edit Failed!\n$errorMsg")
                Log.e("FFMPEG_FAIL", logs)
                onComplete(false)
            }
        }
    }
}
