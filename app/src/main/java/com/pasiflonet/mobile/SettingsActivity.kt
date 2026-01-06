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
    private lateinit var binding: ActivitySettingsBinding
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            lifecycleScope.launch {
                DataStoreRepo(this@SettingsActivity).saveLogo(it.toString())
                binding.ivCurrentLogo.setImageURI(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val repo = DataStoreRepo(this@SettingsActivity)
            binding.etTargetUsername.setText(repo.targetUsername.first() ?: "")
            repo.logoUri.first()?.let { binding.ivCurrentLogo.setImageURI(Uri.parse(it)) }
        }

        binding.btnSaveSettings.setOnClickListener {
            val user = binding.etTargetUsername.text.toString()
            lifecycleScope.launch {
                DataStoreRepo(this@SettingsActivity).saveTargetUsername(user)
                Toast.makeText(this@SettingsActivity, "Target Saved", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPickLogo.setOnClickListener { pickImage.launch("image/*") }

        // הפעלת הניקוי
        binding.btnClearCache.setOnClickListener {
            val deleted = CacheManager.clearAppCache(this)
            val mb = deleted / (1024 * 1024)
            Toast.makeText(this, "Cleaned $mb MB. Connection safe.", Toast.LENGTH_LONG).show()
        }
    }
}
