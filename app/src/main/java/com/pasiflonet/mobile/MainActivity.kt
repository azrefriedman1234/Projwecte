package com.pasiflonet.mobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.apiContainer.visibility = View.GONE; b.loginContainer.visibility = View.GONE; b.mainContent.visibility = View.GONE
        setupUI(); checkPermissions(); checkApiAndInit()
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

        // כפתור שליחת טלפון - עם טיפול בשגיאות
        b.btnSendCode.setOnClickListener {
            val phone = b.etPhone.text.toString()
            if (phone.isEmpty()) { Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            
            b.btnSendCode.isEnabled = false
            b.btnSendCode.text = "Sending..."
            
            TdLibManager.sendPhone(phone) { errorMsg ->
                runOnUiThread {
                    b.btnSendCode.isEnabled = true
                    b.btnSendCode.text = "SEND CODE"
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show() // הצגת השגיאה!
                }
            }
        }

        // כפתור אימות קוד - עם טיפול בשגיאות
        b.btnVerify.setOnClickListener { 
            val code = b.etCode.text.toString()
            if (code.isEmpty()) return@setOnClickListener
            
            TdLibManager.sendCode(code) { errorMsg ->
                runOnUiThread { Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show() }
            }
        }

        b.btnVerifyPassword.setOnClickListener { 
             val pass = b.etPassword.text.toString()
             TdLibManager.sendPassword(pass) { errorMsg ->
                runOnUiThread { Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show() }
            }
        }

        adapter = ChatAdapter(emptyList()) { msg ->
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

            if (fullId != 0) TdLibManager.downloadFile(fullId)
            
            val intent = Intent(this, DetailsActivity::class.java)
            if (thumbPath != null) intent.putExtra("THUMB_PATH", thumbPath)
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
            } else { b.apiContainer.visibility = View.VISIBLE; b.mainContent.visibility = View.GONE }
        }
    }
    
    private fun observeAuth() {
         lifecycleScope.launch {
            TdLibManager.authState.collect { state ->
                runOnUiThread {
                    // עדכון ה-UI לפי המצב הנוכחי
                    if (state is TdApi.AuthorizationStateWaitPhoneNumber) {
                        b.apiContainer.visibility = View.GONE
                        b.loginContainer.visibility = View.VISIBLE
                        b.phoneLayout.visibility = View.VISIBLE
                        b.codeLayout.visibility = View.GONE
                        b.passwordLayout.visibility = View.GONE
                        
                        // איפוס הכפתור למקרה שנתקע
                        b.btnSendCode.isEnabled = true
                        b.btnSendCode.text = "SEND CODE"
                    }
                    else if (state is TdApi.AuthorizationStateWaitCode) {
                        b.loginContainer.visibility = View.VISIBLE
                        b.phoneLayout.visibility = View.GONE
                        b.codeLayout.visibility = View.VISIBLE
                    }
                    else if (state is TdApi.AuthorizationStateWaitPassword) {
                        b.loginContainer.visibility = View.VISIBLE
                        b.codeLayout.visibility = View.GONE
                        b.passwordLayout.visibility = View.VISIBLE
                    }
                    else if (state is TdApi.AuthorizationStateReady) {
                        b.loginContainer.visibility = View.GONE
                        b.mainContent.visibility = View.VISIBLE
                    }
                }
            }
        }
        lifecycleScope.launch { TdLibManager.currentMessages.collect { msgs -> runOnUiThread { adapter.updateList(msgs) } } }
    }
    
    private fun checkPermissions() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
        else requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
}
