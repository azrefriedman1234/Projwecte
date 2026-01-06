package com.pasiflonet.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.utils.MediaProcessor
import com.pasiflonet.mobile.utils.DataStoreRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailsBinding
    private var currentFilePath: String? = null
    private var isVideo: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentFilePath = intent.getStringExtra("FILE_PATH")
        isVideo = intent.getBooleanExtra("IS_VIDEO", false)

        setupPreview()
        
        binding.btnUndo.setOnClickListener { binding.overlayView.undo() }
        binding.btnClear.setOnClickListener { binding.overlayView.clear() }
        binding.btnSend.setOnClickListener { processAndFinish() }
    }

    private fun setupPreview() {
        currentFilePath?.let {
            val bitmap = BitmapFactory.decodeFile(it)
            binding.previewImage.setImageBitmap(bitmap)
        }
    }

    private fun processAndFinish() {
        lifecycleScope.launch {
            val outPath = File(cacheDir, "processed_${System.currentTimeMillis()}.jpg").absolutePath
            val logoUri = DataStoreRepo(this@DetailsActivity).logoUri.first()?.let { Uri.parse(it) }
            
            try {
                MediaProcessor.processImage(
                    context = this@DetailsActivity,
                    inputPath = currentFilePath!!,
                    outputPath = outPath,
                    blurRects = binding.overlayView.getBlurRects(),
                    logoUri = logoUri
                )
                Toast.makeText(this@DetailsActivity, "Saved to $outPath", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@DetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
