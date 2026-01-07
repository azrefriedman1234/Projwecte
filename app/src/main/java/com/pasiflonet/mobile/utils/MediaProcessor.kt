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
import java.util.Locale

object MediaProcessor {

    private fun showToast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // פונקציית עזר קריטית: הופכת מספרים לפורמט אמריקאי (0.5 ולא 0,5)
    private fun fmt(value: Float): String {
        return String.format(Locale.US, "%.4f", value)
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

        val args = mutableListOf<String>()
        args.add("-y")
        args.add("-i"); args.add(safeInput.absolutePath)
        
        if (logoPath != null) {
            args.add("-i"); args.add(logoPath)
        }

        val filter = StringBuilder()
        var currentStream = "[0:v]"
        
        // תיקון: שימוש ב-fmt() כדי למנוע פסיקים במספרים
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            
            // חישוב המידות בפורמט תקין
            val w = "iw*${fmt(r.right-r.left)}"
            val h = "ih*${fmt(r.bottom-r.top)}"
            val x = "iw*${fmt(r.left)}"
            val y = "ih*${fmt(r.top)}"
            
            filter.append("$currentStream split=2[main][tocrop];[tocrop]crop=$w:$h:$x:$y,boxblur=10:1[blurred];[main][blurred]overlay=$x:$y $nextStream;")
            currentStream = nextStream
        }

        if (logoPath != null) {
            val s = fmt(lScale)
            val lx = fmt(lX)
            val ly = fmt(lY)
            
            filter.append("[1:v]scale=iw*$s:-1[logo];")
            filter.append("$currentStream[logo]overlay=x=W*$lx:y=H*$ly[v_done]")
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
                val errorMsg = if (logs.length > 300) logs.takeLast(300) else logs
                showToast(context, "❌ Fix Failed!\n$errorMsg")
                Log.e("FFMPEG_FAIL", logs)
                onComplete(false)
            }
        }
    }
}
