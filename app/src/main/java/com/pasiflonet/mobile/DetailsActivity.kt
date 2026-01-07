package com.pasiflonet.mobile

import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var thumbPath: String? = null
    private var isVideo = false
    private var fileId = 0
    private var dX = 0f
    private var dY = 0f
    private var logoScale = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            b = ActivityDetailsBinding.inflate(layoutInflater)
            setContentView(b.root)

            // קבלת נתונים בצורה בטוחה
            thumbPath = intent.getStringExtra("THUMB_PATH")
            fileId = intent.getIntExtra("FILE_ID", 0)
            isVideo = intent.getBooleanExtra("IS_VIDEO", false)
            val caption = intent.getStringExtra("CAPTION") ?: ""
            
            b.etCaption.setText(caption)

            // טעינת תמונה בטוחה באמצעות Coil
            if (!thumbPath.isNullOrEmpty() && File(thumbPath!!).exists()) {
                b.ivPreview.load(File(thumbPath!!))
            } else {
                Toast.makeText(this, "Downloading media...", Toast.LENGTH_SHORT).show()
                // אם אין תמונה, ננסה להוריד אותה
                lifecycleScope.launch { TdLibManager.downloadFile(fileId) }
            }

            setupTools()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening details: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // סוגר את המסך במקום לקרוס
        }
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { 
            b.drawingView.isBlurMode = true
            b.drawingView.visibility = View.VISIBLE
            b.ivDraggableLogo.alpha = 0.5f 
            Toast.makeText(this, "Draw on screen to blur", Toast.LENGTH_SHORT).show()
        }
        
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                val uriStr = DataStoreRepo(this@DetailsActivity).logoUri.first()
                if (uriStr != null) { 
                    b.ivDraggableLogo.visibility = View.VISIBLE
                    b.ivDraggableLogo.setImageURI(Uri.parse(uriStr))
                    b.ivDraggableLogo.alpha = 1.0f 
                } else {
                    Toast.makeText(this@DetailsActivity, "Please set a Logo in Settings first!", Toast.LENGTH_LONG).show()
                }
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
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { 
                val s = 0.5f + (p/100f)
                b.ivDraggableLogo.scaleX = s
                b.ivDraggableLogo.scaleY = s
                logoScale = s 
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.btnTranslate.setOnClickListener { 
            lifecycleScope.launch { 
                val t = b.etCaption.text.toString()
                if (t.isNotEmpty()) {
                    b.btnTranslate.text = "Translating..."
                    b.etCaption.setText(TranslationManager.translateToHebrew(t))
                    b.btnTranslate.text = "🌐 Translate to HE"
                }
            } 
        }
        
        b.btnSend.setOnClickListener { sendData() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun sendData() {
        // הגנה מפני קריסה בחישוב מידות
        if (b.previewContainer.width == 0 || b.previewContainer.height == 0) {
            Toast.makeText(this, "Wait for layout to load...", Toast.LENGTH_SHORT).show()
            return
        }

        b.btnSend.text = "Processing..."
        b.btnSend.isEnabled = false
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repo = DataStoreRepo(this@DetailsActivity)
                val target = repo.targetUsername.first() ?: ""
                
                if (target.isEmpty()) {
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(this@DetailsActivity, "No Target Channel defined in Settings!", Toast.LENGTH_LONG).show()
                        b.btnSend.isEnabled = true
                        b.btnSend.text = "Send Now"
                    }
                    return@launch
                }

                // שימוש בנתיב התמונה הממוזערת כברירת מחדל אם המלא לא ירד
                // (בפרודקשן צריך לחכות להורדה מלאה)
                val inputPath = thumbPath ?: ""
                if (inputPath.isEmpty() || !File(inputPath).exists()) {
                     withContext(Dispatchers.Main) { Toast.makeText(this@DetailsActivity, "File not found locally. Try again later.", Toast.LENGTH_SHORT).show() }
                     return@launch
                }

                val outExtension = if (isVideo) "mp4" else "jpg"
                val outPath = File(cacheDir, "processed_${System.currentTimeMillis()}.$outExtension").absolutePath
                val logoUri = repo.logoUri.first()?.let { Uri.parse(it) }
                
                val lX = b.ivDraggableLogo.x / b.previewContainer.width
                val lY = b.ivDraggableLogo.y / b.previewContainer.height

                MediaProcessor.processContent(
                    this@DetailsActivity, inputPath, outPath, isVideo, 
                    b.drawingView.rects, logoUri, lX, lY, logoScale
                ) { success ->
                    if (success) {
                        TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), outPath, isVideo)
                        runOnUiThread { 
                            Toast.makeText(this@DetailsActivity, "Sent successfully!", Toast.LENGTH_SHORT).show()
                            finish() 
                        }
                    } else {
                        runOnUiThread { 
                            Toast.makeText(this@DetailsActivity, "Processing Failed.", Toast.LENGTH_SHORT).show()
                            b.btnSend.isEnabled = true
                            b.btnSend.text = "Try Again"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailsActivity, "Error sending: ${e.message}", Toast.LENGTH_LONG).show()
                    b.btnSend.isEnabled = true
                }
            }
        }
    }
}
