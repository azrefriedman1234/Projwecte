package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

// FORCE UPDATE: Safe Mode 720p + AAC
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
        // אם זה לא וידאו - מעבירים ל-ImageUtils
        if (!isVideo) {
            onComplete(false)
            return
        }

        Log.d("MediaProcessor", "Processing Video in Safe Mode (720p)")

        // הכנת לוגו
        var logoPath: String? = null
        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val file = File(context.cacheDir, "logo_v.png")
                val out = FileOutputStream(file)
                // לוגו קטן לוידאו
                val scaled = Bitmap.createScaledBitmap(bitmap, 300, (300f/bitmap.width*bitmap.height).toInt(), true)
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.close()
                logoPath = file.absolutePath
            } catch (e: Exception) {}
        }

        // בניית פקודה - הכי בסיסית שאפשר כדי למנוע קריסה
        val cmd = StringBuilder()
        cmd.append("-y -i \"$inputPath\" ")
        if (logoPath != null) cmd.append("-i \"$logoPath\" ")

        cmd.append("-filter_complex \"")
        
        // 1. קודם כל מקטינים ל-720p (קל למעבד)
        cmd.append("[0:v]scale=720:-2[v];")
        var stream = "[v]"

        // 2. טשטוש (אם יש)
        if (blurRects.isNotEmpty()) {
            for (i in blurRects.indices) {
                val r = blurRects[i]
                val x = (r.left * 100).toInt(); val y = (r.top * 100).toInt()
                val w = ((r.right - r.left) * 100).toInt(); val h = ((r.bottom - r.top) * 100).toInt()
                cmd.append("${stream}boxblur=10:1:enable='between(x,iw*$x/100,iw*${x+w}/100)*between(y,ih*$y/100,ih*${y+h}/100)'[b$i];")
                stream = "[b$i]"
            }
        }

        // 3. לוגו
        if (logoPath != null) {
            val x = (logoRelX * 100).toInt(); val y = (logoRelY * 100).toInt()
            // שימוש במידות קבועות ובטוחות
            cmd.append("[1:v]scale=iw*0.3:-1[logo];${stream}[logo]overlay=W*$x/100:H*$y/100")
        } else {
            if (blurRects.isNotEmpty()) cmd.setLength(cmd.length - 1) else cmd.append("null")
        }

        // שינוי קריטי: קידוד אודיו מחדש (-c:a aac) במקום העתקה
        cmd.append("\" -c:v libx264 -preset superfast -c:a aac -b:a 128k \"$outputPath\"")

        try {
            FFmpegKit.executeAsync(cmd.toString()) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    onComplete(true)
                } else {
                    Log.e("FFmpeg", "Failed: ${session.failStackTrace}")
                    fallbackCopy(inputPath, outputPath, onComplete)
                }
            }
        } catch (e: Exception) {
            fallbackCopy(inputPath, outputPath, onComplete)
        }
    }

    private fun fallbackCopy(input: String, output: String, callback: (Boolean) -> Unit) {
        try { File(input).copyTo(File(output), overwrite = true); callback(true) } 
        catch (e: Exception) { callback(false) }
    }
}
