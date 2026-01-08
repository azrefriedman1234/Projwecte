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
        
        try { File(inputPath).copyTo(safeInput, overwrite = true) } 
        catch (e: Exception) { onComplete(false); return }

        // אם אין עריכה - פשוט מעתיקים ומודיעים שהכל מוכן
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
            
            var wStr = ""; var hStr = ""; var xStr = ""; var yStr = ""
            if (useMath) {
                 wStr = "trunc(iw*${fmt(r.right-r.left)})"
                 hStr = "trunc(ih*${fmt(r.bottom-r.top)})"
                 xStr = "trunc(iw*${fmt(r.left)})"
                 yStr = "trunc(ih*${fmt(r.top)})"
            } else {
                var pixelW = (width * (r.right - r.left)).toInt(); var pixelH = (height * (r.bottom - r.top)).toInt(); var pixelX = (width * r.left).toInt(); var pixelY = (height * r.top).toInt()
                if (pixelW % 2 != 0) pixelW--; if (pixelH % 2 != 0) pixelH--
                wStr = pixelW.toString(); hStr = pixelH.toString(); xStr = pixelX.toString(); yStr = pixelY.toString()
            }
            
            // Blur by scaling down and up
            filter.append("$currentStream split=2[$splitName][$cropName];")
            filter.append("[$cropName]crop=$wStr:$hStr:$xStr:$yStr,scale=iw/15:-1,scale=$wStr:$hStr[$blurName];")
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
            args.add("-c:v"); args.add("libx264"); args.add("-preset"); args.add("ultrafast"); args.add("-crf"); args.add("28"); args.add("-c:a"); args.add("copy")
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
                onComplete(false)
            }
        }
    }
}
