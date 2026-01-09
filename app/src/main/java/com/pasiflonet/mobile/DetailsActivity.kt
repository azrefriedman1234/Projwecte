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
import com.pasiflonet.mobile.utils.BlurRect
import kotlinx.coroutines.*
import java.io.File
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
    private var savedLogoRelX = 0.5f; private var savedLogoRelY = 0.5f

    private val pickLogoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("logo_uri", uri.toString()).apply()
            b.ivDraggableLogo.load(uri)
            b.ivDraggableLogo.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)

        // קבלת נתונים מהאינטנט
        isVideo = intent.getBooleanExtra("IS_VIDEO", false)
        fileId = intent.getIntExtra("FILE_ID", 0)
        thumbId = intent.getIntExtra("THUMB_ID", 0)
        val initialThumbPath = intent.getStringExtra("THUMB_PATH")
        b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")

        Log.d("Details", "Started: isVideo=$isVideo, fileId=$fileId, thumbId=$thumbId")

        // 1. טיפול בתצוגה מקדימה (Thumbnail)
        if (initialThumbPath != null && File(initialThumbPath).exists()) {
            b.ivPreview.load(File(initialThumbPath))
        } else if (thumbId != 0) {
            startThumbHunter(thumbId)
        }

        // 2. הורדת המדיה המלאה
        if (fileId != 0) {
            startFullMediaHunter(fileId)
        }

        setupTools()
    }

    private fun startThumbHunter(tId: Int) {
        TdLibManager.downloadFile(tId)
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0..10) {
                val path = TdLibManager.getFilePath(tId)
                if (path != null && File(path).exists()) {
                    withContext(Dispatchers.Main) { b.ivPreview.load(File(path)) }
                    break
                }
                delay(1000)
            }
        }
    }

    private fun startFullMediaHunter(fId: Int) {
        TdLibManager.downloadFile(fId)
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0..60) { // מחכים עד דקה לקובץ מלא
                val path = TdLibManager.getFilePath(fId)
                if (path != null && File(path).exists()) {
                    val file = File(path)
                    // בדיקה שזה לא קובץ זמני קטן מדי
                    if (file.length() > 50000 || !isVideo) {
                        actualMediaPath = path
                        Log.d("Hunter", "Full media ready: $path")
                        if (!isVideo) withContext(Dispatchers.Main) { b.ivPreview.load(file) }
                        break
                    }
                }
                delay(1500)
            }
        }
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.visibility = View.VISIBLE; calculateMatrixBounds() }
        b.btnModeLogo.setOnClickListener {
            val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
            if (uriStr != null) { b.ivDraggableLogo.load(Uri.parse(uriStr)); b.ivDraggableLogo.visibility = View.VISIBLE }
            else { pickLogoLauncher.launch("image/*") }
        }
        b.btnSend.setOnClickListener { performSafeSend() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun performSafeSend() {
        val mediaPath = actualMediaPath
        
        // מניעת קריסה: אם אין נתיב, לא עושים כלום
        if (mediaPath == null || !File(mediaPath).exists()) {
            safeToast("⚠️ Media still loading, please wait...")
            return
        }

        b.loadingOverlay.visibility = View.VISIBLE
        b.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outPath = File(cacheDir, "processed_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}").absolutePath
                val rects = b.drawingView.rects.map { BlurRect(it.left, it.top, it.right, it.bottom) }
                
                val logoUriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
                val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
                
                val relW = if (imageBounds.width() > 0) b.ivDraggableLogo.width / imageBounds.width() else 0.2f

                // עיבוד
                val success = if (isVideo) {
                    suspendCoroutine { cont -> 
                        MediaProcessor.processContent(applicationContext, mediaPath, outPath, true, rects, logoUri, savedLogoRelX, savedLogoRelY, relW) { cont.resume(it) } 
                    }
                } else {
                    ImageUtils.processImage(applicationContext, mediaPath, outPath, rects, logoUri, savedLogoRelX, savedLogoRelY, relW)
                }

                withContext(Dispatchers.Main) {
                    val target = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("target_username", "") ?: ""
                    // שליחה סופית - וידוא נתיב לא נאל
                    val finalPathToSend = if (success && File(outPath).exists()) outPath else mediaPath
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), finalPathToSend, isVideo)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    b.loadingOverlay.visibility = View.GONE
                    b.btnSend.isEnabled = true
                    safeToast("Error during send: ${e.message}") 
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

    private fun safeToast(msg: String) { runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
}
