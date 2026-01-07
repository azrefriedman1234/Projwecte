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
import java.io.File

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
        b.btnSendCode.setOnClickListener { 
            val p = b.etPhone.text.toString(); if(p.isEmpty()) return@setOnClickListener
            b.btnSendCode.text="Sending..."; b.btnSendCode.isEnabled=false
            TdLibManager.sendPhone(p) { e -> runOnUiThread { b.btnSendCode.isEnabled=true; b.btnSendCode.text="SEND CODE"; Toast.makeText(this,e,Toast.LENGTH_LONG).show() } }
        }
        b.btnVerify.setOnClickListener { val c=b.etCode.text.toString(); if(c.isNotEmpty()) TdLibManager.sendCode(c) { e->runOnUiThread{Toast.makeText(this,e,Toast.LENGTH_LONG).show()}} }
        b.btnVerifyPassword.setOnClickListener { val pa=b.etPassword.text.toString(); TdLibManager.sendPassword(pa) { e->runOnUiThread{Toast.makeText(this,e,Toast.LENGTH_LONG).show()}} }

        adapter = ChatAdapter(emptyList()) { msg ->
            var thumbPath: String? = null
            var miniThumbData: ByteArray? = null
            var fullId = 0
            var isVideo = false
            var caption = ""

            when (msg.content) {
                is TdApi.MessagePhoto -> {
                    val c = msg.content as TdApi.MessagePhoto
                    // 1. נסיון למצוא קובץ קיים (הכי איכותי)
                    for (size in c.photo.sizes.reversed()) {
                        val path = size.photo.local.path
                        if (path.isNotEmpty() && File(path).exists()) {
                            thumbPath = path
                            break
                        }
                    }
                    // 2. שמירת ה-Mini Thumbnail לגיבוי
                    miniThumbData = c.photo.minithumbnail?.data
                    
                    // 3. מזהה להורדה ברקע
                    fullId = if (c.photo.sizes.isNotEmpty()) c.photo.sizes.last().photo.id else 0
                    caption = c.caption.text
                }
                is TdApi.MessageVideo -> {
                    val c = msg.content as TdApi.MessageVideo
                    thumbPath = c.video.thumbnail?.file?.local?.path
                    miniThumbData = c.video.minithumbnail?.data
                    fullId = c.video.video.id
                    isVideo = true
                    caption = c.caption.text
                }
                is TdApi.MessageText -> caption = (msg.content as TdApi.MessageText).text.text
            }

            if (fullId != 0) TdLibManager.downloadFile(fullId)
            
            val intent = Intent(this, DetailsActivity::class.java)
            if (thumbPath != null) intent.putExtra("THUMB_PATH", thumbPath)
            if (miniThumbData != null) intent.putExtra("MINI_THUMB", miniThumbData) // שליחת הגיבוי
            
            intent.putExtra("FILE_ID", fullId)
            intent.putExtra("IS_VIDEO", isVideo)
            intent.putExtra("CAPTION", caption)
            startActivity(intent)
        }
        
        b.rvMessages.layoutManager = LinearLayoutManager(this)
        b.rvMessages.adapter = adapter
        b.btnClearCache.setOnClickListener { lifecycleScope.launch { CacheManager.clearAppCache(this@MainActivity) } }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }
    
    private fun checkApiAndInit() { lifecycleScope.launch { val r=DataStoreRepo(this@MainActivity); val i=r.apiId.first(); val h=r.apiHash.first(); if(i!=null&&h!=null){ b.apiContainer.visibility=View.GONE; TdLibManager.init(this@MainActivity,i,h); observeAuth() } else { b.apiContainer.visibility=View.VISIBLE; b.mainContent.visibility=View.GONE } } }
    private fun observeAuth() { lifecycleScope.launch { TdLibManager.authState.collect { s -> runOnUiThread { if(s is TdApi.AuthorizationStateWaitPhoneNumber){ b.apiContainer.visibility=View.GONE; b.loginContainer.visibility=View.VISIBLE; b.phoneLayout.visibility=View.VISIBLE; b.codeLayout.visibility=View.GONE; b.passwordLayout.visibility=View.GONE; b.btnSendCode.isEnabled=true; b.btnSendCode.text="SEND CODE" } else if(s is TdApi.AuthorizationStateWaitCode){ b.loginContainer.visibility=View.VISIBLE; b.phoneLayout.visibility=View.GONE; b.codeLayout.visibility=View.VISIBLE } else if(s is TdApi.AuthorizationStateWaitPassword){ b.loginContainer.visibility=View.VISIBLE; b.codeLayout.visibility=View.GONE; b.passwordLayout.visibility=View.VISIBLE } else if(s is TdApi.AuthorizationStateReady){ b.loginContainer.visibility=View.GONE; b.mainContent.visibility=View.VISIBLE } } } }; lifecycleScope.launch { TdLibManager.currentMessages.collect { m -> runOnUiThread { adapter.updateList(m) } } } }
    private fun checkPermissions() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)) else requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)) }
}
