package com.pasiflonet.mobile.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pasiflonet.mobile.utils.BitmapProcessor
import com.pasiflonet.mobile.utils.Media3Exporter
import com.pasiflonet.mobile.utils.TdRepository
import com.pasiflonet.mobile.utils.WatermarkSettings
import java.io.File
import java.io.FileOutputStream

class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val sourcePath = inputData.getString("sourcePath") ?: return Result.failure()
        val blurRects = listOf<FloatArray>() // Stub: parse from data
        val settings = WatermarkSettings() // Stub: load from DataStore
        val watermarkBitmap: Bitmap? = null // Stub: load from URI
        val targetChatId = inputData.getLong("targetChatId", 0L)
        val isVideo = sourcePath.endsWith(".mp4") // Simple check

        val outputFile = File(applicationContext.cacheDir, "processed_\( {System.currentTimeMillis()} \){if (isVideo) ".mp4" else ".jpg"}")
        val outputPath = outputFile.path

        if (isVideo) {
            Media3Exporter.exportVideoWithWatermark(applicationContext, sourcePath, outputPath, watermarkBitmap, settings)
        } else {
            val bitmap = BitmapFactory.decodeFile(sourcePath)
            val processed = BitmapProcessor.applyBlurAndWatermark(bitmap, blurRects, watermarkBitmap, settings)
            processed.compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(outputFile))
        }

        // שליחה עם TdRepository
        TdRepository.sendFile(targetChatId, outputPath)

        return Result.success()
    }
}
