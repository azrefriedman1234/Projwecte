package com.pasiflonet.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pasiflonet.mobile.databinding.ActivityMainBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.CacheManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: ChatAdapter

    // מנהל ההרשאות
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "Permissions Granted. FFmpeg Ready.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage access is required for video editing!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // בדיקה ובקשת הרשאות מיד עם הפתיחה
        checkStoragePermissions()

        setupUI()
        initTelegram()
    }

    private fun checkStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // אנדרואיד 13+: מבקשים תמונות ווידאו בנפרד
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            // אנדרואיד 12 ומטה: מבקשים אחסון כללי
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupUI() {
        adapter = ChatAdapter(emptyList()) { msg ->
            val intent = Intent(this, DetailsActivity::class.java)
            var thumbPath: String? = null
            var fullId = 0
            var isVideo = false
            var caption = ""

            when (msg.content) {
                is TdApi.MessagePhoto -> {
                    val c = msg.content as TdApi.MessagePhoto
                    thumbPath = c.photo.sizes.firstOrNull()?.photo?.local?.path
                    // הגנה מפני קריסה אם אין רשימת גדלים
                    fullId = if (c.photo.sizes.isNotEmpty()) c.photo.sizes.last().photo.id else 0
                    caption = c.caption.text
                }
                is TdApi.MessageVideo -> {
                    val c = msg.content as TdApi.MessageVideo
                    thumbPath = c.video.thumbnail?.file?.local?.path
                    fullId = c.video.video.id
                    isVideo = true
                    caption = c.caption.text
                }
                is TdApi.MessageText -> caption = (msg.content as TdApi.MessageText).text.text
            }
            
            intent.putExtra("THUMB_PATH", thumbPath)
            intent.putExtra("FILE_ID", fullId)
            intent.putExtra("IS_VIDEO", isVideo)
            intent.putExtra("CAPTION", caption)
            startActivity(intent)
        }
        
        b.rvMessages.layoutManager = LinearLayoutManager(this)
        b.rvMessages.adapter = adapter

        b.btnClearCache.setOnClickListener {
            val deleted = CacheManager.clearAppCache(this)
            Toast.makeText(this, "Cleared ${deleted/1024} KB", Toast.LENGTH_SHORT).show()
        }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun initTelegram() {
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@MainActivity)
            val id = repo.apiId.first()
            val hash = repo.apiHash.first()
            
            if (id != null && hash != null) {
                TdLibManager.init(this@MainActivity, id, hash)
                
                TdLibManager.authState.collect { state ->
                    runOnUiThread {
                        if (state is TdApi.AuthorizationStateReady) {
                            b.tvConnectionStatus.text = "🟢 Online"
                            b.tvConnectionStatus.setTextColor(0xFF4CAF50.toInt())
                        } else {
                            b.tvConnectionStatus.text = "🔴 Connecting..."
                            b.tvConnectionStatus.setTextColor(0xFFFF0000.toInt())
                        }
                    }
                }
                
                TdLibManager.currentMessages.collect { msgs ->
                    runOnUiThread { adapter.updateList(msgs) }
                }
            } else {
                b.tvConnectionStatus.text = "⚠️ Setup API"
                b.tvConnectionStatus.setTextColor(0xFFFFA500.toInt())
            }
        }
    }
}
