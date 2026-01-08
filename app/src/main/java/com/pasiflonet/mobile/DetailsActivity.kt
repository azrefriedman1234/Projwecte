package com.pasiflonet.mobile

import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
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
    
    // גבולות התמונה האמיתיים על המסך
    private var imageBounds = RectF()
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
            
            // האזנה לשינויי גודל מסך (כדי לחשב גבולות מחדש)
            b.ivPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    imageBounds = ViewUtils.getBitmapPositionInsideImageView(b.ivPreview)
                    // עדכון גבולות הציור ל-DrawingView
                    b.drawingView.setValidBounds(imageBounds)
                }
            })
            
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
            listener(onSuccess = { _, _ ->
                // חישוב מחדש כשהתמונה נטענת
                b.ivPreview.post { 
                    imageBounds = ViewUtils.getBitmapPositionInsideImageView(b.ivPreview)
                    b.drawingView.setValidBounds(imageBounds)
                }
            })
        }
    }

    private fun setupMediaToggle() {
        b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked ->
            b.vDisabledOverlay.visibility = if (isChecked) View.GONE else View.VISIBLE
            b.tvTextOnlyLabel.visibility = if (isChecked) View.GONE else View.VISIBLE
            b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f
            enableMediaTools(isChecked)
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
            b.ivDraggableLogo.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 1.0f
            
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val uriStr = prefs.getString("logo_uri", null)
            if (uriStr != null) { b.ivDraggableLogo.load(Uri.parse(uriStr)); b.ivDraggableLogo.clearColorFilter() } 
            else { b.ivDraggableLogo.load(android.R.drawable.ic_menu_gallery); b.ivDraggableLogo.setColorFilter(Color.WHITE) }
            
            // איפוס מיקום הלוגו למרכז התמונה האמיתית
            b.ivDraggableLogo.post {
                b.ivDraggableLogo.x = imageBounds.centerX() - (b.ivDraggableLogo.width / 2)
                b.ivDraggableLogo.y = imageBounds.centerY() - (b.ivDraggableLogo.height / 2)
            }
        }

        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    var newX = event.rawX + dX
                    var newY = event.rawY + dY
                    
                    // הגבלת הגרירה לגבולות התמונה בלבד!
                    if (newX < imageBounds.left) newX = imageBounds.left
                    if (newX + view.width > imageBounds.right) newX = imageBounds.right - view.width
                    if (newY < imageBounds.top) newY = imageBounds.top
                    if (newY + view.height > imageBounds.bottom) newY = imageBounds.bottom - view.height
                    
                    view.animate().x(newX).y(newY).setDuration(0).start()
                }
            }
            true
        }

        b.sbLogoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val scale = 0.5f + (p / 50f) 
                b.ivDraggableLogo.scaleX = scale
                b.ivDraggableLogo.scaleY = scale
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.btnTranslate.setOnClickListener { lifecycleScope.launch { val t = b.etCaption.text.toString(); if (t.isNotEmpty()) b.etCaption.setText(TranslationManager.translateToHebrew(t)) } }
        b.btnSend.setOnClickListener { sendData() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun sendData() {
        lifecycleScope.launch {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val target = prefs.getString("target_username", "") ?: ""
            if (target.isEmpty()) { Toast.makeText(this@DetailsActivity, "Set Target Channel!", Toast.LENGTH_SHORT).show(); return@launch }
            
            val caption = b.etCaption.text.toString()

            if (!b.swIncludeMedia.isChecked) {
                TdLibManager.sendFinalMessage(target, caption, null, false)
                finish()
                return@launch
            }

            val finalPath = thumbPath
            if (finalPath == null || !File(finalPath).exists()) { Toast.makeText(this@DetailsActivity, "Wait for HD...", Toast.LENGTH_SHORT).show(); return@launch }

            // עדכון גבולות לפני שליחה ליתר ביטחון
            imageBounds = ViewUtils.getBitmapPositionInsideImageView(b.ivPreview)
            if (imageBounds.width() <= 0) { Toast.makeText(this@DetailsActivity, "Error: Image bounds invalid", Toast.LENGTH_SHORT).show(); return@launch }

            // --- חישוב מיקום לוגו יחסי לתמונה (לא למסך!) ---
            val logoCenterX = b.ivDraggableLogo.x + (b.ivDraggableLogo.width / 2f)
            val logoCenterY = b.ivDraggableLogo.y + (b.ivDraggableLogo.height / 2f)
            
            // מנרמלים את המיקום ביחס לפינה השמאלית של התמונה האמיתית
            // 0.0 = קצה שמאלי של התמונה, 1.0 = קצה ימני של התמונה
            val lX = (logoCenterX - imageBounds.left) / imageBounds.width()
            val lY = (logoCenterY - imageBounds.top) / imageBounds.height()
            
            val logoVisualWidth = b.ivDraggableLogo.width * b.ivDraggableLogo.scaleX
            val relativeWidth = logoVisualWidth / imageBounds.width()

            val logoUriStr = prefs.getString("logo_uri", null)
            val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
            val outputPath = File(cacheDir, "processed_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}").absolutePath

            Toast.makeText(this@DetailsActivity, "Processing High Quality...", Toast.LENGTH_SHORT).show()

            MediaProcessor.processContent(
                context = this@DetailsActivity,
                inputPath = finalPath,
                outputPath = outputPath,
                isVideo = isVideo,
                rects = b.drawingView.getRectsRelative(imageBounds), // שליחת ריבועים מנורמלים
                logoUri = logoUri,
                lX = lX, lY = lY,
                lRelWidth = relativeWidth,
                onComplete = { success ->
                    if (success) {
                        lifecycleScope.launch {
                            TdLibManager.sendFinalMessage(target, caption, outputPath, isVideo)
                            runOnUiThread { Toast.makeText(this@DetailsActivity, "Sent!", Toast.LENGTH_SHORT).show(); finish() }
                        }
                    }
                }
            )
        }
    }
}
