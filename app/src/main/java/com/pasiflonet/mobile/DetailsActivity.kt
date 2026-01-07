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
    private var thumbPath: String? = null; private var isVideo = false; private var fileId = 0
    private var dX = 0f; private var dY = 0f; private var logoScale = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)

        thumbPath = intent.getStringExtra("THUMB_PATH")
        fileId = intent.getIntExtra("FILE_ID", 0)
        isVideo = intent.getBooleanExtra("IS_VIDEO", false)
        b.etCaption.setText(intent.getStringExtra("CAPTION") ?: "")

        if (thumbPath != null) b.ivPreview.setImageBitmap(BitmapFactory.decodeFile(thumbPath))
        lifecycleScope.launch { TdLibManager.downloadFile(fileId) }

        setupTools()
    }

    private fun setupTools() {
        b.btnModeBlur.setOnClickListener { b.drawingView.isBlurMode = true; b.drawingView.visibility = View.VISIBLE; b.ivDraggableLogo.alpha = 0.5f }
        b.btnModeLogo.setOnClickListener {
            b.drawingView.isBlurMode = false
            lifecycleScope.launch {
                val uriStr = DataStoreRepo(this@DetailsActivity).logoUri.first()
                if (uriStr != null) { b.ivDraggableLogo.visibility = View.VISIBLE; b.ivDraggableLogo.setImageURI(Uri.parse(uriStr)); b.ivDraggableLogo.alpha = 1.0f }
                else Toast.makeText(this@DetailsActivity, "Set Logo in Settings!", Toast.LENGTH_SHORT).show()
            }
        }

        b.ivDraggableLogo.setOnTouchListener { view, event ->
            if (b.drawingView.isBlurMode) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY }
                MotionEvent.ACTION_MOVE -> view.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
            }
            true
        }

        b.sbLogoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { val s = 0.5f + (p/100f); b.ivDraggableLogo.scaleX = s; b.ivDraggableLogo.scaleY = s; logoScale = s }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.btnTranslate.setOnClickListener { lifecycleScope.launch { val t = b.etCaption.text.toString(); if (t.isNotEmpty()) b.etCaption.setText(TranslationManager.translateToHebrew(t)) } }
        b.btnSend.setOnClickListener { sendData() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun sendData() {
        b.btnSend.text = "Sending..."; b.btnSend.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = DataStoreRepo(this@DetailsActivity)
            val target = repo.targetUsername.first() ?: ""
            if (target.isEmpty()) return@launch

            val outPath = File(cacheDir, "sent_${System.currentTimeMillis()}.jpg").absolutePath
            val logoUri = repo.logoUri.first()?.let { Uri.parse(it) }
            
            if (!isVideo && thumbPath != null) {
                MediaProcessor.processImage(this@DetailsActivity, thumbPath!!, outPath, b.drawingView.rects, logoUri, b.ivDraggableLogo.x/b.previewContainer.width, b.ivDraggableLogo.y/b.previewContainer.height, logoScale)
                TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), outPath, false)
            } else if (isVideo && thumbPath != null) {
                TdLibManager.sendFinalMessage(target, b.etCaption.text.toString(), thumbPath!!, true)
            }
            withContext(Dispatchers.Main) { Toast.makeText(this@DetailsActivity, "Sent!", Toast.LENGTH_SHORT).show(); finish() }
        }
    }
}
