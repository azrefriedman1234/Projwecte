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
import android.graphics.Bitmap
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
        
        val passedThumbPath = intent.getStringExtra("THUMB_PATH")
        b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")

        // טעינת תצוגה מקדימה
        if (passedThumbPath != null && File(passedThumbPath).exists()) {
            loadPreview(passedThumbPath)
        } else if (thumbId != 0) {
            startThumbHunter(thumbId)
        }

        // הורדת מדיה מלאה ברקע
        if (fileId != 0) startFullMediaHunter(fileId)

        setupTools()
    }

    private fun loadPreview(path: String) {
        b.ivPreview.load(File(path)) {
            listener(onSuccess = { _, _ -> 
                b.ivPreview.post { 
                    calculateMatrixBounds() 
                    // אם הלוגו היה אמור להיות מוצג, נמקם אותו מחדש
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
                        actualMediaPath = path
                        Log.d("Hunter", "Media ready: $path")
                        if (!isVideo) withContext(Dispatchers.Main) { loadPreview(path) }
                        break
                    }
                }
                delay(1000)
            }
        }
    }

    private fun setupTools() {
        // --- כפתור טשטוש ---
        b.btnModeBlur.setOnClickListener {
            b.drawingView.visibility = View.VISIBLE
            b.drawingView.bringToFront() // מעלה למעלה
            b.drawingView.isBlurMode = true
            b.ivDraggableLogo.alpha = 0.5f // מחליש לוגו כדי להתרכז בטשטוש
            calculateMatrixBounds()
        }

        // --- כפתור לוגו (התיקון הגדול) ---
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            b.ivDraggableLogo.visibility = View.VISIBLE
            b.ivDraggableLogo.alpha = 1.0f
            
            // הכרח את הלוגו לעלות מעל הכל!
            b.ivDraggableLogo.bringToFront() 
            b.mediaToolsContainer.bringToFront() // שהכלים לא יוסתרו

            val uriStr = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("logo_uri", null)
            if (uriStr != null) { 
                loadLogoFromUri(Uri.parse(uriStr)) 
            } else { 
                safeToast("Pick a logo image")
                pickLogoLauncher.launch("image/*") 
            }
        }
        
        // לחיצה ארוכה להחלפה
        b.btnModeLogo.setOnLongClickListener { 
            pickLogoLauncher.launch("image/*"); true 
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
            listener(
                onSuccess = { _, _ ->
                    b.ivDraggableLogo.visibility = View.VISIBLE
                    b.ivDraggableLogo.bringToFront() // שוב, ליתר ביטחון
                    restoreLogoPosition()
                },
                onError = { _, _ ->
                    safeToast("Error loading logo")
                }
            )
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
        } else {
            // מיקום ברירת מחדל באמצע המסך אם החישוב נכשל
            b.ivDraggableLogo.x = b.mainContainer.width / 2f - 100
            b.ivDraggableLogo.y = b.mainContainer.height / 2f - 100
        }
    }

    private fun performSafeSend() {
        val path = actualMediaPath
        if (path == null || !File(path).exists()) {
            safeToast("Downloading video... please wait")
            return
        }

        b.loadingOverlay.visibility = View.VISIBLE
        b.btnSend.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            // עטיפה כללית למניעת קריסה בתוך הקורוטינה
            try {
                val outPath = File(cacheDir, "out_${System.currentTimeMillis()}.${if(isVideo) "mp4" else "jpg"}").absolutePath
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

                // --- כאן ההגנה מקריסה ---
                val success = if (isVideo) {
                    try {
                        // קריאה למעבד הוידאו עם מנגנון השהיה
                        processVideoSuspending(applicationContext, path, outPath, rects, logoUri, savedLogoRelX, savedLogoRelY, relW)
                    } catch (e: Exception) {
                        Log.e("SEND", "Video crash caught!", e)
                        false // אם קרס - מחזירים False, לא קורסים
                    }
                } else {
                    ImageUtils.processImage(applicationContext, path, outPath, rects, logoUri, savedLogoRelX, savedLogoRelY, relW)
                }

                withContext(Dispatchers.Main) {
                    val target = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("target_username", "") ?: ""
                    // אם הצליח -> שלח מעובד. אם נכשל -> שלח מקור. אל תעשה כלום אחר.
                    val finalFile = if (success && File(outPath).exists()) outPath else path
                    
                    if (!success && isVideo) safeToast("Edit failed, sending original video...")
                    
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), finalFile, isVideo)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    b.loadingOverlay.visibility = View.GONE; b.btnSend.isEnabled = true
                    safeToast("Send Error: ${e.message}. Sending original.")
                    // Fallback חירום אחרון
                    val target = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("target_username", "") ?: ""
                    TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), path, isVideo)
                    finish()
                }
            }
        }
    }

    private suspend fun processVideoSuspending(
        ctx: Context, input: String, output: String, rects: List<BlurRect>, logo: Uri?, lx: Float, ly: Float, lw: Float
    ): Boolean = suspendCoroutine { cont ->
        // קריאה למעבד עם הגנה פנימית
        MediaProcessor.processContent(ctx, input, output, true, rects, logo, lx, ly, lw) { result ->
            cont.resume(result)
        }
    }

    private fun safeToast(msg: String) { runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
}
