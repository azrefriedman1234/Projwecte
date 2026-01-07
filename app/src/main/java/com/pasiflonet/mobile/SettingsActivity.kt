package com.pasiflonet.mobile

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivitySettingsBinding
import com.pasiflonet.mobile.utils.CacheManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                DataStoreRepo(this@SettingsActivity).saveLogoUri(uri.toString())
                b.ivCurrentLogo.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        updateCacheSize()

        lifecycleScope.launch {
            val repo = DataStoreRepo(this@SettingsActivity)
            val currentTarget = repo.targetUsername.first()
            val currentLogo = repo.logoUri.first()
            
            if (!currentTarget.isNullOrEmpty()) b.etTargetUsername.setText(currentTarget)
            if (!currentLogo.isNullOrEmpty()) b.ivCurrentLogo.setImageURI(Uri.parse(currentLogo))
        }

        b.btnSaveSettings.setOnClickListener {
            val target = b.etTargetUsername.text.toString()
            if (target.isNotEmpty()) {
                lifecycleScope.launch {
                    DataStoreRepo(this@SettingsActivity).saveTargetUsername(target)
                    Toast.makeText(this@SettingsActivity, "Saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        b.btnSelectLogo.setOnClickListener { pickLogo.launch("image/*") }
        
        // כפתור הניקוי המשודרג
        b.btnClearCache.setOnClickListener {
            lifecycleScope.launch {
                b.btnClearCache.text = "Cleaning..."
                b.btnClearCache.isEnabled = false
                
                CacheManager.clearAppCache(this@SettingsActivity)
                
                updateCacheSize()
                b.btnClearCache.isEnabled = true
            }
        }
    }

    private fun updateCacheSize() {
        val size = CacheManager.getCacheSize(this)
        b.btnClearCache.text = "🗑️ Clear Cache (Current: $size)"
    }
}
