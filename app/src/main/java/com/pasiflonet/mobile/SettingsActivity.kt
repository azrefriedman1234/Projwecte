package com.pasiflonet.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivitySettingsBinding
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
                updateUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickLogo.setOnClickListener { pickImage.launch("image/*") }
        
        lifecycleScope.launch { updateUI() }
    }
    
    private suspend fun updateUI() {
        val repo = DataStoreRepo(this)
        val uriStr = repo.logoUri.first()
        if (uriStr != null) {
            binding.ivCurrentLogo.setImageURI(Uri.parse(uriStr))
        }
    }
}
