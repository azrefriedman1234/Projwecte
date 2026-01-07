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

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var thumbPath: String? = null
    private var isVideo = false
    private var fileId = 0
    private var logoScale = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            b = ActivityDetailsBinding.inflate(layoutInflater)
            setContentView(b.root)

            thumbPath = intent.getStringExtra("THUMB_PATH")
            fileId = intent.getIntExtra("FILE_ID", 0)
            isVideo = intent.getBooleanExtra("IS_VIDEO", false)
            val caption = intent.getStringExtra("CAPTION") ?: ""
            b.etCaption.setText(caption)

            // טעינת תמונה חכמה:
            if (!thumbPath.isNullOrEmpty() && File(thumbPath!!).exists()) {
                // מקרה 1: התמונה קיימת בדיסק - מציגים מיד
                b.ivPreview.load(File(thumbPath!!))
                b.previewContainer.visibility = View.VISIBLE
            } else if (!thumbPath.isNullOrEmpty()) {
                // מקרה 2: יש נתיב אבל הקובץ עוד לא ירד - מנסים לטעון כל שנייה
                b.previewContainer.visibility = View.VISIBLE
                // משתמשים ב-Coil שינסה לטעון, וגם בלולאה שמחכה לקובץ
                waitForThumbnail(thumbPath!!)
            } else if (fileId != 0) {
                 b.previewContainer.visibility = View.VISIBLE
                 Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
            } else {
                b.previewContainer.visibility = View.INVISIBLE
                disableVisualTools()
            }
            setupTools()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // פונקציה שמחכה לתמונה החדה שתסיים לרדת
    private fun waitForThumbnail(path: String) {
        lifecycleScope.launch {
            var attempts = 0
            while (attempts < 20) { // מנסה במשך 10 שניות (חצי שנייה כל פעם)
                if (File(path).exists()) {
                    b.ivPreview.load(File(path))
                    break
                }
                delay(500)
                attempts++
            }
        }
    }

    private fun disableVisualTools() {
        b.btnModeBlur.isEnabled = false; b.btnModeLogo.isEnabled = false; b.sbLogoSize.isEnabled = false
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f }
        
        // --- תיקון קריסת הלוגו ---
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                try {
                    val uriStr = DataStoreRepo(this@DetailsActivity).logoUri.first()
                    if (uriStr != null) { 
                        b.ivDraggableLogo.visibility = View.VISIBLE
                        // שימוש ב-Coil לטעינה בטוחה! מונע OutOfMemory וקריסות
                        b.ivDraggableLogo.load(Uri.parse(uriStr))
                        b.ivDraggableLogo.alpha = 1.0f 
                    } else Toast.makeText(this@DetailsActivity, "Set Logo in Settings first!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@DetailsActivity, "Logo Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            when (event.action) {
                android.view.MotionEvent.ACTION_MOVE -> { view.animate().x(event.rawX - view.width/2).y(event.rawY - view.height/2).setDuration(0).start() }
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
            val logoUri = repo.logoUri.first()?.let { Uri.parse(it) }
            val lX = if (b.previewContainer.width > 0) b.ivDraggableLogo.x / b.previewContainer.width else 0f
            val lY = if (b.previewContainer.height > 0) b.ivDraggableLogo.y / b.previewContainer.height else 0f
            
            TdLibManager.processAndSendInBackground(
                fileId = fileId, thumbPath = thumbPath ?: "", isVideo = isVideo,
                caption = b.etCaption.text.toString(), targetUsername = target,
                rects = b.drawingView.rects.toList(), logoUri = logoUri, lX = lX, lY = lY, lScale = logoScale
            )
            Toast.makeText(this@DetailsActivity, "Sending...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
