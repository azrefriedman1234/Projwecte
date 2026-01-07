package com.pasiflonet.mobile

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.MediaProcessor
import com.pasiflonet.mobile.utils.TranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var thumbPath: String? = null
    private var isVideo = false
    private var fileId = 0
    private var thumbId = 0
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
            thumbId = intent.getIntExtra("THUMB_ID", 0)
            isVideo = intent.getBooleanExtra("IS_VIDEO", false)
            val caption = intent.getStringExtra("CAPTION") ?: ""
            b.etCaption.setText(caption)

            if (miniThumb != null && miniThumb.isNotEmpty()) {
                b.ivPreview.load(miniThumb)
                b.previewContainer.visibility = View.VISIBLE
            }

            val targetId = if (thumbId != 0) thumbId else fileId
            if (targetId != 0) {
                Toast.makeText(this, "⏳ Loading High Quality...", Toast.LENGTH_SHORT).show()
                startImageHunter(targetId)
            } else if (!thumbPath.isNullOrEmpty() && File(thumbPath!!).exists()) {
                loadSharpImage(thumbPath!!)
            }
            
            setupTools()
            setupMediaToggle()

        } catch (e: Exception) { Toast.makeText(this, "Init Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun startImageHunter(fileIdToHunt: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            var attempts = 0
            while (attempts < 120) {
                TdLibManager.downloadFile(fileIdToHunt)
                val realPath = TdLibManager.getFilePath(fileIdToHunt)
                if (realPath != null) {
                    val file = File(realPath)
                    if (file.exists() && file.length() > 0) {
                        withContext(Dispatchers.Main) {
                            thumbPath = realPath
                            loadSharpImage(realPath)
                            Toast.makeText(this@DetailsActivity, "✅ HD Loaded", Toast.LENGTH_SHORT).show()
                        }
                        break
                    }
                }
                delay(500)
                attempts++
            }
        }
    }

    private fun loadSharpImage(path: String) {
        b.ivPreview.load(File(path)) {
            memoryCachePolicy(CachePolicy.DISABLED)
            diskCachePolicy(CachePolicy.DISABLED)
            crossfade(true)
        }
        b.previewContainer.visibility = View.VISIBLE
    }

    private fun setupMediaToggle() {
        b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                b.vDisabledOverlay.visibility = View.GONE; b.tvTextOnlyLabel.visibility = View.GONE; b.mediaToolsContainer.alpha = 1.0f; enableMediaTools(true)
            } else {
                b.vDisabledOverlay.visibility = View.VISIBLE; b.tvTextOnlyLabel.visibility = View.VISIBLE; b.mediaToolsContainer.alpha = 0.3f; enableMediaTools(false)
            }
        }
    }
    
    private fun enableMediaTools(enable: Boolean) {
        b.btnModeBlur.isEnabled = enable; b.btnModeLogo.isEnabled = enable; b.sbLogoSize.isEnabled = enable
        if (!enable) { b.drawingView.visibility = View.GONE; b.ivDraggableLogo.visibility = View.GONE; b.drawingView.isBlurMode = false }
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f }
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            try {
                // שימוש ב-SharedPreferences לטעינת הלוגו
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val uriStr = prefs.getString("logo_uri", null)
                
                b.ivDraggableLogo.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 1.0f
                if (uriStr != null) { b.ivDraggableLogo.load(Uri.parse(uriStr)); b.ivDraggableLogo.clearColorFilter() } 
                else { b.ivDraggableLogo.load(android.R.drawable.ic_menu_gallery); b.ivDraggableLogo.setColorFilter(Color.WHITE) }
            } catch (e: Exception) { }
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
            // שימוש ב-SharedPreferences לקבלת ערוץ היעד
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val target = prefs.getString("target_username", "") ?: ""
            
            if (target.isEmpty()) { Toast.makeText(this@DetailsActivity, "Go to Settings and set Target Channel!", Toast.LENGTH_LONG).show(); return@launch }
            
            val includeMedia = b.swIncludeMedia.isChecked
            if (!includeMedia) {
                TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), null, false)
                Toast.makeText(this@DetailsActivity, "Text Sent!", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val logoUriStr = prefs.getString("logo_uri", null)
            val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
            val lX = if (b.previewContainer.width > 0) b.ivDraggableLogo.x / b.previewContainer.width else 0f
            val lY = if (b.previewContainer.height > 0) b.ivDraggableLogo.y / b.previewContainer.height else 0f
            
            val finalPath = thumbPath
            
            if (finalPath == null || !File(finalPath).exists()) {
                 Toast.makeText(this@DetailsActivity, "❌ Media not ready yet. Please wait a second.", Toast.LENGTH_SHORT).show()
                 return@launch
            }

            TdLibManager.processAndSendInBackground(
                fileId = fileId, thumbPath = finalPath, isVideo = isVideo,
                caption = b.etCaption.text.toString(), targetUsername = target,
                rects = b.drawingView.rects.toList(), logoUri = logoUri, lX = lX, lY = lY, lScale = logoScale
            )
            Toast.makeText(this@DetailsActivity, "Processing & Sending...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
