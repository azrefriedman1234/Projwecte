package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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

    private fun fmt(value: Float): String {
        return String.format(Locale.US, "%.4f", value)
    }

    private fun getDimensions(path: String, isVideo: Boolean): Pair<Int, Int> {
        return try {
            if (isVideo) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                retriever.release()
                Pair(w, h)
            } else {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(path, options)
                Pair(options.outWidth, options.outHeight)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
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
        val safeInput = File(context.cacheDir, "input_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}")
        val finalOutputPath = if (!outputPath.endsWith(".mp4") && isVideo) "$outputPath.mp4" else outputPath

        try { File(inputPath).copyTo(safeInput, overwrite = true) } 
        catch (e: Exception) { onComplete(false); return }

        if (rects.isEmpty() && logoUri == null) {
            try { 
                safeInput.copyTo(File(finalOutputPath), overwrite = true)
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

        val (width, height) = getDimensions(safeInput.absolutePath, isVideo)
        val useMath = (width == 0 || height == 0)

        val args = mutableListOf<String>()
        args.add("-y")
        args.add("-i"); args.add(safeInput.absolutePath)
        
        if (logoPath != null) {
            args.add("-i"); args.add(logoPath)
        }

        val filter = StringBuilder()
        var currentStream = "[0:v]"
        
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            val splitName = "split_$i"; val cropName = "crop_$i"; val blurName = "blur_$i"
            
            // חישוב פיקסלים מדויק וזוגי
            var pixelW = 0; var pixelH = 0; var pixelX = 0; var pixelY = 0
            
            if (useMath) {
                 // שיטה ישנה - לא מומלץ, אבל כגיבוי
                 // (לא בשימוש אם getDimensions עובד)
            } else {
                pixelW = (width * (r.right - r.left)).toInt()
                pixelH = (height * (r.bottom - r.top)).toInt()
                pixelX = (width * r.left).toInt()
                pixelY = (height * r.top).toInt()
                
                // התיקון הקריטי ל-500x500: כל מספר חייב להיות זוגי
                if (pixelW % 2 != 0) pixelW--
                if (pixelH % 2 != 0) pixelH--
                if (pixelX % 2 != 0) pixelX--
                if (pixelY % 2 != 0) pixelY--
                
                // הגנה מינימלית
                if (pixelW < 4) pixelW = 4
                if (pixelH < 4) pixelH = 4
            }
            
            val wStr = pixelW.toString()
            val hStr = pixelH.toString()
            val xStr = pixelX.toString()
            val yStr = pixelY.toString()
            
            // שיפור האיכות: הקטנה פי 5 במקום פי 15 (הרבה יותר חד)
            filter.append("$currentStream split=2[$splitName][$cropName];")
            filter.append("[$cropName]crop=$wStr:$hStr:$xStr:$yStr,scale=iw/5:-1,scale=$wStr:$hStr[$blurName];")
            filter.append("[$splitName][$blurName]overlay=$xStr:$yStr$nextStream;")
            currentStream = nextStream
        }

        if (logoPath != null) {
            var targetLogoW = (width * lRelWidth).toInt()
            if (targetLogoW % 2 != 0) targetLogoW--
            if (targetLogoW < 10) targetLogoW = 10 

            filter.append("[1:v]scale=$targetLogoW:-1[logo];")
            val bx = fmt(lX)
            val by = fmt(lY)
            filter.append("$currentStream[logo]overlay=x=(W-w)*$bx:y=(H-h)*$by[v_done]")
        } else {
            filter.append("${currentStream}scale=iw:ih[v_done]")
        }

        args.add("-filter_complex"); args.add(filter.toString())
        args.add("-map"); args.add("[v_done]")
        
        if (isVideo) {
            args.add("-c:v"); args.add("libx264")
            args.add("-preset"); args.add("ultrafast")
            args.add("-crf"); args.add("26") // איכות טובה יותר
            args.add("-pix_fmt"); args.add("yuv420p")
            args.add("-max_muxing_queue_size"); args.add("1024") // מונע קריסה בעומס
            args.add("-c:a"); args.add("copy")
        } else {
            args.add("-q:v"); args.add("2") // איכות תמונה גבוהה (2-3 זה מעולה ב-jpg)
        }
        args.add(finalOutputPath)

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
