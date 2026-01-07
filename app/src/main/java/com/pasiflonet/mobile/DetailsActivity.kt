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
import com.pasiflonet.mobile.utils.DataStoreRepo
import com.pasiflonet.mobile.utils.MediaProcessor
import com.pasiflonet.mobile.utils.TranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

            // 1. קודם כל מציגים מיני-טאבנייל (שיהיה משהו בעיניים)
            if (miniThumb != null && miniThumb.isNotEmpty()) {
                b.ivPreview.load(miniThumb)
                b.previewContainer.visibility = View.VISIBLE
            }

            // 2. בדיקה האם יש לנו כבר קובץ איכותי ביד
            if (!thumbPath.isNullOrEmpty() && File(thumbPath!!).exists() && File(thumbPath!!).length() > 0) {
                // יש קובץ! טוענים אותו מיד
                loadSharpImage(thumbPath!!)
            } else {
                // אין קובץ (או שהנתיב ריק).
                // אם יש לנו מזהה (ID) של תמונה, נתחיל לצוד אותה מטלגרם
                if (thumbId != 0) {
                    pollForRealFile(thumbId)
                } else if (fileId != 0) {
                    // אם אין טאבנייל נפרד, ננסה להוריד את הקובץ הראשי
                    pollForRealFile(fileId)
                }
            }
            
            setupTools()
            setupMediaToggle()

        } catch (e: Exception) { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    // פונקציה לטעינת התמונה ללא Cache (כדי לוודא שזה מתעדכן)
    private fun loadSharpImage(path: String) {
        b.ivPreview.load(File(path)) {
            memoryCachePolicy(CachePolicy.DISABLED)
            diskCachePolicy(CachePolicy.DISABLED)
            crossfade(true)
        }
        b.previewContainer.visibility = View.VISIBLE
    }

    // התיקון הגדול: לולאה ששואלת את טלגרם "מה הנתיב החדש?"
    private fun pollForRealFile(id: Int) {
        lifecycleScope.launch {
            var attempts = 0
            while (attempts < 60) { // מנסים למשך 30 שניות
                // 1. מבקשים מטלגרם להוריד
                TdLibManager.downloadFile(id)
                
                // 2. שואלים: "האם זה ירד? ואם כן, איפה זה?"
                val realPath = TdLibManager.getFilePath(id)
                
                if (realPath != null && File(realPath).exists()) {
                    // יש! קיבלנו נתיב אמיתי וקיים
                    loadSharpImage(realPath)
                    break
                }
                
                delay(500) // מחכים חצי שנייה ושואלים שוב
                attempts++
            }
        }
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

    private fun disableVisualTools() { b.btnModeBlur.isEnabled = false; b.btnModeLogo.isEnabled = false; b.sbLogoSize.isEnabled = false }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f }
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                try {
                    val uriStr = DataStoreRepo(this@DetailsActivity).logoUri.first()
                    b.ivDraggableLogo.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 1.0f
                    if (uriStr != null) { b.ivDraggableLogo.load(Uri.parse(uriStr)); b.ivDraggableLogo.clearColorFilter() } 
                    else { b.ivDraggableLogo.load(android.R.drawable.ic_menu_gallery); b.ivDraggableLogo.setColorFilter(Color.WHITE) }
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
            
            // שימוש בנתיב המעודכן ביותר שיש לנו
            var finalPath = thumbPath
            // אם הצלחנו להשיג נתיב חדש בלולאה, הוא כרגע ב-ImageView אבל לא במשתנה.
            // ליתר ביטחון ננסה להשיג את הנתיב המלא של הקובץ המקורי (fileId)
            val realMainPath = TdLibManager.getFilePath(fileId)
            if (realMainPath != null) finalPath = realMainPath
            
            TdLibManager.processAndSendInBackground(
                fileId = fileId, thumbPath = finalPath ?: "", isVideo = isVideo,
                caption = b.etCaption.text.toString(), targetUsername = target,
                rects = b.drawingView.rects.toList(), logoUri = logoUri, lX = lX, lY = lY, lScale = logoScale
            )
            Toast.makeText(this@DetailsActivity, "Media Sending...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
