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
        catch (e: Exception) { showToast(context, "Copy Failed"); onComplete(false); return }

        // מקרה קצה: אין עריכה
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
            
            var pixelW = (width * (r.right - r.left)).toInt()
            var pixelH = (height * (r.bottom - r.top)).toInt()
            var pixelX = (width * r.left).toInt()
            var pixelY = (height * r.top).toInt()
            
            if (pixelX + pixelW > width) pixelW = width - pixelX
            if (pixelY + pixelH > height) pixelH = height - pixelY
            
            if (pixelW < 4) pixelW = 4
            if (pixelH < 4) pixelH = 4

            val wStr = pixelW.toString()
            val hStr = pixelH.toString()
            val xStr = pixelX.toString()
            val yStr = pixelY.toString()
            
            if (filter.isNotEmpty()) filter.append(";")
            
            filter.append("$currentStream split=2[$splitName][$cropName];")
            filter.append("[$cropName]crop=$wStr:$hStr:$xStr:$yStr,scale=trunc(iw/5/2)*2:-2:flags=lanczos,scale=$wStr:$hStr:flags=lanczos[$blurName];")
            filter.append("[$splitName][$blurName]overlay=$xStr:$yStr$nextStream")
            currentStream = nextStream
        }

        // --- שלב הלוגו ---
        if (logoPath != null) {
            var targetLogoW = (width * lRelWidth).toInt()
            if (targetLogoW % 2 != 0) targetLogoW--
            if (targetLogoW < 4) targetLogoW = 4

            val finalX = (width * lX).toInt()
            val finalY = (height * lY).toInt()

            if (filter.isNotEmpty()) filter.append(";")
            
            filter.append("[1:v]scale=$targetLogoW:-1:flags=lanczos[logo];")
            filter.append("$currentStream[logo]overlay=x=$finalX:y=$finalY[v_pre_final]")
            currentStream = "[v_pre_final]"
        }

        // --- שלב סופי (הגנה וחידוד) ---
        if (filter.isNotEmpty()) filter.append(";")
        
        if (isVideo) {
            // הגנת זוגיות לוידאו
            filter.append("${currentStream}scale=trunc(iw/2)*2:trunc(ih/2)*2[v_done]")
        } else {
            // חידוד לתמונות
            filter.append("${currentStream}unsharp=5:5:1.0:5:5:0.0[v_done]")
        }

        args.add("-filter_complex"); args.add(filter.toString())
        
        // מיפוי וידאו
        args.add("-map"); args.add("[v_done]")
        
        if (isVideo) {
            // --- התיקון הגדול לאודיו ---
            // המיפוי 0:a? אומר: קח את האודיו מהקובץ הראשון (0) רק אם הוא קיים (?)
            // אם אין אודיו, FFmpeg פשוט יצור וידאו אילם בלי לקרוס
            args.add("-map"); args.add("0:a?")
            
            args.add("-c:v"); args.add("libx264")
            args.add("-preset"); args.add("ultrafast")
            args.add("-crf"); args.add("23")
            args.add("-pix_fmt"); args.add("yuv420p")
            
            // חזרה להעתקה בטוחה (copy) במקום קידוד מחדש
            args.add("-c:a"); args.add("copy")
            
            // דגלים לתאימות מקסימלית (Telegram Friendly)
            args.add("-movflags"); args.add("+faststart")
        } else {
            args.add("-q:v"); args.add("1")
        }
        args.add(finalOutputPath)

        FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { session ->
            safeInput.delete()
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                val logs = session.allLogsAsString
                val errorMsg = if (logs.length > 200) logs.takeLast(200) else logs
                showToast(context, "❌ Fix Failed!\n$errorMsg")
                Log.e("FFMPEG_FAIL", logs)
                onComplete(false)
            }
        }
    }
}
