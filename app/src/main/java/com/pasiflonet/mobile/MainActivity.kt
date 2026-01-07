package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
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

        // הגדרת הטבלה
        adapter = ChatAdapter(emptyList()) { msg ->
            // לחיצה על כפתור פרטים
            val intent = Intent(this, DetailsActivity::class.java)
            
            // חילוץ נתונים להעברה
            var thumbPath: String? = null
            var fullId = 0
            var isVideo = false
            var caption = ""

            when (msg.content) {
                is TdApi.MessagePhoto -> {
                    val c = msg.content as TdApi.MessagePhoto
                    // ננסה לקחת את התמונה הכי קטנה לטעינה מיידית
                    thumbPath = c.photo.sizes.firstOrNull()?.photo?.local?.path
                    fullId = c.photo.sizes.last().photo.id
                    caption = c.caption.text
                }
                is TdApi.MessageVideo -> {
                    val c = msg.content as TdApi.MessageVideo
                    thumbPath = c.video.thumbnail?.file?.local?.path
                    fullId = c.video.video.id
                    isVideo = true
                    caption = c.caption.text
                }
                is TdApi.MessageText -> {
                     caption = (msg.content as TdApi.MessageText).text.text
                }
            }
            
            intent.putExtra("THUMB_PATH", thumbPath)
            intent.putExtra("FILE_ID", fullId)
            intent.putExtra("IS_VIDEO", isVideo)
            intent.putExtra("CAPTION", caption)
            startActivity(intent)
        }
        
        b.rvMessages.layoutManager = LinearLayoutManager(this)
        b.rvMessages.adapter = adapter

        // כפתורים עליונים
        b.btnClearCache.setOnClickListener {
            val size = CacheManager.clearAppCache(this)
            Toast.makeText(this, "Cleared ${size/1024} KB media", Toast.LENGTH_SHORT).show()
        }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        initTelegram()
    }

    private fun initTelegram() {
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@MainActivity)
            val id = repo.apiId.first()
            val hash = repo.apiHash.first()
            
            if (id != null && hash != null) {
                TdLibManager.init(this@MainActivity, id, hash)
                
                // האזנה לסטטוס חיבור
                TdLibManager.authState.collect { state ->
                    runOnUiThread {
                        if (state is TdApi.AuthorizationStateReady) {
                            b.tvConnectionStatus.text = "🟢 Online"
                            b.tvConnectionStatus.setTextColor(0xFF4CAF50.toInt())
                            
                            // טעינת 3 הודעות אחרונות בלבד (לוגיקה בסיסית: לוקחים מהרשימה)
                            // ביישום אמיתי היינו מבקשים LoadHistory עם limit=3
                        } else {
                            b.tvConnectionStatus.text = "🔴 Connecting..."
                        }
                    }
                }
                
                // האזנה להודעות חדשות בזמן אמת
                TdLibManager.currentMessages.collect { msgs ->
                    runOnUiThread {
                        // כאן אפשר לסנן רק ל-3 האחרונות בהתחלה
                        adapter.updateList(msgs)
                    }
                }
            } else {
                b.tvConnectionStatus.text = "⚠️ No API Config"
                // פתיחת דיאלוג הגדרות או מעבר למסך הגדרות...
            }
        }
    }
}
