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
        
        // שלב הטשטוש
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            val splitName = "split_$i"; val cropName = "crop_$i"; val blurName = "blur_$i"
            
            var pixelW = (width * (r.right - r.left)).toInt()
            var pixelH = (height * (r.bottom - r.top)).toInt()
            var pixelX = (width * r.left).toInt()
            var pixelY = (height * r.top).toInt()
            
            if (pixelX + pixelW > width) pixelW = width - pixelX
            if (pixelY + pixelH > height) pixelH = height - pixelY
            
            // הגנה בסיסית
            if (pixelW < 4) pixelW = 4
            if (pixelH < 4) pixelH = 4

            val wStr = pixelW.toString()
            val hStr = pixelH.toString()
            val xStr = pixelX.toString()
            val yStr = pixelY.toString()
            
            // טשטוש עם הגנת זוגיות פנימית
            filter.append("$currentStream split=2[$splitName][$cropName];")
            filter.append("[$cropName]crop=$wStr:$hStr:$xStr:$yStr,scale=trunc(iw/5/2)*2:-2:flags=lanczos,scale=$wStr:$hStr:flags=lanczos[$blurName];")
            filter.append("[$splitName][$blurName]overlay=$xStr:$yStr$nextStream;")
            currentStream = nextStream
        }

        // שלב הלוגו
        if (logoPath != null) {
            var targetLogoW = (width * lRelWidth).toInt()
            if (targetLogoW % 2 != 0) targetLogoW--
            if (targetLogoW < 4) targetLogoW = 4

            val finalX = (width * lX).toInt()
            val finalY = (height * lY).toInt()

            filter.append("[1:v]scale=$targetLogoW:-1:flags=lanczos[logo];")
            filter.append("$currentStream[logo]overlay=x=$finalX:y=$finalY[v_pre_final]")
            currentStream = "[v_pre_final]"
        }

        // שלב סופי קריטי: הגנת זוגיות גלובלית
        // זה מה שיפתור את הבעיה של 500x500 ודומיו
        // אנחנו מכריחים את הוידאו הסופי להיות זוגי ברוחב ובגובה
        if (isVideo) {
            filter.append(";${currentStream}scale=trunc(iw/2)*2:trunc(ih/2)*2[v_done]")
        } else {
            // לתמונות נוסיף חידוד
            filter.append(";${currentStream}unsharp=5:5:1.0:5:5:0.0[v_done]")
        }

        args.add("-filter_complex"); args.add(filter.toString())
        args.add("-map"); args.add("[v_done]")
        
        if (isVideo) {
            args.add("-c:v"); args.add("libx264")
            args.add("-preset"); args.add("superfast") // קצת פחות עומס על המעבד
            args.add("-profile:v"); args.add("baseline") // תאימות מקסימלית
            args.add("-level"); args.add("3.0")
            args.add("-crf"); args.add("20")
            args.add("-pix_fmt"); args.add("yuv420p")
            
            // שינוי קריטי: קידוד אודיו מחדש (מונע התנגשויות העתקה)
            args.add("-c:a"); args.add("aac")
            args.add("-b:a"); args.add("128k")
            args.add("-ac"); args.add("2")
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
                val errorMsg = if (logs.length > 300) logs.takeLast(300) else logs
                showToast(context, "❌ Fix Failed!\n$errorMsg")
                Log.e("FFMPEG_FAIL", logs)
                onComplete(false)
            }
        }
    }
}
