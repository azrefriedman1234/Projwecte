package com.pasiflonet.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import com.pasiflonet.mobile.databinding.ActivityDetailsBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.MediaProcessor
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

class DetailsActivity : AppCompatActivity() {
    private lateinit var b: ActivityDetailsBinding
    private var thumbPath: String? = null
    private var isVideo = false
    private var fileId = 0
    private var thumbId = 0
    
    // גבולות התמונה האמיתיים על המסך
    private var imageBounds = RectF()
    
    private var savedLogoRelX = 0.5f
    private var savedLogoRelY = 0.5f
    private var savedLogoScale = 1.0f
    private var dX = 0f
    private var dY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            b = ActivityDetailsBinding.inflate(layoutInflater)
            setContentView(b.root)
            
            // --- תיקון פרופורציות קריטי ---
            // מכריחים את התמונה להתאים בדיוק למסגרת בלי חיתוכים
            b.ivPreview.scaleType = ImageView.ScaleType.FIT_CENTER
            
            thumbPath = intent.getStringExtra("THUMB_PATH")
            val miniThumb = intent.getByteArrayExtra("MINI_THUMB")
            fileId = intent.getIntExtra("FILE_ID", 0); thumbId = intent.getIntExtra("THUMB_ID", 0)
            isVideo = intent.getBooleanExtra("IS_VIDEO", false)
            b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")
            
            if (miniThumb != null) b.ivPreview.load(miniThumb)
            
            val targetId = if (thumbId != 0) thumbId else fileId
            if (targetId != 0) startHDImageHunter(targetId) else if (thumbPath != null) loadSharpImage(thumbPath!!)
            if (targetId == 0 && thumbPath.isNullOrEmpty()) { b.swIncludeMedia.isChecked = false; b.swIncludeMedia.isEnabled = false }
            
            b.ivPreview.viewTreeObserver.addOnGlobalLayoutListener { 
                calculateExactImageBounds() 
                if (b.ivDraggableLogo.visibility == android.view.View.VISIBLE) restoreLogoPosition() 
            }
            
            setupTools(); setupMediaToggle()
        } catch (e: Exception) { Toast.makeText(this, "Init Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun startHDImageHunter(targetId: Int) {
        TdLibManager.downloadFile(targetId)
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0..20) {
                val realPath = TdLibManager.getFilePath(targetId)
                if (realPath != null && File(realPath).exists() && File(realPath).length() > 1000) {
                    withContext(Dispatchers.Main) { thumbPath = realPath; loadSharpImage(realPath) }
                    break
                }
                delay(500)
            }
        }
    }

    private fun loadSharpImage(path: String) { 
        b.ivPreview.load(File(path)) { 
            memoryCachePolicy(CachePolicy.DISABLED); diskCachePolicy(CachePolicy.DISABLED); crossfade(true)
            listener(onSuccess = { _, _ -> b.ivPreview.post { calculateExactImageBounds() } }) 
        } 
    }

    // --- חישוב גבולות מדויק לתמונת FIT_CENTER ---
    // זה הלב של התיקון. זה מחשב בדיוק איפה הפיקסלים של התמונה נמצאים על המסך.
    private fun calculateExactImageBounds() {
        val imageView = b.ivPreview
        val drawable = imageView.drawable ?: return
        
        val imageW = drawable.intrinsicWidth.toFloat()
        val imageH = drawable.intrinsicHeight.toFloat()
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()
        
        if (viewW == 0f || viewH == 0f) return

        val scaleX = viewW / imageW
        val scaleY = viewH / imageH
        val scale = Math.min(scaleX, scaleY) // FIT_CENTER לוקח את המינימום

        val finalW = imageW * scale
        val finalH = imageH * scale
        
        // מרכוז
        val left = (viewW - finalW) / 2
        val top = (viewH - finalH) / 2
        
        imageBounds.set(left, top, left + finalW, top + finalH)
        b.drawingView.setValidBounds(imageBounds)
    }
    
    private fun restoreLogoPosition() { if (imageBounds.width() > 0) { b.ivDraggableLogo.x = imageBounds.left + (savedLogoRelX * imageBounds.width()); b.ivDraggableLogo.y = imageBounds.top + (savedLogoRelY * imageBounds.height()) } }
    private fun setupMediaToggle() { b.swIncludeMedia.setOnCheckedChangeListener { _, isChecked -> val v = if (isChecked) android.view.View.GONE else android.view.View.VISIBLE; b.vDisabledOverlay.visibility = v; b.tvTextOnlyLabel.visibility = v; b.mediaToolsContainer.alpha = if (isChecked) 1.0f else 0.3f; enableMediaTools(isChecked) } }
    private fun enableMediaTools(enable: Boolean) { b.btnModeBlur.isEnabled = enable; b.btnModeLogo.isEnabled = enable; b.sbLogoSize.isEnabled = enable; if (!enable) { b.drawingView.visibility = android.view.View.GONE; b.ivDraggableLogo.visibility = android.view.View.GONE; b.drawingView.isBlurMode = false } }
    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = android.view.View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f; calculateExactImageBounds() }
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false; b.ivDraggableLogo.visibility = android.view.View.VISIBLE; b.ivDraggableLogo.alpha = 1.0f
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE); val uriStr = prefs.getString("logo_uri", null)
            if (uriStr != null) { b.ivDraggableLogo.load(Uri.parse(uriStr)); b.ivDraggableLogo.clearColorFilter() } 
            else { b.ivDraggableLogo.load(android.R.drawable.ic_menu_gallery); b.ivDraggableLogo.setColorFilter(Color.WHITE) }
            b.ivDraggableLogo.post { calculateExactImageBounds(); savedLogoRelX = 0.5f - ((b.ivDraggableLogo.width / 2f) / imageBounds.width()); savedLogoRelY = 0.5f - ((b.ivDraggableLogo.height / 2f) / imageBounds.height()); restoreLogoPosition() }
        }
        b.ivDraggableLogo.setOnTouchListener { view, event -> if (b.drawingView.isBlurMode) return@setOnTouchListener false; when (event.action) { android.view.MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY }; android.view.MotionEvent.ACTION_MOVE -> { var newX = event.rawX + dX; var newY = event.rawY + dY; if (newX < imageBounds.left) newX = imageBounds.left; if (newX + view.width > imageBounds.right) newX = imageBounds.right - view.width; if (newY < imageBounds.top) newY = imageBounds.top; if (newY + view.height > imageBounds.bottom) newY = imageBounds.bottom - view.height; view.x = newX; view.y = newY; if (imageBounds.width() > 0) { savedLogoRelX = (newX - imageBounds.left) / imageBounds.width(); savedLogoRelY = (newY - imageBounds.top) / imageBounds.height() } } }; true }
        b.sbLogoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { val scale = 0.5f + (p / 50f); b.ivDraggableLogo.scaleX = scale; b.ivDraggableLogo.scaleY = scale; savedLogoScale = scale }; override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {} })
        b.btnTranslate.setOnClickListener { lifecycleScope.launch { val t = b.etCaption.text.toString(); if (t.isNotEmpty()) b.etCaption.setText(TranslationManager.translateToHebrew(t)) } }
        b.btnSend.setOnClickListener { sendData() }; b.btnCancel.setOnClickListener { finish() }
    }

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap { if (drawable is BitmapDrawable) return drawable.bitmap; val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); drawable.setBounds(0, 0, canvas.width, canvas.height); drawable.draw(canvas); return bitmap }
    private fun cleanFloat(v: Float): Float { if (v.isNaN() || v.isInfinite()) return 0.0f; if (v < 0f) return 0f; if (v > 1f) return 1f; return v }

    private fun sendData() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val target = prefs.getString("target_username", "") ?: ""
        if (target.isEmpty()) { Toast.makeText(this, "Set Target!", Toast.LENGTH_SHORT).show(); return }
        val caption = b.etCaption.text.toString(); val includeMedia = b.swIncludeMedia.isChecked; val targetId = if (thumbId != 0) thumbId else fileId; val currentThumbPath = thumbPath
        
        calculateExactImageBounds() // וודא חישוב לפני שמירה

        val rectsSnapshot = ArrayList<BlurRect>()
        for (r in b.drawingView.rects) { rectsSnapshot.add(BlurRect(cleanFloat(r.left), cleanFloat(r.top), cleanFloat(r.right), cleanFloat(r.bottom))) }

        val boundsW = if (imageBounds.width() > 0) imageBounds.width() else 1000f
        val logoW = b.ivDraggableLogo.width * savedLogoScale
        var relW = logoW / boundsW; if (relW.isNaN() || relW <= 0) relW = 0.2f
        val relativeWidthSnapshot = relW
        val logoRelXSnapshot = cleanFloat(savedLogoRelX); val logoRelYSnapshot = cleanFloat(savedLogoRelY)
        
        var logoUriStr = prefs.getString("logo_uri", null); var logoUri = if (logoUriStr != null) Uri.parse(logoUriStr) else null
        if (b.ivDraggableLogo.visibility == android.view.View.VISIBLE && logoUri == null) {
            try { val drawable = b.ivDraggableLogo.drawable; if (drawable != null) { val bitmap = getBitmapFromDrawable(drawable); val file = File(cacheDir, "captured_logo_final.png"); val out = FileOutputStream(file); bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.flush(); out.close(); logoUri = Uri.fromFile(file) } } catch (e: Exception) { e.printStackTrace() }
        }

        Toast.makeText(this, "Processing in background...", Toast.LENGTH_SHORT).show()
        finish() 

        GlobalScope.launch(Dispatchers.IO) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pasiflonet:EncodeLock")
            wakeLock.acquire(10*60*1000L)
            try {
                if (!includeMedia) { TdLibManager.sendFinalMessage(target, caption, null, false); return@launch }
                var finalPath = currentThumbPath
                if (finalPath == null || !File(finalPath).exists()) {
                    if (targetId != 0) { TdLibManager.downloadFile(targetId); for (i in 1..60) { val realPath = TdLibManager.getFilePath(targetId); if (realPath != null && File(realPath).exists() && File(realPath).length() > 0) { finalPath = realPath; break }; delay(1000) } }
                }
                if (finalPath == null || !File(finalPath).exists()) return@launch 
                val extension = if(isVideo) "mp4" else "png"
                val outputPath = File(cacheDir, "bg_proc_${System.currentTimeMillis()}.$extension").absolutePath
                MediaProcessor.processContent(applicationContext, finalPath, outputPath, isVideo, rectsSnapshot, logoUri, logoRelXSnapshot, logoRelYSnapshot, relativeWidthSnapshot) { success -> if (success) TdLibManager.sendFinalMessage(target, caption, outputPath, isVideo) }
            } finally { if (wakeLock.isHeld) wakeLock.release() }
        }
    }
}
