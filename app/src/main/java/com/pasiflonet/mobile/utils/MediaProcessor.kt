package com.pasiflonet.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.BitmapOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Transformer.Listener
import com.pasiflonet.mobile.ui.BlurRect
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object MediaProcessor {

    fun processImage(
        context: Context,
        inputPath: String,
        outputPath: String,
        blurRects: List<BlurRect>,
        logoUri: Uri?
    ) {
        val original = BitmapFactory.decodeFile(inputPath)
            .copy(Bitmap.Config.ARGB_8888, true)
        
        val canvas = Canvas(original)
        val w = original.width
        val h = original.height

        blurRects.forEach { relative ->
            val left = (relative.rect.left * w).toInt().coerceAtLeast(0)
            val top = (relative.rect.top * h).toInt().coerceAtLeast(0)
            val right = (relative.rect.right * w).toInt().coerceAtMost(w)
            val bottom = (relative.rect.bottom * h).toInt().coerceAtMost(h)
            
            if (right > left && bottom > top) {
                // Simple pixelation blur logic
                val subset = Bitmap.createBitmap(original, left, top, right - left, bottom - top)
                val small = Bitmap.createScaledBitmap(subset, subset.width / 10 + 1, subset.height / 10 + 1, true)
                val blurred = Bitmap.createScaledBitmap(small, subset.width, subset.height, false)
                canvas.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
            }
        }

        if (logoUri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val logo = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (logo != null) {
                    canvas.drawBitmap(logo, 20f, 20f, null) 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        FileOutputStream(File(outputPath)).use { out ->
            original.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) 
    suspend fun processVideo(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        logoUri: Uri?
    ): File = suspendCoroutine { continuation ->
        
        val effects = mutableListOf<Effect>()
        if (logoUri != null) {
             try {
                // Fixed: Explicit type usage for Media3 Overlay
                val overlay = BitmapOverlay.createFromUri(context, logoUri)
                val overlayEffect = OverlayEffect(ImmutableList.of(overlay as TextureOverlay))
                effects.add(overlayEffect)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build()

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = androidx.media3.transformer.EditedMediaItem.Builder(mediaItem)
            .setEffects(androidx.media3.transformer.Effects(
                ImmutableList.of(), 
                ImmutableList.copyOf(effects)
            ))
            .build()

        transformer.addListener(object : Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                continuation.resume(File(outputPath))
            }
            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                continuation.resumeWithException(exception)
            }
        })

        transformer.start(editedMediaItem, outputPath)
    }
}
