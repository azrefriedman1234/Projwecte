package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = ChatAdapter(emptyList()) { msg ->
            val intent = Intent(this, DetailsActivity::class.java)
            var thumbPath: String? = null; var fullId = 0; var isVideo = false; var caption = ""
            when (msg.content) {
                is TdApi.MessagePhoto -> { val c = msg.content as TdApi.MessagePhoto; thumbPath = c.photo.sizes.firstOrNull()?.photo?.local?.path; fullId = c.photo.sizes.last().photo.id; caption = c.caption.text }
                is TdApi.MessageVideo -> { val c = msg.content as TdApi.MessageVideo; thumbPath = c.video.thumbnail?.file?.local?.path; fullId = c.video.video.id; isVideo = true; caption = c.caption.text }
                is TdApi.MessageText -> caption = (msg.content as TdApi.MessageText).text.text
            }
            intent.putExtra("THUMB_PATH", thumbPath); intent.putExtra("FILE_ID", fullId); intent.putExtra("IS_VIDEO", isVideo); intent.putExtra("CAPTION", caption)
            startActivity(intent)
        }
        b.rvMessages.layoutManager = LinearLayoutManager(this); b.rvMessages.adapter = adapter

        b.btnClearCache.setOnClickListener { Toast.makeText(this, "Cleared ${CacheManager.clearAppCache(this)/1024} KB", Toast.LENGTH_SHORT).show() }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        lifecycleScope.launch {
            val repo = DataStoreRepo(this@MainActivity)
            val id = repo.apiId.first(); val hash = repo.apiHash.first()
            if (id != null && hash != null) {
                TdLibManager.init(this@MainActivity, id, hash)
                TdLibManager.authState.collect { state -> runOnUiThread { if (state is TdApi.AuthorizationStateReady) { b.tvConnectionStatus.text = "🟢 Online"; b.tvConnectionStatus.setTextColor(0xFF4CAF50.toInt()) } else b.tvConnectionStatus.text = "🔴 Connecting..." } }
                TdLibManager.currentMessages.collect { msgs -> runOnUiThread { adapter.updateList(msgs) } }
            } else b.tvConnectionStatus.text = "⚠️ No API Config"
        }
    }
}
