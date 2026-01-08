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

    // דיוק גבוה יותר (6 ספרות) למיקום 1:1
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
        // ניקוי קבצים ישנים למניעת התנגשויות
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
            args.add("-i"); args.add(logoPath)
        }

        val filter = StringBuilder()
        var currentStream = "[0:v]"
        
        // --- שלב הטשטוש ---
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            val splitName = "split_$i"; val cropName = "crop_$i"; val blurName = "blur_$i"
            
            // חישובים בסיסיים
            val wRel = r.right - r.left
            val hRel = r.bottom - r.top
            val xRel = r.left
            val yRel = r.top
            
            // הגנה מפני חריגות (FFmpeg קורס אם עוברים את ה-1.0)
            // אבל אנחנו שומרים על דיוק
            
            if (filter.isNotEmpty()) filter.append(";")
            filter.append("$currentStream split=2[$splitName][$cropName];")
            
            // חיתוך מדויק
            val cropCmd = "crop=iw*${fmt(wRel)}:ih*${fmt(hRel)}:iw*${fmt(xRel)}:ih*${fmt(yRel)}"
            
            // שרשרת הטשטוש
            filter.append("[$cropName]$cropCmd,scale=trunc(iw/8/2)*2:-2:flags=lanczos,scale=iw*8:ih*8:flags=neighbor[$blurName];")
            
            // הדבקה עם shortest=0 כדי למנוע חיתוך של הוידאו אם הטשטוש "נגמר" לפני הזמן
            filter.append("[$splitName][$blurName]overlay=x=main_w*${fmt(xRel)}:y=main_h*${fmt(yRel)}:shortest=0$nextStream")
            
            currentStream = nextStream
        }

        // --- שלב הלוגו ---
        if (logoPath != null) {
            val scaleCmd = "scale=iw*${fmt(lRelWidth)}:-1"
            
            if (filter.isNotEmpty()) filter.append(";")
            filter.append("[1:v]$scaleCmd[logo];")
            
            val xCmd = "main_w*${fmt(lX)}"
            val yCmd = "main_h*${fmt(lY)}"
            
            filter.append("$currentStream[logo]overlay=x=$xCmd:y=$yCmd:shortest=0[v_pre_final]")
            currentStream = "[v_pre_final]"
        }

        // --- סיום ---
        if (filter.isNotEmpty()) filter.append(";")
        
        if (isVideo) {
             // הגנת זוגיות בסוף
             filter.append("${currentStream}scale=trunc(iw/2)*2:trunc(ih/2)*2[v_done]")
        } else {
             filter.append("${currentStream}null[v_done]")
        }
        
        args.add("-filter_complex"); args.add(filter.toString())
        args.add("-map"); args.add("[v_done]")
        
        if (isVideo) {
            args.add("-map"); args.add("0:a?") 
            
            // --- התיקון הקריטי לאורך הוידאו ---
            // כפיית קצב פריימים של 30FPS. זה מתקן בעיות סנכרון שגורמות לוידאו להיקטע.
            args.add("-r"); args.add("30")
            
            args.add("-c:v"); args.add("mpeg4")
            args.add("-q:v"); args.add("2") 
            args.add("-pix_fmt"); args.add("yuv420p")
            
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-ac"); args.add("2")
        } else {
            args.add("-c:v"); args.add("png")
        }
        args.add(finalOutputPath)

        showToast(context, "🎬 Processing (High Precision)...")

        FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { session ->
            safeInput.delete()
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                val logs = session.allLogsAsString
                val errorMsg = if (logs.length > 400) logs.takeLast(400) else logs
                showToast(context, "❌ Error: $errorMsg")
                Log.e("FFMPEG_FAIL", logs)
                onComplete(false)
            }
        }
    }
}
