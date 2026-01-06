package com.pasiflonet.mobile

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import com.pasiflonet.mobile.utils.MediaProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailsBinding
    private var currentFilePath: String? = null
    private var isVideo: Boolean = false
    private var dX = 0f
    private var dY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentFilePath = intent.getStringExtra("FILE_PATH")
        isVideo = intent.getBooleanExtra("IS_VIDEO", false)
        
        setupPreview()
        setupDraggableLogo()
        
        binding.btnSend.setOnClickListener { processAndSend() }
        binding.btnUndo.setOnClickListener { binding.overlayView.undo() }
    }

    private fun setupPreview() {
        currentFilePath?.let { binding.previewImage.setImageBitmap(BitmapFactory.decodeFile(it)) }
    }

    private fun setupDraggableLogo() {
        lifecycleScope.launch {
            val logoUri = DataStoreRepo(this@DetailsActivity).logoUri.first()
            if (logoUri != null) {
                binding.ivDraggableLogo.visibility = View.VISIBLE
                binding.ivDraggableLogo.setImageURI(Uri.parse(logoUri))
                binding.ivDraggableLogo.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY }
                        MotionEvent.ACTION_MOVE -> { view.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start() }
                    }
                    true
                }
            }
        }
    }

    private fun processAndSend() {
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@DetailsActivity)
            val target = repo.targetUsername.first() ?: ""
            if (target.isEmpty()) {
                Toast.makeText(this@DetailsActivity, "Set target in Settings!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val editedText = binding.etMessageText.text.toString()
            val includeMedia = binding.cbIncludeMedia.isChecked
            
            if (includeMedia && currentFilePath != null) {
                val outPath = File(cacheDir, "final_output.jpg").absolutePath
                val logoUri = repo.logoUri.first()?.let { Uri.parse(it) }
                val logoX = binding.ivDraggableLogo.x / binding.previewContainer.width
                val logoY = binding.ivDraggableLogo.y / binding.previewContainer.height

                MediaProcessor.processImage(this@DetailsActivity, currentFilePath!!, outPath, binding.overlayView.getBlurRects(), logoUri, logoX, logoY)
                TdLibManager.sendFinalMessage(target, editedText, outPath, isVideo)
            } else {
                TdLibManager.sendFinalMessage(target, editedText, null, false)
            }
            finish()
        }
    }
}
