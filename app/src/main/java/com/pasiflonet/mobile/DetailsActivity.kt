package com.pasiflonet.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.MediaProcessor
import com.pasiflonet.mobile.utils.ImageUtils
import com.pasiflonet.mobile.utils.TranslationManager
import com.pasiflonet.mobile.utils.BlurRect
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import android.graphics.drawable.BitmapDrawable
import java.util.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var rawMediaPath: String? = null // הנתיב המקורי (עלול להיות בעייתי)
    private var isVideo = false
    private var fileId = 0
    private var thumbId = 0
    private var imageBounds = RectF()
    private var savedLogoRelX = 0.5f; private var savedLogoRelY = 0.5f

    private val pickLogoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("logo_uri", uri.toString()).apply()
            loadLogoFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)

        isVideo = intent.getBooleanExtra("IS_VIDEO", false)
        fileId = intent.getIntExtra("FILE_ID", 0)
        thumbId = intent.getIntExtra("THUMB_ID", 0)
        
        val passedThumbPath = intent.getStringExtra("THUMB_PATH")
        b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")

        // 1. טעינת תצוגה מקדימה מיידית (אם יש)
        if (passedThumbPath != null && File(passedThumbPath).exists()) {
            loadPreview(passedThumbPath)
        } else if (thumbId != 0) {
            startThumbHunter(thumbId)
        }

        // 2. הורדת הקובץ המלא ברקע
        if (fileId != 0) startFullMediaHunter(fileId)

        setupTools()
    }

    private fun loadPreview(path: String) {
        b.ivPreview.load(File(path)) {
            listener(onSuccess = { _, _ -> 
                b.ivPreview.post { 
                    calculateMatrixBounds() 
                    if (b.ivDraggableLogo.visibility == View.VISIBLE) restoreLogoPosition()
                } 
            })
        }
    }

    private fun startThumbHunter(tId: Int) {
        TdLibManager.downloadFile(tId)
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0..10) {
                val path = TdLibManager.getFilePath(tId)
                if (path != null && File(path).exists()) {
                    withContext(Dispatchers.Main) { loadPreview(path) }
                    break
                }
                delay(500)
            }
        }
    }

    private fun startFullMediaHunter(fId: Int) {
        TdLibManager.downloadFile(fId)
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0..60) {
                val path = TdLibManager.getFilePath(fId)
                if (path != null && File(path).exists()) {
                    val file = File(path)
                    if (file.length() > 50000 || !isVideo) {
                        rawMediaPath = path // שומרים את הנתיב הגולמי
                        if (!isVideo) withContext(Dispatchers.Main) { loadPreview(path) }
                        break
                    }
                }
                delay(1000)
            }
        }
    }

    private fun setupTools() {
        // --- 1. תיקון התרגום: החזרת הכפתור לפעולה ---
        b.btnTranslate.setOnClickListener {
            val originalText = b.etCaption.text.toString()
            if (originalText.isNotEmpty()) {
                safeToast("Translating...")
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val translated = TranslationManager.translateToHebrew(originalText)
                        withContext(Dispatchers.Main) {
                            b.etCaption.setText(translated)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { safeToast("Translation failed") }
                    }
                }
            }
        }

        b.btnModeBlur.setOnClickListener {
            b.drawingView.visibility = View.VISIBLE
            b.drawingView.bringToFront()
            b.drawingView.isBlurMode = true
            b.ivDraggableLogo.alpha = 0.5f
            calculateMatrixBounds()
        }

        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            b.ivDraggableLogo.visibility = View.VISIBLE
            b.ivDraggableLogo.alpha = 1.0f
            b.ivDraggableLogo.bringToFront() 

            val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
            if (uriStr != null) { loadLogoFromUri(Uri.parse(uriStr)) } 
            else { pickLogoLauncher.launch("image/*") }
        }
        
        b.btnModeLogo.setOnLongClickListener { pickLogoLauncher.launch("image/*"); true }

        var dX = 0f; var dY = 0f
        b.ivDraggableLogo.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { dX = v.x - event.rawX; dY = v.y - event.rawY }
                android.view.MotionEvent.ACTION_MOVE -> {
                    var newX = event.rawX + dX; var newY = event.rawY + dY
                    if (imageBounds.width() > 0) {
                        newX = newX.coerceIn(imageBounds.left, imageBounds.right - v.width)
                        newY = newY.coerceIn(imageBounds.top, imageBounds.bottom - v.height)
                        savedLogoRelX = (newX - imageBounds.left) / imageBounds.width()
                        savedLogoRelY = (newY - imageBounds.top) / imageBounds.height()
                    }
                    v.x = newX; v.y = newY
                }
            }
            true
        }

        b.btnSend.setOnClickListener { performStrictSend() }
        b.btnCancel.setOnClickListener { finish() }
    }
    
    private fun loadLogoFromUri(uri: Uri) {
        b.ivDraggableLogo.load(uri) {
            listener(onSuccess = { _, _ ->
                b.ivDraggableLogo.visibility = View.VISIBLE
                b.ivDraggableLogo.bringToFront()
                restoreLogoPosition()
            })
        }
    }

    private fun calculateMatrixBounds() {
        val d = b.ivPreview.drawable ?: return
        val v = FloatArray(9); b.ivPreview.imageMatrix.getValues(v)
        val w = d.intrinsicWidth * v[Matrix.MSCALE_X]; val h = d.intrinsicHeight * v[Matrix.MSCALE_Y]
        imageBounds.set(v[Matrix.MTRANS_X], v[Matrix.MTRANS_Y], v[Matrix.MTRANS_X] + w, v[Matrix.MTRANS_Y] + h)
        b.drawingView.setValidBounds(imageBounds)
    }

    private fun restoreLogoPosition() {
        if (imageBounds.width() > 0) {
            b.ivDraggableLogo.x = imageBounds.left + (savedLogoRelX * imageBounds.width())
            b.ivDraggableLogo.y = imageBounds.top + (savedLogoRelY * imageBounds.height())
        }
    }

    private fun performStrictSend() {
        // שימוש בנתיב הגולמי
        val rawPath = rawMediaPath
        
        if (rawPath == null || !File(rawPath).exists()) {
            safeToast("Wait, downloading video...")
            return
        }

        b.loadingOverlay.visibility = View.VISIBLE
        b.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // --- 2. Sandbox: העתקת הקובץ למקום בטוח (Cache) ---
                // זה מבטיח שהנתיב תקין, ללא תלות ב-Termux או CI
                val safeInputFile = File(cacheDir, "safe_input.${if(isVideo) "mp4" else "jpg"}")
                File(rawPath).copyTo(safeInputFile, overwrite = true)
                val safeInputPath = safeInputFile.absolutePath

                // קובץ הפלט
                val outPath = File(cacheDir, "safe_out_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}").absolutePath
                
                // נתונים לציור
                val rects = b.drawingView.rects.map { BlurRect(it.left, it.top, it.right, it.bottom) }
                var logoUri: Uri? = null
                if (b.ivDraggableLogo.visibility == View.VISIBLE) {
                     try {
                         val d = b.ivDraggableLogo.drawable
                         if (d is BitmapDrawable) { 
                             val f = File(cacheDir, "temp_logo.png"); val o = FileOutputStream(f)
                             d.bitmap.compress(Bitmap.CompressFormat.PNG, 100, o); o.close()
                             logoUri = Uri.fromFile(f) 
                         }
                     } catch(e: Exception) {}
                }
                val relW = if (imageBounds.width() > 0) b.ivDraggableLogo.width.toFloat() / imageBounds.width() else 0.2f

                // --- שליחה למעבד עם הנתיב ה"מולבן" (safeInputPath) ---
                val success = if (isVideo) {
                    try {
                        suspendCoroutine { cont -> 
                            MediaProcessor.processContent(applicationContext, safeInputPath, outPath, true, rects, logoUri, savedLogoRelX, savedLogoRelY, relW) { cont.resume(it) } 
                        }
                    } catch (e: Exception) { false }
                } else {
                    ImageUtils.processImage(applicationContext, safeInputPath, outPath, rects, logoUri, savedLogoRelX, savedLogoRelY, relW)
                }

                withContext(Dispatchers.Main) {
                    // שולחים רק אם הצליח והקובץ קיים
                    if (success && File(outPath).exists() && File(outPath).length() > 0) {
                        val target = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("target_username", "") ?: ""
                        // שולחים את outPath שהוא בוודאות ב-cacheDir ותקין
                        TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), outPath, isVideo)
                        finish()
                    } else {
                        b.loadingOverlay.visibility = View.GONE
                        b.btnSend.isEnabled = true
                        safeToast("❌ Edit Failed. Not sent.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    b.loadingOverlay.visibility = View.GONE
                    b.btnSend.isEnabled = true
                    safeToast("Error: ${e.message}") 
                }
            }
        }
    }

    private fun safeToast(msg: String) { runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
    private fun setupMediaToggle() { b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked -> b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f } }
}
