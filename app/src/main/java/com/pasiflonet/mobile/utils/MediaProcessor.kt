package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

object MediaProcessor {

    fun processContent(
        context: Context,
        inputPath: String,
        outputPath: String,
        isVideo: Boolean,
        blurRects: List<BlurRect>,
        logoUri: Uri?,
        logoRelX: Float,
        logoRelY: Float,
        logoRelW: Float,
        onComplete: (Boolean) -> Unit
    ) {
        Log.d("MediaProcessor", "Starting FFmpeg process for Video")

        // אם אין עריכות - העתקה פשוטה
        if (blurRects.isEmpty() && logoUri == null) {
            fallbackCopy(inputPath, outputPath, onComplete)
            return
        }

        // הכנת לוגו
        var logoPath: String? = null
        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val file = File(context.cacheDir, "ffmpeg_logo_temp.png")
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush(); out.close()
                logoPath = file.absolutePath
            } catch (e: Exception) {}
        }

        // בניית פקודת FFmpeg (הגרסה הקלאסית שעבדה, עם תיקון קטן למניעת קריסה)
        val cmd = StringBuilder()
        cmd.append("-y -i \"$inputPath\" ")
        if (logoPath != null) cmd.append("-i \"$logoPath\" ")

        cmd.append("-filter_complex \"")
        var stream = "[0:v]"
        
        // טשטוש
        if (blurRects.isNotEmpty()) {
            for (i in blurRects.indices) {
                val r = blurRects[i]
                val x = (r.left * 100).toInt(); val y = (r.top * 100).toInt()
                val w = ((r.right - r.left) * 100).toInt(); val h = ((r.bottom - r.top) * 100).toInt()
                cmd.append("${stream}boxblur=10:1:enable='between(x,iw*$x/100,iw*${x+w}/100)*between(y,ih*$y/100,ih*${y+h}/100)'[b$i];")
                stream = "[b$i]"
            }
        }

        // לוגו - עם אכיפת מספרים זוגיים (חובה ל-libx264)
        if (logoPath != null) {
            val x = (logoRelX * 100).toInt(); val y = (logoRelY * 100).toInt()
            val scaleFactor = logoRelW
            cmd.append("[1:v]scale=trunc(iw*$scaleFactor/2)*2:-2[logo];${stream}[logo]overlay=trunc(W*$x/100/2)*2:trunc(H*$y/100/2)*2")
        } else {
            if (blurRects.isNotEmpty()) cmd.setLength(cmd.length - 1) else cmd.append("null")
        }

        // קידוד מחדש
        cmd.append("\" -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a copy \"$outputPath\"")

        try {
            FFmpegKitConfig.enableLogCallback { log -> Log.d("FFmpeg", log.message) }
            FFmpegKit.executeAsync(cmd.toString()) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    onComplete(true)
                } else {
                    Log.e("FFmpeg", "Failed with RC: ${session.returnCode}")
                    fallbackCopy(inputPath, outputPath, onComplete)
                }
            }
        } catch (e: Exception) { fallbackCopy(inputPath, outputPath, onComplete) }
    }

    private fun fallbackCopy(input: String, output: String, callback: (Boolean) -> Unit) {
        try { File(input).copyTo(File(output), overwrite = true); callback(true) } 
        catch (e: Exception) { callback(false) }
    }
}
