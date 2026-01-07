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
    private var thumbPath: String? = null
    private var fullPath: String? = null // יתמלא כשיורד
    private var isVideo = false
    private var fileId = 0
    
    // משתנים לגרירת לוגו
    private var dX = 0f
    private var dY = 0f
    private var logoScale = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)

        // קבלת נתונים מהאינטנט
        thumbPath = intent.getStringExtra("THUMB_PATH")
        fileId = intent.getIntExtra("FILE_ID", 0)
        isVideo = intent.getBooleanExtra("IS_VIDEO", false)
        val caption = intent.getStringExtra("CAPTION") ?: ""
        
        b.etCaption.setText(caption)

        // תצוגה מיידית של Thumbnail
        if (thumbPath != null) {
            b.ivPreview.setImageBitmap(BitmapFactory.decodeFile(thumbPath))
        }

        // האזנה להורדה מלאה של הקובץ ברקע
        lifecycleScope.launch {
            TdLibManager.downloadFile(fileId) // וודא הורדה
            TdLibManager.currentMessages.collect { msgs ->
                // לוגיקה למציאת הנתיב המלא כשהוא מוכן...
                // (בפועל נשתמש ב-TdLibManager כדי לקבל נתיב מעודכן)
            }
        }

        setupTools()
    }

    private fun setupTools() {
        // מצב טשטוש
        b.btnModeBlur.setOnClickListener {
            b.drawingView.isBlurMode = true
            b.drawingView.visibility = View.VISIBLE
            b.ivDraggableLogo.alpha = 0.5f // לסמן שהלוגו לא פעיל
            Toast.makeText(this, "Draw rectangles to blur", Toast.LENGTH_SHORT).show()
        }

        // הוספת לוגו
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                val repo = DataStoreRepo(this@DetailsActivity)
                val uriStr = repo.logoUri.first()
                if (uriStr != null) {
                    b.ivDraggableLogo.visibility = View.VISIBLE
                    b.ivDraggableLogo.setImageURI(Uri.parse(uriStr))
                    b.ivDraggableLogo.alpha = 1.0f
                } else {
                    Toast.makeText(this@DetailsActivity, "Set Logo in Settings first!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // גרירת לוגו
        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    view.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
                }
            }
            true
        }

        // שינוי גודל לוגו
        b.sbLogoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val scale = 0.5f + (p / 100f)
                b.ivDraggableLogo.scaleX = scale
                b.ivDraggableLogo.scaleY = scale
                logoScale = scale
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        
        // תרגום
        b.btnTranslate.setOnClickListener {
            lifecycleScope.launch {
                val txt = b.etCaption.text.toString()
                if (txt.isNotEmpty()) {
                    val translated = TranslationManager.translateToHebrew(txt)
                    b.etCaption.setText(translated)
                }
            }
        }

        // שליחה
        b.btnSend.setOnClickListener {
            sendData()
        }
        
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun sendData() {
        b.btnSend.text = "Sending..."
        b.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val repo = DataStoreRepo(this@DetailsActivity)
            val target = repo.targetUsername.first() ?: ""
            
            if (target.isEmpty()) {
                withContext(Dispatchers.Main) { Toast.makeText(this@DetailsActivity, "No Target Channel!", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            
            // בדיקה אם הקובץ המלא ירד כבר (במימוש מלא נבדוק ב-DB של TdLib)
            // לצורך הדוגמא נשתמש ב-Thumb אם אין מלא, או נחכה
            val inputPath = thumbPath!! // זמני - צריך את המלא

            val outPath = File(cacheDir, "sent_${System.currentTimeMillis()}.jpg").absolutePath
            
            // חישוב מיקום יחסי של הלוגו
            val parentW = b.previewContainer.width.toFloat()
            val parentH = b.previewContainer.height.toFloat()
            val logoX = b.ivDraggableLogo.x / parentW
            val logoY = b.ivDraggableLogo.y / parentH
            
            val logoUriStr = repo.logoUri.first()
            val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null

            // עיבוד תמונה (עובד רק על תמונות כרגע לפי הדרישה "בלי FFmpeg")
            if (!isVideo) {
                 MediaProcessor.processImage(
                    this@DetailsActivity, inputPath, outPath, 
                    b.drawingView.rects, logoUri, logoX, logoY, logoScale
                )
                TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), outPath, false)
            } else {
                // לוידאו - שולחים את המקור (ללא עריכה גרפית כי אין FFmpeg) אבל עם הטקסט הערוך
                TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), inputPath, true)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@DetailsActivity, "Sent!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
