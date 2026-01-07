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

        // 1. הסתרה אגרסיבית של הכל בהתחלה
        b.apiContainer.visibility = View.GONE
        b.loginContainer.visibility = View.GONE
        b.mainContent.visibility = View.GONE

        setupUI()
        checkPermissions()
        checkApiAndInit()
    }

    private fun setupUI() {
        // כפתורי לוגין
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

        // טבלה
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
                // רק אם אין API מוגדר - מציגים מסך API
                b.apiContainer.visibility = View.VISIBLE
                b.mainContent.visibility = View.GONE
            }
        }
    }

    private fun observeAuth() {
        lifecycleScope.launch {
            TdLibManager.authState.collect { state ->
                runOnUiThread {
                    // איפוס תצוגה
                    b.apiContainer.visibility = View.GONE
                    b.loginContainer.visibility = View.GONE
                    b.mainContent.visibility = View.GONE
                    
                    when (state) {
                        is TdApi.AuthorizationStateWaitTdlibParameters -> {
                            // טעינה... לא מציגים כלום עדיין
                        }
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
                            b.passwordLayout.visibility = View.VISIBLE // תצוגת סיסמה
                            b.tvLoginStatus.text = "Two-Step Verification"
                        }
                        is TdApi.AuthorizationStateReady -> {
                            // רק עכשיו מותר להציג את התוכן הראשי!
                            b.mainContent.visibility = View.VISIBLE
                            b.tvConnectionStatus.text = "🟢 Online"
                            b.tvConnectionStatus.setTextColor(0xFF4CAF50.toInt())
                        }
                        else -> {
                            // מצבי ביניים (logging out, closing, etc)
                        }
                    }
                }
            }
        }
        
        // האזנה להודעות
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
