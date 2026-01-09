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

        // --- תיקון: טעינת תמונה ממוזערת מיידית (Mini Thumb) ---
        val miniThumb = intent.getByteArrayExtra("MINI_THUMB")
        if (miniThumb != null && miniThumb.isNotEmpty()) {
            // טעינה מהירה מהזיכרון
            b.ivPreview.load(miniThumb) {
                listener(onSuccess = { _, _ -> b.ivPreview.post { calculateMatrixBounds() } })
            }
        }

        // --- נסיון לטעון תמונה איכותית יותר אם קיימת ---
        val path = intent.getStringExtra("THUMB_PATH")
        if (path != null && File(path).exists()) {
            actualMediaPath = path
            b.ivPreview.load(File(path)) {
                listener(onSuccess = { _, _ -> b.ivPreview.post { calculateMatrixBounds() } })
            }
        } else if (thumbId != 0) {
            // אם אין נתיב, מורידים את ה-Thumbnail הגדול
            startThumbHunter(thumbId)
        }

        // במקביל - מורידים את המדיה המלאה (וידאו/תמונה מקורית) לצרכי שליחה
        if (fileId != 0) startFullMediaHunter(fileId)

        setupTools()
    }

    private fun startThumbHunter(tId: Int) {
        TdLibManager.downloadFile(tId)
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0..10) {
                val path = TdLibManager.getFilePath(tId)
                if (path != null && File(path).exists()) {
                    withContext(Dispatchers.Main) { 
                        b.ivPreview.load(File(path)) {
                             listener(onSuccess = { _, _ -> b.ivPreview.post { calculateMatrixBounds() } })
                        }
                    }
                    break
                }
                delay(1000)
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
                    // וידוא שהקובץ מלא (גדול מ-50KB או שזו תמונה)
                    if (file.length() > 50000 || !isVideo) {
                        actualMediaPath = path
                        Log.d("Hunter", "Full media ready: $path")
                        // אם זו תמונה, נעדכן לתצוגה חדה יותר. אם זה וידאו, נשאיר את ה-Thumbnail
                        if (!isVideo) {
                             withContext(Dispatchers.Main) { 
                                 b.ivPreview.load(file) {
                                     listener(onSuccess = { _, _ -> b.ivPreview.post { calculateMatrixBounds() } })
                                 }
                             }
                        }
                        break
                    }
                }
                delay(1500)
            }
        }
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener {
            b.drawingView.visibility = View.VISIBLE
            b.drawingView.bringToFront()
            b.drawingView.isBlurMode = true
            calculateMatrixBounds()
        }

        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
            if (uriStr != null) { loadLogoFromUri(Uri.parse(uriStr)) }
            else { pickLogoLauncher.launch("image/*") }
        }

        // גרירת לוגו
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

        b.btnSend.setOnClickListener { performSafeSend() }
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
        // חישוב מדויק של גבולות התמונה על המסך
        val scaleX = v[Matrix.MSCALE_X]; val scaleY = v[Matrix.MSCALE_Y]
        val transX = v[Matrix.MTRANS_X]; val transY = v[Matrix.MTRANS_Y]
        val w = d.intrinsicWidth * scaleX; val h = d.intrinsicHeight * scaleY
        imageBounds.set(transX, transY, transX + w, transY + h)
        b.drawingView.setValidBounds(imageBounds)
    }

    private fun restoreLogoPosition() {
        if (imageBounds.width() > 0) {
            b.ivDraggableLogo.x = imageBounds.left + (savedLogoRelX * imageBounds.width())
            b.ivDraggableLogo.y = imageBounds.top + (savedLogoRelY * imageBounds.height())
        }
    }

    private fun performSafeSend() {
        val path = actualMediaPath
        if (path == null || !File(path).exists()) {
            safeToast("Wait, downloading full quality media...")
            return
        }

        b.loadingOverlay.visibility = View.VISIBLE
        b.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outPath = File(cacheDir, "out_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}").absolutePath
                val rects = b.drawingView.rects.map { BlurRect(it.left, it.top, it.right, it.bottom) }
                
                val logoUriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
                val logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
                
                // ברירת מחדל לרוחב לוגו אם לא חושב (20% מהרוחב)
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
                    // אם העיבוד הצליח שולחים את המעובד, אחרת את המקור
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), if (success) outPath else path, isVideo)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    b.loadingOverlay.visibility = View.GONE; b.btnSend.isEnabled = true
                    safeToast("Error: ${e.message}") 
                }
            }
        }
    }

    private fun safeToast(msg: String) { runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
    private fun setupMediaToggle() { b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked -> b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f } }
}
