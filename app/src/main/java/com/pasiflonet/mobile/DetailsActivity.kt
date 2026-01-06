package com.pasiflonet.mobile

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.utils.DataStoreRepo
import com.pasiflonet.mobile.utils.MediaProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailsBinding
    private var filePath: String? = null
    private var isVideo: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filePath = intent.getStringExtra("FILE_PATH")
        isVideo = intent.getBooleanExtra("IS_VIDEO", false) // Fixed: default false

        if (filePath == null) return

        if (!isVideo) {
            binding.previewImage.setImageURI(Uri.fromFile(File(filePath!!)))
        }

        binding.btnUndo.setOnClickListener { binding.overlayView.undo() }
        binding.btnClear.setOnClickListener { binding.overlayView.clear() }
        binding.btnSend.setOnClickListener { processAndSend() }
    }

    private fun processAndSend() = lifecycleScope.launch(Dispatchers.IO) {
        val repo = DataStoreRepo(this@DetailsActivity)
        val logoUriStr = repo.logoUri.first()
        val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
        val outFile = File(cacheDir, "out_${UUID.randomUUID()}.jpg")

        try {
            runOnUiThread { Toast.makeText(this@DetailsActivity, "Processing...", Toast.LENGTH_SHORT).show() }
            
            if (!isVideo) {
                MediaProcessor.processImage(
                    this@DetailsActivity, filePath!!, outFile.absolutePath,
                    binding.overlayView.getRectsRelative(), logoUri
                )
            } else {
                 MediaProcessor.processVideo(
                    this@DetailsActivity, Uri.fromFile(File(filePath!!)), outFile.absolutePath, logoUri
                )
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@DetailsActivity, "Done! Size: ${outFile.length()}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
