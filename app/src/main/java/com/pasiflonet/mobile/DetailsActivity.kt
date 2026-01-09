package com.pasiflonet.mobile

import android.content.Context
import android.content.Intent
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
    private var actualMediaPath: String? = null 
    private var isVideo = false
    private var fileId = 0
    private var thumbId = 0
    private var imageBounds = RectF()
    private var savedLogoRelX = 0.5f; private var savedLogoRelY = 0.5f; private var savedLogoScale = 1.0f

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
        b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")

        // טעינת תמונה ממוזערת ראשונית
        intent.getStringExtra("THUMB_PATH")?.let { 
            actualMediaPath = it
            loadSharpImage(it) 
        }

        if (fileId != 0) startFullMediaHunter(fileId)
        setupTools()
    }

    private fun loadSharpImage(path: String) {
        b.ivPreview.load(File(path)) {
            listener(onSuccess = { _, _ ->
                // חייבים לחכות שהתמונה תהיה בתוך ה-ImageView כדי לחשב מיקומים
                b.ivPreview.post { 
                    calculateMatrixBounds()
                    // אם הלוגו כבר היה פתוח, נמקם אותו מחדש
                    if (b.ivDraggableLogo.visibility == View.VISIBLE) restoreLogoPosition()
                }
            })
        }
    }

    private fun loadLogoFromUri(uri: Uri) {
        b.ivDraggableLogo.load(uri) {
            listener(onSuccess = { _, _ ->
                b.ivDraggableLogo.visibility = View.VISIBLE
                b.ivDraggableLogo.bringToFront() // מוודא שהוא מעל התמונה
                restoreLogoPosition()
            })
        }
    }

    private fun setupTools() {
        // כפתור טשטוש
        b.btnModeBlur.setOnClickListener {
            b.drawingView.visibility = View.VISIBLE
            b.drawingView.bringToFront() // מעלה לקדמת השכבות
            b.drawingView.isBlurMode = true
            calculateMatrixBounds()
            safeToast("Blur Mode Active")
        }

        // כפתור לוגו
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
            if (uriStr != null) {
                loadLogoFromUri(Uri.parse(uriStr))
            } else {
                pickLogoLauncher.launch("image/*")
            }
        }

        // גרירת לוגו
        var dX = 0f; var dY = 0f
        b.ivDraggableLogo.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    var newX = event.rawX + dX
                    var newY = event.rawY + dY
                    
                    // הגבלה לגבולות התמונה בלבד
                    if (imageBounds.width() > 0) {
                        newX = newX.coerceIn(imageBounds.left, imageBounds.right - v.width)
                        newY = newY.coerceIn(imageBounds.top, imageBounds.bottom - v.height)
                        savedLogoRelX = (newX - imageBounds.left) / imageBounds.width()
                        savedLogoRelY = (newY - imageBounds.top) / imageBounds.height()
                    }
                    v.x = newX
                    v.y = newY
                }
            }
            true
        }

        b.btnSend.setOnClickListener { performSafeSend() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun calculateMatrixBounds() {
        val drawable = b.ivPreview.drawable ?: return
        val values = FloatArray(9)
        b.ivPreview.imageMatrix.getValues(values)
        
        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        
        val drawW = drawable.intrinsicWidth * scaleX
        val drawH = drawable.intrinsicHeight * scaleY
        
        imageBounds.set(transX, transY, transX + drawW, transY + drawH)
        
        // מעדכן את שכבת הציור איפה מותר לה לצייר
        b.drawingView.setValidBounds(imageBounds)
    }

    private fun restoreLogoPosition() {
        if (imageBounds.width() > 0) {
            b.ivDraggableLogo.x = imageBounds.left + (savedLogoRelX * imageBounds.width())
            b.ivDraggableLogo.y = imageBounds.top + (savedLogoRelY * imageBounds.height())
        }
    }

    private fun startFullMediaHunter(targetId: Int) {
        TdLibManager.downloadFile(targetId)
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0..60) {
                val path = TdLibManager.getFilePath(targetId)
                if (path != null && File(path).exists() && File(path).length() > 50000) {
                    actualMediaPath = path
                    withContext(Dispatchers.Main) { loadSharpImage(path) }
                    break
                }
                delay(1000)
            }
        }
    }

    private fun performSafeSend() {
        val path = actualMediaPath
        if (path == null || !File(path).exists()) {
            safeToast("Media not ready yet...")
            return
        }

        b.loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outPath = File(cacheDir, "out_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}").absolutePath
                val rects = b.drawingView.rects.map { BlurRect(it.left, it.top, it.right, it.bottom) }
                
                val logoUriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
                val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
                
                val relW = if (imageBounds.width() > 0) b.ivDraggableLogo.width.toFloat() / imageBounds.width() else 0.2f

                val success = if (isVideo) {
                    suspendCoroutine { cont -> 
                        MediaProcessor.processContent(applicationContext, path, outPath, true, rects, logoUri, savedLogoRelX, savedLogoRelY, relW) { cont.resume(it) } 
                    }
                } else {
                    ImageUtils.processImage(applicationContext, path, outPath, rects, logoUri, savedLogoRelX, savedLogoRelY, relW)
                }

                withContext(Dispatchers.Main) {
                    val target = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("target_username", "") ?: ""
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), if (success) outPath else path, isVideo)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    b.loadingOverlay.visibility = View.GONE
                    safeToast("Error: ${e.message}") 
                }
            }
        }
    }

    private fun safeToast(msg: String) { runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
    private fun setupMediaToggle() { b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked -> b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f } }
}
