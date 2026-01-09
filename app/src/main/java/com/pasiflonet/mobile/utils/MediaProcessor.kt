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
        // רשת ביטחון ראשונה: עוטפים הכל
        try {
            if (!isVideo) { onComplete(false); return }

            Log.d("MediaProcessor", "Processing Video Safe Mode")

            // הכנת לוגו (אם נכשל - ממשיכים בלי לוגו)
            var logoPath: String? = null
            if (logoUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(logoUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val file = File(context.cacheDir, "v_logo.png")
                    val out = FileOutputStream(file)
                    val scaled = Bitmap.createScaledBitmap(bitmap, 200, (200f/bitmap.width*bitmap.height).toInt(), true)
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.close()
                    logoPath = file.absolutePath
                } catch (e: Exception) { Log.e("MediaProcessor", "Logo skipped: ${e.message}") }
            }

            // בניית פקודה - הכי פשוטה שאפשר
            val cmd = StringBuilder()
            cmd.append("-y -i \"$inputPath\" ")
            if (logoPath != null) cmd.append("-i \"$logoPath\" ")

            cmd.append("-filter_complex \"")
            
            // הקטנה מתונה ל-720p
            cmd.append("[0:v]scale=720:-2[v];")
            var stream = "[v]"

            if (blurRects.isNotEmpty()) {
                for (i in blurRects.indices) {
                    val r = blurRects[i]
                    val x = (r.left * 100).toInt(); val y = (r.top * 100).toInt()
                    val w = ((r.right - r.left) * 100).toInt(); val h = ((r.bottom - r.top) * 100).toInt()
                    cmd.append("${stream}boxblur=10:1:enable='between(x,iw*$x/100,iw*${x+w}/100)*between(y,ih*$y/100,ih*${y+h}/100)'[b$i];")
                    stream = "[b$i]"
                }
            }

            if (logoPath != null) {
                val x = (logoRelX * 100).toInt(); val y = (logoRelY * 100).toInt()
                cmd.append("[1:v]scale=iw*0.2:-1[logo];${stream}[logo]overlay=W*$x/100:H*$y/100")
            } else {
                if (blurRects.isNotEmpty()) cmd.setLength(cmd.length - 1) else cmd.append("null")
            }

            // preset ultrafast = פחות מאמץ למעבד
            cmd.append("\" -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a copy \"$outputPath\"")

            FFmpegKit.executeAsync(cmd.toString()) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    onComplete(true)
                } else {
                    Log.e("FFmpeg", "Failed RC: ${session.returnCode}")
                    onComplete(false) // מודיע שנכשל כדי שישלח מקור
                }
            }

        } catch (e: Exception) {
            Log.e("MediaProcessor", "CRASH PREVENTED: ${e.message}")
            onComplete(false) // במקרה של קריסה פנימית - מודיע שנכשל
        }
    }
}
