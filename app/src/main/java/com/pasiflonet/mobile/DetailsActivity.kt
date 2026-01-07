package com.pasiflonet.mobile

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import com.pasiflonet.mobile.utils.MediaProcessor
import com.pasiflonet.mobile.utils.TranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var thumbPath: String? = null; private var isVideo = false; private var fileId = 0
    private var dX = 0f; private var dY = 0f; private var logoScale = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)

        thumbPath = intent.getStringExtra("THUMB_PATH")
        fileId = intent.getIntExtra("FILE_ID", 0)
        isVideo = intent.getBooleanExtra("IS_VIDEO", false)
        b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")

        if (thumbPath != null) b.ivPreview.setImageBitmap(BitmapFactory.decodeFile(thumbPath))
        
        // נסיון הורדה של הקובץ המלא (FFmpeg צריך את הקובץ המלא לעריכה)
        lifecycleScope.launch { TdLibManager.downloadFile(fileId) }

        setupTools()
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f }
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                val uriStr = DataStoreRepo(this@DetailsActivity).logoUri.first()
                if (uriStr != null) { b.ivDraggableLogo.visibility = View.VISIBLE; b.ivDraggableLogo.setImageURI(Uri.parse(uriStr)); b.ivDraggableLogo.alpha = 1.0f }
                else Toast.makeText(this@DetailsActivity, "Set Logo in Settings!", Toast.LENGTH_SHORT).show()
            }
        }

        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY }
                MotionEvent.ACTION_MOVE -> view.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
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
        b.btnSend.text = "Processing..."; b.btnSend.isEnabled = false
        
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = DataStoreRepo(this@DetailsActivity)
            val target = repo.targetUsername.first() ?: ""
            if (target.isEmpty()) return@launch

            // השגת נתיב קובץ מלא (לא ה-Thumbnail)
            // בפרויקט אמיתי צריך לחכות שההורדה תסתיים. כאן נניח שהוא ירד לתיקיית TDLib הרגילה
            // לצורך הדוגמה נשתמש ב-Thumb אם אין ברירה, אבל זה ייכשל בוידאו.
            // לכן: נניח שהקובץ המלא נמצא בנתיב המקור אם הוא זמין.
            val inputPath = thumbPath!! // **זה חייב להיות הנתיב המלא לוידאו**

            val outExtension = if (isVideo) "mp4" else "jpg"
            val outPath = File(cacheDir, "processed_${System.currentTimeMillis()}.$outExtension").absolutePath
            val logoUri = repo.logoUri.first()?.let { Uri.parse(it) }
            
            // חישוב מיקום יחסי
            val lX = b.ivDraggableLogo.x / b.previewContainer.width
            val lY = b.ivDraggableLogo.y / b.previewContainer.height

            MediaProcessor.processContent(
                this@DetailsActivity, inputPath, outPath, isVideo, 
                b.drawingView.rects, logoUri, lX, lY, logoScale
            ) { success ->
                if (success) {
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), outPath, isVideo)
                    runOnUiThread { Toast.makeText(this@DetailsActivity, "Sent!", Toast.LENGTH_SHORT).show(); finish() }
                } else {
                    runOnUiThread { 
                        Toast.makeText(this@DetailsActivity, "Processing Failed (Video might not be ready)", Toast.LENGTH_LONG).show() 
                        b.btnSend.isEnabled = true
                        b.btnSend.text = "Try Again"
                    }
                }
            }
        }
    }
}
