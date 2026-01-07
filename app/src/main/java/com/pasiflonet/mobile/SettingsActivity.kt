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
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val localFile = File(filesDir, "app_logo.png")
                    val outputStream = FileOutputStream(localFile)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()

                    val localUri = Uri.fromFile(localFile).toString()
                    DataStoreRepo(this@SettingsActivity).saveLogoUri(localUri)
                    
                    b.ivCurrentLogo.setImageURI(Uri.parse(localUri))
                    Toast.makeText(this@SettingsActivity, "Logo Saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Error saving logo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // עטיפת הכל ב-Try-Catch אחד גדול
        try {
            b = ActivitySettingsBinding.inflate(layoutInflater)
            setContentView(b.root)

            // נסיון בטוח לטעון מטמון
            try {
                updateCacheSize()
            } catch (e: Exception) {
                b.btnClearCache.text = "Clear Cache"
            }

            // נסיון בטוח לטעון נתונים
            lifecycleScope.launch {
                try {
                    val repo = DataStoreRepo(this@SettingsActivity)
                    val currentTarget = repo.targetUsername.first()
                    val currentLogo = repo.logoUri.first()
                    
                    if (!currentTarget.isNullOrEmpty()) b.etTargetUsername.setText(currentTarget)
                    
                    if (!currentLogo.isNullOrEmpty()) {
                        try { b.ivCurrentLogo.setImageURI(Uri.parse(currentLogo)) } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    // התעלמות משגיאות טעינה ראשוניות
                }
            }

            b.btnSaveSettings.setOnClickListener {
                val target = b.etTargetUsername.text.toString()
                if (target.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            DataStoreRepo(this@SettingsActivity).saveTargetUsername(target)
                            Toast.makeText(this@SettingsActivity, "Saved! Target: $target", Toast.LENGTH_SHORT).show()
                            finish()
                        } catch (e: Exception) {
                            Toast.makeText(this@SettingsActivity, "Save Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@SettingsActivity, "Enter channel name (e.g. @MyChannel)", Toast.LENGTH_SHORT).show()
                }
            }

            b.btnSelectLogo.setOnClickListener { pickLogo.launch("image/*") }
            
            b.btnClearCache.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        b.btnClearCache.text = "Cleaning..."
                        b.btnClearCache.isEnabled = false
                        CacheManager.clearAppCache(this@SettingsActivity)
                        updateCacheSize()
                        b.btnClearCache.isEnabled = true
                    } catch (e: Exception) {
                         b.btnClearCache.isEnabled = true
                         b.btnClearCache.text = "Error Cleaning"
                    }
                }
            }
        } catch (e: Exception) {
            // רשת ביטחון אחרונה - אם המסך קורס בטעינה
            e.printStackTrace()
            Toast.makeText(this, "Settings Crash: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // סגירה עדינה
        }
    }

    private fun updateCacheSize() {
        val size = CacheManager.getCacheSize(this)
        b.btnClearCache.text = "🗑️ Clear Cache (Current: $size)"
    }
}
