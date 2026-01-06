package com.pasiflonet.mobile.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File

@UnstableApi
object Media3Exporter {
    suspend fun exportVideoWithWatermark(context: Context, inputPath: String, outputPath: String, watermarkBitmap: Bitmap?, settings: WatermarkSettings) {
        val mediaItem = MediaItem.fromUri(Uri.fromFile(File(inputPath)))

        val overlays = mutableListOf<BitmapOverlay>()
        if (settings.enabled) {
            watermarkBitmap?.let {
                overlays.add(object : BitmapOverlay() {
                    override fun getBitmap(presentationTimeUs: Long): Bitmap? = it // static watermark
                })
            }
        }

        val effects = if (overlays.isNotEmpty()) listOf(OverlayEffect(ImmutableList.copyOf(overlays))) else emptyList()

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), effects))
            .build()

        val transformer = Transformer.Builder(context).build()
        transformer.start(editedItem, outputPath)
        // Use ProgressListener to wait for completion in worker
    }
}
