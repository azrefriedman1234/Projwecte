package com.pasiflonet.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import java.util.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var actualMediaPath: String? = null // הקובץ המלא (וידאו/תמונה)
    private var isVideo = false
    private var fileId = 0
    private var thumbId = 0
    private var imageBounds = RectF()
    private var savedLogoRelX = 0.5f; private var savedLogoRelY = 0.5f; private var savedLogoScale = 1.0f

    private val pickLogoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("logo_uri", uri.toString()).apply()
            b.ivDraggableLogo.load(uri)
            b.ivDraggableLogo.visibility = android.view.View.VISIBLE
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

        // הורדת הקובץ המלא מיד עם הפתיחה
        if (fileId != 0) {
            startFullMediaHunter(fileId)
        } else {
            actualMediaPath = intent.getStringExtra("THUMB_PATH")
        }

        // טעינת תצוגה מקדימה (Thumbnail)
        val initialThumb = intent.getStringExtra("THUMB_PATH")
        initialThumb?.let { b.ivPreview.load(File(it)) }

        setupTools()
    }

    private fun startFullMediaHunter(targetId: Int) {
        TdLibManager.downloadFile(targetId)
        lifecycleScope.launch(Dispatchers.IO) {
            // מחכים לקובץ המלא (בדיקה כל חצי שנייה)
            for (i in 0..30) {
                val path = TdLibManager.getFilePath(targetId)
                if (path != null && File(path).exists()) {
                    val file = File(path)
                    // אם זה וידאו, אנחנו מצפים לקובץ "כבד"
                    if (isVideo && file.length() < 50000) {
                        Log.d("Hunter", "File too small, still waiting...")
                    } else {
                        Log.d("Hunter", "Full media found: $path (Size: ${file.length()})")
                        actualMediaPath = path
                        if (!isVideo) withContext(Dispatchers.Main) { b.ivPreview.load(file) }
                        break
                    }
                }
                delay(1000)
            }
        }
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.visibility = android.view.View.VISIBLE; calculateMatrixBounds() }
        b.btnModeLogo.setOnClickListener {
            val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
            if (uriStr != null) { b.ivDraggableLogo.load(Uri.parse(uriStr)); b.ivDraggableLogo.visibility = android.view.View.VISIBLE }
            else { pickLogoLauncher.launch("image/*") }
        }
        b.btnSend.setOnClickListener { performSafeSend() }
    }

    private fun performSafeSend() {
        val pathToSend = actualMediaPath
        
        // הגנה קריטית: אם הקובץ לא ירד או שהוא קטן מדי (Thumbnail)
        if (pathToSend == null || !File(pathToSend).exists() || (isVideo && File(pathToSend).length() < 50000)) {
            safeToast("Wait! High quality media still downloading...")
            return
        }

        b.loadingOverlay.visibility = android.view.View.VISIBLE
        b.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outPath = File(cacheDir, "final_out.${if(isVideo) "mp4" else "jpg"}").absolutePath
                val rects = b.drawingView.rects.map { BlurRect(it.left, it.top, it.right, it.bottom) }
                
                val logoUriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
                val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
                
                val relW = if (imageBounds.width() > 0) b.ivDraggableLogo.width / imageBounds.width() else 0.2f

                Log.d("Process", "Processing REAL file: $pathToSend")

                val success = if (isVideo) {
                    suspendCoroutine { cont -> 
                        MediaProcessor.processContent(applicationContext, pathToSend, outPath, true, rects, logoUri, savedLogoRelX, savedLogoRelY, relW) { cont.resume(it) } 
                    }
                } else {
                    ImageUtils.processImage(applicationContext, pathToSend, outPath, rects, logoUri, savedLogoRelX, savedLogoRelY, relW)
                }

                withContext(Dispatchers.Main) {
                    val target = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("target_username", "") ?: ""
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), if (success) outPath else pathToSend, isVideo)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    b.loadingOverlay.visibility = android.view.View.GONE; b.btnSend.isEnabled = true
                    safeToast("Error: ${e.message}") 
                }
            }
        }
    }

    private fun calculateMatrixBounds() {
        val d = b.ivPreview.drawable ?: return
        val v = FloatArray(9); b.ivPreview.imageMatrix.getValues(v)
        imageBounds.set(v[2], v[5], v[2] + d.intrinsicWidth * v[0], v[5] + d.intrinsicHeight * v[4])
        b.drawingView.setValidBounds(imageBounds)
    }
    
    private fun restoreLogoPosition() {
        b.ivDraggableLogo.x = imageBounds.left + (savedLogoRelX * imageBounds.width())
        b.ivDraggableLogo.y = imageBounds.top + (savedLogoRelY * imageBounds.height())
    }

    private fun safeToast(msg: String) { runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
    private fun setupMediaToggle() { b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked -> b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f } }
}
