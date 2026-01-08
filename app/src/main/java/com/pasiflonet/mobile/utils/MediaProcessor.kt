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
        lX: Float, lY: Float, lScale: Float,
        onComplete: (Boolean) -> Unit
    ) {
        val safeInput = File(context.cacheDir, "input_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}")
        
        try {
            File(inputPath).copyTo(safeInput, overwrite = true)
        } catch (e: Exception) {
            showToast(context, "❌ Copy Error")
            onComplete(false)
            return
        }

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
        
        // --- בניית הפקודה החדשה ---
        val args = mutableListOf<String>()
        args.add("-y")
        args.add("-i"); args.add(safeInput.absolutePath)
        
        if (logoPath != null) {
            args.add("-i"); args.add(logoPath)
        }

        val filter = StringBuilder()
        var currentStream = "[0:v]" // הזרם הראשי
        
        // שימוש בפילטר 'delogo' - פילטר אחד שעושה את כל העבודה
        rects.forEachIndexed { i, r ->
            val nextStream = "[v$i]"
            
            // חישוב פיקסלים מדויק
            var w = (width * (r.right - r.left)).toInt()
            var h = (height * (r.bottom - r.top)).toInt()
            var x = (width * r.left).toInt()
            var y = (height * r.top).toInt()
            
            // הגנה מפני קריסה: רוחב/גובה חייבים להיות חיוביים
            if (w < 1) w = 1
            if (h < 1) h = 1
            
            // השרשור הפשוט: זרם כניסה -> טשטוש -> זרם יציאה
            // בלי split, בלי crop, בלי overlay
            filter.append("$currentStream delogo=x=$x:y=$y:w=$w:h=$h $nextStream;")
            currentStream = nextStream
        }

        // הוספת לוגו בסוף השרשרת (אם יש)
        if (logoPath != null) {
            // חישוב מידות לוגו
            val s = String.format(Locale.US, "%.4f", lScale)
            val lx = (width * lX).toInt()
            val ly = (height * lY).toInt()

            filter.append("[1:v]scale=trunc(iw*$s):-1[logo];")
            filter.append("$currentStream[logo]overlay=x=$lx:y=$ly[v_done]")
        } else {
            // אם אין לוגו, הזרם האחרון הוא התוצאה
            // (משתמשים בפילטר null כדי רק לשנות שם, למניעת שגיאות)
            filter.append("${currentStream}null[v_done]")
        }

        args.add("-filter_complex"); args.add(filter.toString())
        args.add("-map"); args.add("[v_done]")
        
        if (isVideo) {
            args.add("-c:v"); args.add("libx264")
            args.add("-preset"); args.add("ultrafast")
            args.add("-crf"); args.add("28")
            args.add("-c:a"); args.add("copy")
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
                Log.e("FFMPEG_FAIL", logs)
                onComplete(false)
            }
        }
    }
}
