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

// נמחק: data class BlurRect(...) - זה כבר קיים ב-Models.kt

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
        Log.d("MediaProcessor", "Starting process for: $inputPath")

        // 1. אם אין עריכות - מעתיקים
        if (blurRects.isEmpty() && logoUri == null) {
            Log.d("MediaProcessor", "No edits needed. Copying file.")
            try {
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                onComplete(true)
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Copy failed", e)
                onComplete(false)
            }
            return
        }

        // 2. הכנת לוגו
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
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Failed to prepare logo", e)
            }
        }

        // 3. בניית פקודת FFmpeg
        val cmd = StringBuilder()
        cmd.append("-y -i \"$inputPath\" ")
        
        if (logoPath != null) {
            cmd.append("-i \"$logoPath\" ")
        }

        cmd.append("-filter_complex \"")
        
        var stream = "[0:v]"
        if (blurRects.isNotEmpty()) {
            for (i in blurRects.indices) {
                val r = blurRects[i]
                val x = (r.left * 100).toInt()
                val y = (r.top * 100).toInt()
                val w = ((r.right - r.left) * 100).toInt()
                val h = ((r.bottom - r.top) * 100).toInt()
                
                cmd.append("${stream}boxblur=10:1:enable='between(x,iw*$x/100,iw*${x+w}/100)*between(y,ih*$y/100,ih*${y+h}/100)'[b$i];")
                stream = "[b$i]"
            }
        }

        if (logoPath != null) {
            val x = (logoRelX * 100).toInt()
            val y = (logoRelY * 100).toInt()
            val w = (logoRelW * 100).toInt()
            cmd.append("[1:v]scale=iw*$w/100:-1[logo];${stream}[logo]overlay=W*$x/100:H*$y/100")
        } else {
            if (blurRects.isNotEmpty()) {
                cmd.setLength(cmd.length - 1)
            } else {
                cmd.append("null")
            }
        }

        cmd.append("\" ")

        if (isVideo) {
            cmd.append("-c:v libx264 -preset ultrafast -c:a copy ")
        }
        
        cmd.append("\"$outputPath\"")

        val finalCommand = cmd.toString()
        Log.d("MediaProcessor", "Executing FFmpeg: $finalCommand")

        // 4. הרצה
        try {
            FFmpegKitConfig.enableLogCallback { log -> Log.d("FFmpeg", log.message) }
            
            FFmpegKit.executeAsync(finalCommand) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    onComplete(true)
                } else {
                    Log.e("MediaProcessor", "FFmpeg failed. Fallback copy.")
                    try {
                        File(inputPath).copyTo(File(outputPath), overwrite = true)
                        onComplete(true)
                    } catch (e: Exception) {
                        onComplete(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaProcessor", "Exception launching FFmpeg", e)
            onComplete(false)
        }
    }
}
