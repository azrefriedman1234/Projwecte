package com.pasiflonet.mobile

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import com.pasiflonet.mobile.utils.MediaProcessor
import com.pasiflonet.mobile.utils.TranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Color // ייבוא נדרש לצבעים

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var thumbPath: String? = null
    private var isVideo = false
    private var fileId = 0
    private var logoScale = 1.0f
    private var dX = 0f; private var dY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            b = ActivityDetailsBinding.inflate(layoutInflater)
            setContentView(b.root)

            thumbPath = intent.getStringExtra("THUMB_PATH")
            val miniThumb = intent.getByteArrayExtra("MINI_THUMB")
            
            fileId = intent.getIntExtra("FILE_ID", 0)
            isVideo = intent.getBooleanExtra("IS_VIDEO", false)
            val caption = intent.getStringExtra("CAPTION") ?: ""
            b.etCaption.setText(caption)

            if (miniThumb != null && miniThumb.isNotEmpty()) { b.ivPreview.load(miniThumb); b.previewContainer.visibility = View.VISIBLE }
            if (!thumbPath.isNullOrEmpty() && File(thumbPath!!).exists()) { b.ivPreview.load(File(thumbPath!!)); b.previewContainer.visibility = View.VISIBLE }
            else if (!thumbPath.isNullOrEmpty()) { waitForThumbnail(thumbPath!!) }
            
            setupTools()
            setupMediaToggle()

        } catch (e: Exception) { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun setupMediaToggle() {
        b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                b.vDisabledOverlay.visibility = View.GONE
                b.tvTextOnlyLabel.visibility = View.GONE
                b.mediaToolsContainer.alpha = 1.0f
                enableMediaTools(true)
            } else {
                b.vDisabledOverlay.visibility = View.VISIBLE
                b.tvTextOnlyLabel.visibility = View.VISIBLE
                b.mediaToolsContainer.alpha = 0.3f
                enableMediaTools(false)
            }
        }
    }
    
    private fun enableMediaTools(enable: Boolean) {
        b.btnModeBlur.isEnabled = enable
        b.btnModeLogo.isEnabled = enable
        b.sbLogoSize.isEnabled = enable
        if (!enable) {
            b.drawingView.visibility = View.GONE
            b.ivDraggableLogo.visibility = View.GONE
            b.drawingView.isBlurMode = false
        }
    }

    private fun waitForThumbnail(path: String) {
        lifecycleScope.launch {
            var attempts = 0
            while (attempts < 20) { if (File(path).exists()) { b.ivPreview.load(File(path)); break }; delay(500); attempts++ }
        }
    }

    private fun disableVisualTools() { b.btnModeBlur.isEnabled = false; b.btnModeLogo.isEnabled = false; b.sbLogoSize.isEnabled = false }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f }
        
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                try {
                    val uriStr = DataStoreRepo(this@DetailsActivity).logoUri.first()
                    
                    b.ivDraggableLogo.visibility = View.VISIBLE
                    b.ivDraggableLogo.alpha = 1.0f
                    
                    if (uriStr != null) { 
                        b.ivDraggableLogo.load(Uri.parse(uriStr))
                        b.ivDraggableLogo.clearColorFilter() // ביטול צבע אם היה
                    } else {
                        // התיקון: טעינת האייקון וצביעתו בלבן בצורה תקנית
                        b.ivDraggableLogo.load(android.R.drawable.ic_menu_gallery)
                        b.ivDraggableLogo.setColorFilter(Color.WHITE)
                    }
                } catch (e: Exception) { }
            }
        }

        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY }
                android.view.MotionEvent.ACTION_MOVE -> { view.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start() }
            }
            true
        }

        b.sbLogoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { val s = 0.5f + (p/100f); b.ivDraggableLogo.scaleX = s; b.ivDraggableLogo.scaleY = s; logoScale = s }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.btnTranslate.setOnClickListener { lifecycleScope.launch { val t = b.etCaption.text.toString(); if (t.isNotEmpty()) b.etCaption.setText(TranslationManager.translateToHebrew(t)) } }
        b.btnSend.setOnClickListener { sendData() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun sendData() {
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@DetailsActivity)
            val target = repo.targetUsername.first() ?: ""
            if (target.isEmpty()) { Toast.makeText(this@DetailsActivity, "Set Target Channel!", Toast.LENGTH_SHORT).show(); return@launch }
            
            val includeMedia = b.swIncludeMedia.isChecked
            if (!includeMedia) {
                TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), null, false)
                Toast.makeText(this@DetailsActivity, "Text Sent!", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val logoUriStr = repo.logoUri.first()
            val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
            val lX = if (b.previewContainer.width > 0) b.ivDraggableLogo.x / b.previewContainer.width else 0f
            val lY = if (b.previewContainer.height > 0) b.ivDraggableLogo.y / b.previewContainer.height else 0f
            
            TdLibManager.processAndSendInBackground(
                fileId = fileId, thumbPath = thumbPath ?: "", isVideo = isVideo,
                caption = b.etCaption.text.toString(), targetUsername = target,
                rects = b.drawingView.rects.toList(), logoUri = logoUri, lX = lX, lY = lY, lScale = logoScale
            )
            Toast.makeText(this@DetailsActivity, "Media Sending...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
