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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        } catch (e: Exception) {
            Toast.makeText(this, "Layout Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }

        try {
            thumbPath = intent.getStringExtra("THUMB_PATH")
            val miniThumbData = intent.getByteArrayExtra("MINI_THUMB") // קבלת ה-Mini Thumb
            
            fileId = intent.getIntExtra("FILE_ID", 0)
            isVideo = intent.getBooleanExtra("IS_VIDEO", false)
            val caption = intent.getStringExtra("CAPTION") ?: ""
            b.etCaption.setText(caption)

            // לוגיקה חכמה לתצוגה:
            // 1. אם יש קובץ פיזי - מציגים אותו (הכי איכותי)
            if (!thumbPath.isNullOrEmpty() && File(thumbPath!!).exists()) {
                b.ivPreview.load(File(thumbPath!!))
                b.previewContainer.visibility = View.VISIBLE
            } 
            // 2. אם אין קובץ, אבל יש Mini Thumb בזיכרון - מציגים אותו (מיידי!)
            else if (miniThumbData != null && miniThumbData.isNotEmpty()) {
                b.ivPreview.load(miniThumbData) // Coil יודע לטעון ByteArray
                b.previewContainer.visibility = View.VISIBLE
            }
            // 3. אם אין כלום - מציגים טעינה
            else if (fileId != 0) {
                 b.previewContainer.visibility = View.VISIBLE
                 Toast.makeText(this, "Loading Preview...", Toast.LENGTH_SHORT).show()
            } else {
                b.previewContainer.visibility = View.INVISIBLE
                disableVisualTools()
            }

            setupTools()
        } catch (e: Exception) {
            Toast.makeText(this, "Logic Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disableVisualTools() {
        b.btnModeBlur.isEnabled = false
        b.btnModeLogo.isEnabled = false
        b.sbLogoSize.isEnabled = false
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { 
            b.drawingView.isBlurMode = true
            b.drawingView.visibility = View.VISIBLE
            b.ivDraggableLogo.alpha = 0.5f 
            Toast.makeText(this, "Blur Mode ON", Toast.LENGTH_SHORT).show()
        }
        
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                val uriStr = DataStoreRepo(this@DetailsActivity).logoUri.first()
                if (uriStr != null) { 
                    b.ivDraggableLogo.visibility = View.VISIBLE
                    b.ivDraggableLogo.setImageURI(Uri.parse(uriStr))
                    b.ivDraggableLogo.alpha = 1.0f 
                } else Toast.makeText(this@DetailsActivity, "Set Logo in Settings!", Toast.LENGTH_SHORT).show()
            }
        }

        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            when (event.action) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX - view.width / 2)
                        .y(event.rawY - view.height / 2)
                        .setDuration(0)
                        .start()
                }
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
                if (t.isNotEmpty()) b.etCaption.setText(TranslationManager.translateToHebrew(t))
            } 
        }
        
        b.btnSend.setOnClickListener { sendData() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun sendData() {
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@DetailsActivity)
            val target = repo.targetUsername.first() ?: ""
            if (target.isEmpty()) { Toast.makeText(this@DetailsActivity, "No Target set in Settings!", Toast.LENGTH_SHORT).show(); return@launch }

            val logoUri = repo.logoUri.first()?.let { Uri.parse(it) }
            val lX = if (b.previewContainer.width > 0) b.ivDraggableLogo.x / b.previewContainer.width else 0f
            val lY = if (b.previewContainer.height > 0) b.ivDraggableLogo.y / b.previewContainer.height else 0f
            
            TdLibManager.processAndSendInBackground(
                fileId = fileId,
                thumbPath = thumbPath ?: "",
                isVideo = isVideo,
                caption = b.etCaption.text.toString(),
                targetUsername = target,
                rects = b.drawingView.rects.toList(),
                logoUri = logoUri,
                lX = lX, lY = lY, lScale = logoScale
            )

            Toast.makeText(this@DetailsActivity, "Sending in background...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
