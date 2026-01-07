package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import com.pasiflonet.mobile.utils.BlurRect // Import מתוקן
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
        lX: Float, lY: Float, lScale: Float,
        onComplete: (Boolean) -> Unit
    ) {
        if (isVideo) {
            processVideoFFmpeg(context, inputPath, outputPath, blurRects, logoUri, lX, lY, lScale, onComplete)
        } else {
            processImageCanvas(context, inputPath, outputPath, blurRects, logoUri, lX, lY, lScale)
            onComplete(true)
        }
    }

    private fun processVideoFFmpeg(
        ctx: Context, inPath: String, outPath: String,
        rects: List<BlurRect>, logoUri: Uri?,
        lX: Float, lY: Float, lScale: Float,
        onComplete: (Boolean) -> Unit
    ) {
        val inputs = mutableListOf("-y", "-i", inPath)
        var filterComplex = "[0:v]null[v0];" 
        var lastStream = "[v0]"
        
        // הערה: בגרסה זו הפישוט של הטשטוש כדי למנוע קריסה ללא פילטרים מורכבים
        // במקום זה נתמקד בלוגו
        
        if (logoUri != null) {
            val logoFile = File(ctx.cacheDir, "temp_logo.png")
            try {
                ctx.contentResolver.openInputStream(logoUri)?.use { input ->
                    FileOutputStream(logoFile).use { output -> input.copyTo(output) }
                }
                inputs.add("-i")
                inputs.add(logoFile.absolutePath)
                
                val xPos = "(main_w-overlay_w)*$lX"
                val yPos = "(main_h-overlay_h)*$lY"
                // סקייל לוגו
                filterComplex += "[1:v]scale=iw*0.2*$lScale:-1[logo];$lastStream[logo]overlay=$xPos:$yPos"
            } catch (e: Exception) {
                Log.e("MediaProcessor", "Logo Error", e)
            }
        } else {
            filterComplex += "${lastStream}null"
        }

        val cmd = inputs + listOf("-filter_complex", filterComplex, "-c:a", "copy", "-preset", "ultrafast", outPath)
        
        FFmpegKit.executeAsync(cmd.joinToString(" ")) { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                onComplete(true)
            } else {
                Log.e("FFmpeg", "Failed: ${session.failStackTrace}")
                onComplete(false)
            }
        }
    }

    private fun processImageCanvas(ctx: Context, inPath: String, outPath: String, rects: List<BlurRect>, logoUri: Uri?, lX: Float, lY: Float, lScale: Float) {
        val opts = BitmapFactory.Options().apply { inMutable = true }
        val original = BitmapFactory.decodeFile(inPath, opts) ?: return
        val canvas = Canvas(original)
        val w = original.width; val h = original.height

        rects.forEach { r ->
            val left = (r.left * w).toInt(); val top = (r.top * h).toInt(); val right = (r.right * w).toInt(); val bottom = (r.bottom * h).toInt()
            if (right > left && bottom > top) {
                val subset = Bitmap.createBitmap(original, left, top, right - left, bottom - top)
                val pixelated = Bitmap.createScaledBitmap(subset, Math.max(1, subset.width/20), Math.max(1, subset.height/20), false)
                val blurred = Bitmap.createScaledBitmap(pixelated, subset.width, subset.height, true)
                canvas.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
            }
        }

        logoUri?.let { uri ->
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val logo = BitmapFactory.decodeStream(stream)
                    if (logo != null) {
                        val baseW = w * 0.2f * lScale
                        val ratio = logo.height.toFloat() / logo.width.toFloat()
                        val scaled = Bitmap.createScaledBitmap(logo, baseW.toInt(), (baseW * ratio).toInt(), true)
                        canvas.drawBitmap(scaled, lX * w, lY * h, null)
                    }
                }
            } catch (e: Exception) {}
        }
        FileOutputStream(File(outPath)).use { out -> original.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    }
}
