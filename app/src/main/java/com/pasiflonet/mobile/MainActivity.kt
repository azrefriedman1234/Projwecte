package com.pasiflonet.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: ChatAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.apiContainer.visibility = View.GONE
        b.loginContainer.visibility = View.GONE
        b.mainContent.visibility = View.GONE

        setupUI()
        checkPermissions()
        checkApiAndInit()
    }

    private fun setupUI() {
        b.btnSaveApi.setOnClickListener {
            val id = b.etApiId.text.toString().toIntOrNull()
            val hash = b.etApiHash.text.toString()
            if (id != null && hash.isNotEmpty()) {
                lifecycleScope.launch {
                    DataStoreRepo(this@MainActivity).saveApi(id, hash)
                    checkApiAndInit()
                }
            }
        }
        b.btnSendCode.setOnClickListener { TdLibManager.sendPhone(b.etPhone.text.toString()) }
        b.btnVerify.setOnClickListener { TdLibManager.sendCode(b.etCode.text.toString()) }
        b.btnVerifyPassword.setOnClickListener { TdLibManager.sendPassword(b.etPassword.text.toString()) }

        adapter = ChatAdapter(emptyList()) { msg ->
            // לוגיקה חדשה: בדיקה לפני פתיחת מסך פרטים
            var thumbPath: String? = null
            var fullId = 0
            var isVideo = false
            var caption = ""
            var hasMedia = false

            when (msg.content) {
                is TdApi.MessagePhoto -> {
                    val c = msg.content as TdApi.MessagePhoto
                    thumbPath = c.photo.sizes.lastOrNull()?.photo?.local?.path // מנסים לקחת את הגדול ביותר
                    if (thumbPath.isNullOrEmpty()) thumbPath = c.photo.sizes.firstOrNull()?.photo?.local?.path // אם אין, את הקטן
                    fullId = if (c.photo.sizes.isNotEmpty()) c.photo.sizes.last().photo.id else 0
                    caption = c.caption.text
                    hasMedia = true
                }
                is TdApi.MessageVideo -> {
                    val c = msg.content as TdApi.MessageVideo
                    thumbPath = c.video.thumbnail?.file?.local?.path
                    fullId = c.video.video.id
                    isVideo = true
                    caption = c.caption.text
                    hasMedia = true
                }
                is TdApi.MessageText -> caption = (msg.content as TdApi.MessageText).text.text
            }

            // אם זו הודעת טקסט או שהקובץ לא ירד עדיין
            if (!hasMedia) {
                Toast.makeText(this, "This is a text message (No media)", Toast.LENGTH_SHORT).show()
                return@ChatAdapter
            }

            // אם הנתיב ריק, סימן שהקובץ עוד לא ירד
            if (thumbPath.isNullOrEmpty() || !File(thumbPath).exists()) {
                Toast.makeText(this, "Media downloading... Try again in a second", Toast.LENGTH_SHORT).show()
                TdLibManager.downloadFile(fullId) // מבקש הורדה דחופה
                return@ChatAdapter
            }
            
            val intent = Intent(this, DetailsActivity::class.java)
            intent.putExtra("THUMB_PATH", thumbPath)
            intent.putExtra("FILE_ID", fullId)
            intent.putExtra("IS_VIDEO", isVideo)
            intent.putExtra("CAPTION", caption)
            startActivity(intent)
        }
        
        b.rvMessages.layoutManager = LinearLayoutManager(this)
        b.rvMessages.adapter = adapter

        b.btnClearCache.setOnClickListener { CacheManager.clearAppCache(this) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun checkApiAndInit() {
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@MainActivity)
            val apiId = repo.apiId.first()
            val apiHash = repo.apiHash.first()

            if (apiId != null && apiHash != null) {
                b.apiContainer.visibility = View.GONE
                TdLibManager.init(this@MainActivity, apiId, apiHash)
                observeAuth()
            } else {
                b.apiContainer.visibility = View.VISIBLE
                b.mainContent.visibility = View.GONE
            }
        }
    }

    private fun observeAuth() {
        lifecycleScope.launch {
            TdLibManager.authState.collect { state ->
                runOnUiThread {
                    b.apiContainer.visibility = View.GONE
                    b.loginContainer.visibility = View.GONE
                    b.mainContent.visibility = View.GONE
                    
                    when (state) {
                        is TdApi.AuthorizationStateWaitPhoneNumber -> {
                            b.loginContainer.visibility = View.VISIBLE
                            b.phoneLayout.visibility = View.VISIBLE
                            b.codeLayout.visibility = View.GONE
                            b.passwordLayout.visibility = View.GONE
                            b.tvLoginStatus.text = "Enter Phone Number"
                        }
                        is TdApi.AuthorizationStateWaitCode -> {
                            b.loginContainer.visibility = View.VISIBLE
                            b.phoneLayout.visibility = View.GONE
                            b.codeLayout.visibility = View.VISIBLE
                            b.passwordLayout.visibility = View.GONE
                            b.tvLoginStatus.text = "Enter Code"
                        }
                        is TdApi.AuthorizationStateWaitPassword -> {
                            b.loginContainer.visibility = View.VISIBLE
                            b.phoneLayout.visibility = View.GONE
                            b.codeLayout.visibility = View.GONE
                            b.passwordLayout.visibility = View.VISIBLE
                            b.tvLoginStatus.text = "Two-Step Verification"
                        }
                        is TdApi.AuthorizationStateReady -> {
                            b.mainContent.visibility = View.VISIBLE
                            b.tvConnectionStatus.text = "🟢 Online"
                            b.tvConnectionStatus.setTextColor(0xFF4CAF50.toInt())
                        }
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            TdLibManager.currentMessages.collect { msgs ->
                runOnUiThread { adapter.updateList(msgs) }
            }
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}
