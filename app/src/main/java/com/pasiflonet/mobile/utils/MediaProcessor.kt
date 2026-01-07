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
        val safeInput = File(context.cacheDir, "input_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}")
        
        try {
            File(inputPath).copyTo(safeInput, overwrite = true)
        } catch (e: Exception) {
            showToast(context, "❌ Copy Error")
            onComplete(false)
            return
        }

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
                val tempLogo = File(context.cacheDir, "logo.png")
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val outputStream = FileOutputStream(tempLogo)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                logoPath = tempLogo.absolutePath
            } catch (e: Exception) { }
        }

        // --- בניית הפקודה כרשימה (מונע שגיאות גרשיים) ---
        val args = mutableListOf<String>()
        args.add("-y")
        args.add("-i"); args.add(safeInput.absolutePath)
        
        if (logoPath != null) {
            args.add("-i"); args.add(logoPath)
        }

        val filter = StringBuilder()
        var currentStream = "[0:v]"
        
        // הוספת פילטרים של טשטוש
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            val w = "iw*${r.right-r.left}"
            val h = "ih*${r.bottom-r.top}"
            val x = "iw*${r.left}"
            val y = "ih*${r.top}"
            
            filter.append("$currentStream split=2[main][tocrop];[tocrop]crop=$w:$h:$x:$y,boxblur=10:1[blurred];[main][blurred]overlay=$x:$y $nextStream;")
            currentStream = nextStream
        }

        // הוספת לוגו או סיום
        // שים לב: אנחנו משתמשים בשם [v_done] כדי לוודא שזה הקוד החדש
        if (logoPath != null) {
            filter.append("[1:v]scale=iw*${lScale}:-1[logo];")
            filter.append("$currentStream[logo]overlay=x=W*${lX}:y=H*${lY}[v_done]")
        } else {
            filter.append("${currentStream}scale=iw:ih[v_done]")
        }

        args.add("-filter_complex"); args.add(filter.toString())
        args.add("-map"); args.add("[v_done]")
        
        if (isVideo) {
            args.add("-c:v"); args.add("libx264")
            args.add("-preset"); args.add("ultrafast")
            args.add("-crf"); args.add("28")
            args.add("-c:a"); args.add("copy")
        } else {
            args.add("-q:v"); args.add("5")
        }

        args.add(outputPath)

        FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { session ->
            safeInput.delete()
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                val logs = session.allLogsAsString
                val errorMsg = if (logs.length > 200) logs.takeLast(200) else logs
                showToast(context, "❌ Fix Failed!\n$errorMsg")
                onComplete(false)
            }
        }
    }
}
