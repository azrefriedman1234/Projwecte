package com.pasiflonet.mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pasiflonet.mobile.ui.OverlayView
import com.pasiflonet.mobile.worker.SendWorker

class DetailsActivity : AppCompatActivity() {
    private lateinit var overlayView: OverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preview image/video
        // Add overlayView for blur rects
        // Buttons: add/remove mode, undo, clear
        // Send button: enqueue SendWorker
        val sourcePath = "" // from intent
        val targetChatId = 0L // from intent
        val data = Data.Builder()
            .putString("sourcePath", sourcePath)
            .putLong("targetChatId", targetChatId)
            .build()
        val request = OneTimeWorkRequestBuilder<SendWorker>().setInputData(data).build()
        WorkManager.getInstance(this).enqueue(request)
    }
}
