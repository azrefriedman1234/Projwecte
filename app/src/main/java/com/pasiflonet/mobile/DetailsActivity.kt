package com.pasiflonet.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.MediaProcessor
import com.pasiflonet.mobile.utils.ImageUtils
import com.pasiflonet.mobile.utils.TranslationManager
import com.pasiflonet.mobile.utils.BlurRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var thumbPath: String? = null
    private var isVideo = false
    private var fileId = 0
    private var thumbId = 0
    private var imageBounds = RectF()
    private var savedLogoRelX = 0.5f; private var savedLogoRelY = 0.5f; private var savedLogoScale = 1.0f

    private val pickLogoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("logo_uri", uri.toString()).apply()
            b.ivDraggableLogo.load(uri)
            b.ivDraggableLogo.visibility = android.view.View.VISIBLE
            b.ivDraggableLogo.post { calculateMatrixBounds(); savedLogoRelX = 0.5f; savedLogoRelY = 0.5f; restoreLogoPosition() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            b = ActivityDetailsBinding.inflate(layoutInflater)
            setContentView(b.root)
            b.ivPreview.scaleType = ImageView.ScaleType.FIT_CENTER
            
            val intentCaption = intent.getStringExtra("CAPTION")
            val intentThumb = intent.getStringExtra("THUMB_PATH")
            
            if (intentThumb != null || intentCaption != null) {
                thumbPath = intentThumb
                fileId = intent.getIntExtra("FILE_ID", 0); thumbId = intent.getIntExtra("THUMB_ID", 0)
                isVideo = intent.getBooleanExtra("IS_VIDEO", false)
                b.etCaption.setText(intentCaption ?: "")
                saveDraft()
            } else { restoreDraft() }
            
            val targetId = if (thumbId != 0) thumbId else fileId
            if (targetId != 0) startHDImageHunter(targetId) else if (thumbPath != null) loadSharpImage(thumbPath!!)
            
            b.ivPreview.viewTreeObserver.addOnGlobalLayoutListener { calculateMatrixBounds(); if (b.ivDraggableLogo.visibility == android.view.View.VISIBLE) restoreLogoPosition() }
            setupTools(); setupMediaToggle()
        } catch (e: Exception) { safeToast("Init Error: ${e.message}") }
    }

    private fun saveDraft() { try { getSharedPreferences("draft_prefs", MODE_PRIVATE).edit().putString("draft_caption", b.etCaption.text.toString()).putString("draft_path", thumbPath).putBoolean("draft_is_video", isVideo).putInt("draft_file_id", fileId).apply() } catch (e: Exception) {} }
    private fun restoreDraft() { val prefs = getSharedPreferences("draft_prefs", MODE_PRIVATE); thumbPath = prefs.getString("draft_path", null); isVideo = prefs.getBoolean("draft_is_video", false); b.etCaption.setText(prefs.getString("draft_caption", "")); thumbPath?.let { loadSharpImage(it) } }
    private fun clearDraft() { getSharedPreferences("draft_prefs", MODE_PRIVATE).edit().clear().apply() }
    private fun safeToast(msg: String) { runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() } }
    
    private fun startHDImageHunter(targetId: Int) { TdLibManager.downloadFile(targetId); lifecycleScope.launch(Dispatchers.IO) { for (i in 0..20) { val realPath = TdLibManager.getFilePath(targetId); if (realPath != null && File(realPath).exists()) { withContext(Dispatchers.Main) { thumbPath = realPath; loadSharpImage(realPath) }; break }; delay(500) } } }
    private fun loadSharpImage(path: String) { b.ivPreview.load(File(path)) { memoryCachePolicy(CachePolicy.DISABLED); diskCachePolicy(CachePolicy.DISABLED); listener(onSuccess = { _, _ -> b.ivPreview.post { calculateMatrixBounds() } }) } }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = android.view.View.VISIBLE; calculateMatrixBounds() }
        b.btnModeLogo.setOnClickListener {
            val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
            if (uriStr != null) {
                b.ivDraggableLogo.load(Uri.parse(uriStr))
                b.ivDraggableLogo.visibility = android.view.View.VISIBLE
            } else { pickLogoLauncher.launch("image/*") }
        }
        var dX = 0f; var dY = 0f
        b.ivDraggableLogo.setOnTouchListener { v, e ->
            when (e.action) {
                0 -> { dX = v.x - e.rawX; dY = v.y - e.rawY }
                2 -> { v.x = e.rawX + dX; v.y = e.rawY + dY; savedLogoRelX = (v.x - imageBounds.left) / imageBounds.width(); savedLogoRelY = (v.y - imageBounds.top) / imageBounds.height() }
            }
            true
        }
        b.btnSend.setOnClickListener { performSafeSend() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun calculateMatrixBounds() { val d = b.ivPreview.drawable ?: return; val v = FloatArray(9); b.ivPreview.imageMatrix.getValues(v); imageBounds.set(v[2], v[5], v[2] + d.intrinsicWidth * v[0], v[5] + d.intrinsicHeight * v[4]); b.drawingView.setValidBounds(imageBounds) }
    private fun restoreLogoPosition() { b.ivDraggableLogo.x = imageBounds.left + (savedLogoRelX * imageBounds.width()); b.ivDraggableLogo.y = imageBounds.top + (savedLogoRelY * imageBounds.height()) }
    private fun setupMediaToggle() { b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked -> b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f } }

    private fun performSafeSend() {
        b.loadingOverlay.visibility = android.view.View.VISIBLE
        b.btnSend.isEnabled = false
        val target = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("target_username", "") ?: ""
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var logoUri: Uri? = null
                if (b.ivDraggableLogo.visibility == 0) {
                    val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
                    if (uriStr != null) {
                        val uri = Uri.parse(uriStr)
                        // כאן התיקון: contentResolver במקום context
                        try { applicationContext.contentResolver.openInputStream(uri)?.close(); logoUri = uri } catch(e: Exception) {}
                    }
                }

                val outPath = File(cacheDir, "out.${if(isVideo) "mp4" else "jpg"}").absolutePath
                val rects = b.drawingView.rects.map { BlurRect(it.left, it.top, it.right, it.bottom) }
                val relW = if (imageBounds.width() > 0) (b.ivDraggableLogo.width) / imageBounds.width() else 0.2f

                val success = if (isVideo) {
                    suspendCoroutine { cont -> MediaProcessor.processContent(applicationContext, thumbPath!!, outPath, true, rects, logoUri, savedLogoRelX, savedLogoRelY, relW) { cont.resume(it) } }
                } else {
                    ImageUtils.processImage(applicationContext, thumbPath!!, outPath, rects, logoUri, savedLogoRelX, savedLogoRelY, relW)
                }

                withContext(Dispatchers.Main) {
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), if (success) outPath else thumbPath, isVideo)
                    clearDraft(); finish()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { b.loadingOverlay.visibility = 8; b.btnSend.isEnabled = true; safeToast("Error: ${e.message}") } }
        }
    }
}
