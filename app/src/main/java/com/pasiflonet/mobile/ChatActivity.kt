package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pasiflonet.mobile.databinding.ActivityChatBinding
import com.pasiflonet.mobile.td.TdLibManager
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private var chatId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getLongExtra("CHAT_ID", 0)
        binding.tvChatTitle.text = intent.getStringExtra("CHAT_TITLE") ?: "Chat"

        adapter = ChatAdapter(emptyList()) { msg ->
            // המרה בטוחה של נתונים לפני הכנסה ל-Intent
            val intent = Intent(this, DetailsActivity::class.java)
            
            var thumbPath = ""
            var fileId = 0
            var isVideo = false
            var caption = ""

            when (msg.content) {
                is TdApi.MessagePhoto -> {
                    val c = msg.content as TdApi.MessagePhoto
                    thumbPath = c.photo.sizes.firstOrNull()?.photo?.local?.path ?: ""
                    fileId = if (c.photo.sizes.isNotEmpty()) c.photo.sizes.last().photo.id else 0
                    caption = c.caption.text
                }
                is TdApi.MessageVideo -> {
                    val c = msg.content as TdApi.MessageVideo
                    thumbPath = c.video.thumbnail?.file?.local?.path ?: ""
                    fileId = c.video.video.id
                    isVideo = true
                    caption = c.caption.text
                }
                is TdApi.MessageText -> {
                    caption = (msg.content as TdApi.MessageText).text.text
                }
            }
            
            // שימוש ב-Strings בלבד היכן שאפשר למניעת בלבול
            if (thumbPath.isNotEmpty()) intent.putExtra("THUMB_PATH", thumbPath)
            intent.putExtra("FILE_ID", fileId)
            intent.putExtra("IS_VIDEO", isVideo)
            intent.putExtra("CAPTION", caption)
            
            startActivity(intent)
        }

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true; reverseLayout = true }
        binding.rvMessages.adapter = adapter

        lifecycleScope.launch {
            TdLibManager.currentMessages.collect { msgs ->
                runOnUiThread { adapter.updateList(msgs) }
            }
        }
        TdLibManager.loadChatHistory(chatId)
    }
}
