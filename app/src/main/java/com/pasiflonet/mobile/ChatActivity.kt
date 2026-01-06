package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pasiflonet.mobile.databinding.ActivityChatBinding
import com.pasiflonet.mobile.td.TdLibManager
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private var chatId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getLongExtra("CHAT_ID", 0)
        binding.tvHeaderTitle.text = intent.getStringExtra("CHAT_TITLE") ?: "Chat"

        val adapter = ChatAdapter(emptyList()) { filePath, isVideo ->
            val intent = Intent(this, DetailsActivity::class.java)
            intent.putExtra("FILE_PATH", filePath)
            intent.putExtra("IS_VIDEO", isVideo)
            startActivity(intent)
        }

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply { 
            stackFromEnd = true 
            reverseLayout = true // TDLib נותן הודעות מהחדש לישן
        }
        binding.rvMessages.adapter = adapter

        // טעינת היסטוריה אמיתית
        if (chatId != 0L) {
            TdLibManager.loadChatHistory(chatId)
        }

        // האזנה להודעות חדשות או עדכוני קבצים
        lifecycleScope.launch {
            TdLibManager.currentMessages.collect { messages ->
                runOnUiThread {
                    adapter.updateList(messages)
                }
            }
        }
    }
}
