package com.pasiflonet.mobile

import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewTreeObserver
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
import com.pasiflonet.mobile.utils.ViewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
    
    private var imageBounds = RectF()
    
    // משתנים לשמירת מיקום יחסי של הלוגו
    private var savedLogoRelX = 0.5f
    private var savedLogoRelY = 0.5f
    private var savedLogoScale = 1.0f
    
    private var dX = 0f
    private var dY = 0f

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
            b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")

            if (miniThumb != null) b.ivPreview.load(miniThumb)

            val targetId = if (thumbId != 0) thumbId else fileId
            if (targetId != 0) startImageHunter(targetId)
            else if (thumbPath != null) loadSharpImage(thumbPath!!)
            
            b.ivPreview.viewTreeObserver.addOnGlobalLayoutListener {
                updateImageBounds()
                if (b.ivDraggableLogo.visibility == android.view.View.VISIBLE) {
                    restoreLogoPosition()
                }
            }
            
            setupTools()
            setupMediaToggle()

        } catch (e: Exception) { Toast.makeText(this, "Init Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun updateImageBounds() {
        imageBounds = ViewUtils.getBitmapPositionInsideImageView(b.ivPreview)
        if (imageBounds.width() <= 0 && b.ivPreview.width > 0) {
            imageBounds.set(0f, 0f, b.ivPreview.width.toFloat(), b.ivPreview.height.toFloat())
        }
        b.drawingView.setValidBounds(imageBounds)
    }
    
    private fun restoreLogoPosition() {
        if (imageBounds.width() > 0) {
            val newX = imageBounds.left + (savedLogoRelX * imageBounds.width())
            val newY = imageBounds.top + (savedLogoRelY * imageBounds.height())
            b.ivDraggableLogo.x = newX
            b.ivDraggableLogo.y = newY
        }
    }

    private fun startImageHunter(fileIdToHunt: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            var attempts = 0
            while (attempts < 120) {
                TdLibManager.downloadFile(fileIdToHunt)
                val realPath = TdLibManager.getFilePath(fileIdToHunt)
                if (realPath != null && File(realPath).exists() && File(realPath).length() > 0) {
                    withContext(Dispatchers.Main) {
                        thumbPath = realPath
                        loadSharpImage(realPath)
                    }
                    break
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
            listener(onSuccess = { _, _ -> b.ivPreview.post { updateImageBounds() } })
        }
    }

    private fun setupMediaToggle() {
        b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked ->
            b.vDisabledOverlay.visibility = if (isChecked) android.view.View.GONE else android.view.View.VISIBLE
            b.tvTextOnlyLabel.visibility = if (isChecked) android.view.View.GONE else android.view.View.VISIBLE
            b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f
            enableMediaTools(isChecked)
        }
    }
    
    private fun enableMediaTools(enable: Boolean) {
        b.btnModeBlur.isEnabled = enable; b.btnModeLogo.isEnabled = enable; b.sbLogoSize.isEnabled = enable
        if (!enable) { b.drawingView.visibility = android.view.View.GONE; b.ivDraggableLogo.visibility = android.view.View.GONE; b.drawingView.isBlurMode = false }
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { 
            b.drawingView.isBlurMode = true; b.drawingView.visibility = android.view.View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f 
            updateImageBounds()
        }
        
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            b.ivDraggableLogo.visibility = android.view.View.VISIBLE; b.ivDraggableLogo.alpha = 1.0f
            
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val uriStr = prefs.getString("logo_uri", null)
            if (uriStr != null) { b.ivDraggableLogo.load(Uri.parse(uriStr)); b.ivDraggableLogo.clearColorFilter() } 
            else { b.ivDraggableLogo.load(android.R.drawable.ic_menu_gallery); b.ivDraggableLogo.setColorFilter(Color.WHITE) }
            
            b.ivDraggableLogo.post {
                updateImageBounds()
                savedLogoRelX = 0.5f - ((b.ivDraggableLogo.width / 2f) / imageBounds.width())
                savedLogoRelY = 0.5f - ((b.ivDraggableLogo.height / 2f) / imageBounds.height())
                restoreLogoPosition()
            }
        }

        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    var newX = event.rawX + dX
                    var newY = event.rawY + dY
                    
                    if (newX < imageBounds.left) newX = imageBounds.left
                    if (newX + view.width > imageBounds.right) newX = imageBounds.right - view.width
                    if (newY < imageBounds.top) newY = imageBounds.top
                    if (newY + view.height > imageBounds.bottom) newY = imageBounds.bottom - view.height
                    
                    view.x = newX
                    view.y = newY
                    
                    if (imageBounds.width() > 0) {
                        savedLogoRelX = (newX - imageBounds.left) / imageBounds.width()
                        savedLogoRelY = (newY - imageBounds.top) / imageBounds.height()
                    }
                }
            }
            true
        }

        b.sbLogoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val scale = 0.5f + (p / 50f) 
                b.ivDraggableLogo.scaleX = scale
                b.ivDraggableLogo.scaleY = scale
                savedLogoScale = scale
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.btnTranslate.setOnClickListener { lifecycleScope.launch { val t = b.etCaption.text.toString(); if (t.isNotEmpty()) b.etCaption.setText(TranslationManager.translateToHebrew(t)) } }
        b.btnSend.setOnClickListener { sendData() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun sendData() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val target = prefs.getString("target_username", "") ?: ""
        if (target.isEmpty()) { Toast.makeText(this, "Set Target!", Toast.LENGTH_SHORT).show(); return }
        
        val caption = b.etCaption.text.toString()
        val includeMedia = b.swIncludeMedia.isChecked
        val finalPath = thumbPath
        
        if (includeMedia && (finalPath == null || !File(finalPath).exists())) { 
            Toast.makeText(this, "Media not ready!", Toast.LENGTH_SHORT).show(); return 
        }

        updateImageBounds()
        val relativeWidth = (b.ivDraggableLogo.width * savedLogoScale) / imageBounds.width()
        
        val rects = b.drawingView.rects
        val logoUriStr = prefs.getString("logo_uri", null)
        val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null

        GlobalScope.launch(Dispatchers.IO) {
            if (!includeMedia) {
                TdLibManager.sendFinalMessage(target, caption, null, false)
            } else {
                val extension = if(isVideo) "mp4" else "jpg"
                val outputPath = File(cacheDir, "bg_proc_${System.currentTimeMillis()}.$extension").absolutePath

                MediaProcessor.processContent(
                    context = applicationContext,
                    inputPath = finalPath!!,
                    outputPath = outputPath,
                    isVideo = isVideo,
                    rects = rects,
                    logoUri = logoUri,
                    lX = savedLogoRelX,
                    lY = savedLogoRelY,
                    lRelWidth = relativeWidth,
                    onComplete = { success ->
                        if (success) {
                            TdLibManager.sendFinalMessage(target, caption, outputPath, isVideo)
                        }
                    }
                )
            }
        }
        
        Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
        finish()
    }
}
