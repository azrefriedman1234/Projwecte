package com.pasiflonet.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivitySettingsBinding
import com.pasiflonet.mobile.utils.DataStoreRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // טעינת ההגדרות הקיימות
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@SettingsActivity)
            val currentTarget = repo.targetUsername.first()
            if (!currentTarget.isNullOrEmpty()) {
                binding.etTargetUsername.setText(currentTarget)
            }
        }

        // כפתור שמירה
        binding.btnSaveSettings.setOnClickListener {
            val target = binding.etTargetUsername.text.toString()
            if (target.isNotEmpty()) {
                lifecycleScope.launch {
                    DataStoreRepo(this@SettingsActivity).saveTargetUsername(target)
                    Toast.makeText(this@SettingsActivity, "Target Channel Saved!", Toast.LENGTH_SHORT).show()
                    finish() // סוגר את המסך אחרי שמירה
                }
            } else {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
            }
        }

        // כפתור ניקוי מטמון בטוח
        binding.btnClearCache.setOnClickListener {
            val cacheDir = cacheDir
            val tdlibFiles = File(filesDir, "tdlib_files") // מדיה של טלגרם בלבד
            
            var count = 0
            cacheDir.deleteRecursively()
            if (tdlibFiles.exists()) {
                tdlibFiles.listFiles()?.forEach { 
                    it.deleteRecursively() 
                    count++
                }
            }
            Toast.makeText(this, "Cleaned temp files. Login kept safe.", Toast.LENGTH_LONG).show()
        }
    }
}
