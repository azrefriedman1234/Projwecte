package com.pasiflonet.mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pasiflonet.mobile.utils.AppDataStore
import com.pasiflonet.mobile.utils.WatermarkSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Toggle logo, choose file (SAF with takePersistableUriPermission)
        // Presets dropdown, advanced sliders
        CoroutineScope(Dispatchers.IO).launch {
            AppDataStore.saveWatermarkSettings(this@SettingsActivity, WatermarkSettings())
        }
    }
}
