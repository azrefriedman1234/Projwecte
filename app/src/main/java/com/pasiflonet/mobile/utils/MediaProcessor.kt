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
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fmt(f: Float): String {
        return String.format(Locale.US, "%.6f", f)
    }

    fun processContent(
        context: Context,
        inputPath: String,
        outputPath: String,
        isVideo: Boolean,
        rects: List<BlurRect>,
        logoUri: Uri?,
        lX: Float, lY: Float, lRelWidth: Float,
        onComplete: (Boolean) -> Unit
    ) {
        File(outputPath).delete()
        
        val safeInput = File(context.cacheDir, "input_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}")
        val finalOutputPath = if (!outputPath.endsWith(".mp4") && isVideo) "$outputPath.mp4" else outputPath

        try { File(inputPath).copyTo(safeInput, overwrite = true) } 
        catch (e: Exception) { showToast(context, "❌ Copy Failed"); onComplete(false); return }

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
            // --- התיקון הקריטי לוידאו 0 שניות ---
            // חובה לשים -loop 1 לפני ה-Input של הלוגו!
            if (isVideo) {
                args.add("-loop"); args.add("1")
            }
            args.add("-i"); args.add(logoPath)
        }

        val filter = StringBuilder()
        var currentStream = "[0:v]"
        
        // --- טשטוש ---
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            val splitName = "split_$i"; val cropName = "crop_$i"; val blurName = "blur_$i"
            
            val wRel = r.right - r.left
            val hRel = r.bottom - r.top
            val xRel = r.left
            val yRel = r.top
            
            if (filter.isNotEmpty()) filter.append(";")
            filter.append("$currentStream split=2[$splitName][$cropName];")
            
            // חיתוך מדויק
            val cropCmd = "crop=iw*${fmt(wRel)}:ih*${fmt(hRel)}:iw*${fmt(xRel)}:ih*${fmt(yRel)}"
            
            // טשטוש חזק ואיכותי
            filter.append("[$cropName]$cropCmd,scale=trunc(iw/15/2)*2:-2:flags=lanczos,scale=iw*15:ih*15:flags=neighbor[$blurName];")
            
            // overlay עם shortest=1 (חשוב מאוד!)
            // ה-1 אומר: תסיים כשהקובץ הכי קצר (הוידאו המקורי) נגמר
            filter.append("[$splitName][$blurName]overlay=x=main_w*${fmt(xRel)}:y=main_h*${fmt(yRel)}:shortest=1$nextStream")
            
            currentStream = nextStream
        }

        // --- לוגו ---
        if (logoPath != null) {
            val scaleCmd = "scale=iw*${fmt(lRelWidth)}:-1"
            
            if (filter.isNotEmpty()) filter.append(";")
            filter.append("[1:v]$scaleCmd[logo];")
            
            val xCmd = "main_w*${fmt(lX)}"
            val yCmd = "main_h*${fmt(lY)}"
            
            filter.append("$currentStream[logo]overlay=x=$xCmd:y=$yCmd:shortest=1[v_pre_final]")
            currentStream = "[v_pre_final]"
        }

        if (filter.isNotEmpty()) filter.append(";")
        
        if (isVideo) {
             filter.append("${currentStream}scale=trunc(iw/2)*2:trunc(ih/2)*2[v_done]")
        } else {
             filter.append("${currentStream}null[v_done]")
        }
        
        args.add("-filter_complex"); args.add(filter.toString())
        args.add("-map"); args.add("[v_done]")
        
        if (isVideo) {
            args.add("-map"); args.add("0:a?") 
            
            // --- איכות וידאו קיצונית ---
            args.add("-r"); args.add("30")
            args.add("-c:v"); args.add("mpeg4")
            args.add("-b:v"); args.add("15M") // 15 מגה-ביט! איכות מטורפת
            args.add("-maxrate"); args.add("20M")
            args.add("-bufsize"); args.add("30M")
            args.add("-pix_fmt"); args.add("yuv420p")
            
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("256k")
            args.add("-ac"); args.add("2")
        } else {
            // --- איכות תמונה ---
            args.add("-c:v"); args.add("png")
            // דחיסה נמוכה יותר כדי לשמור על פרטים
            args.add("-compression_level"); args.add("3")
        }
        args.add(finalOutputPath)

        showToast(context, "🎬 High-Res Rendering...")

        FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { session ->
            safeInput.delete()
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                val logs = session.allLogsAsString
                val errorMsg = if (logs.length > 300) logs.takeLast(300) else logs
                showToast(context, "❌ Render Fail: $errorMsg")
                Log.e("FFMPEG_FAIL", logs)
                onComplete(false)
            }
        }
    }
}
