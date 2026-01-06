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
    private lateinit var adapter: ChatAdapter
    private var chatId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getLongExtra("CHAT_ID", 0)
        val chatTitle = intent.getStringExtra("CHAT_TITLE") ?: "Chat"
        
        binding.tvChatTitle.text = chatTitle

        // תיקון: הוספת הפרמטר השלישי (text) ללמבדה
        adapter = ChatAdapter(emptyList()) { filePath, isVideo, originalText ->
            val intent = Intent(this, DetailsActivity::class.java).apply {
                putExtra("FILE_PATH", filePath)
                putExtra("IS_VIDEO", isVideo)
                putExtra("MESSAGE_TEXT", originalText) // העברת הטקסט למסך העריכה
            }
            startActivity(intent)
        }

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // הודעות חדשות למטה
        }
        binding.rvMessages.adapter = adapter

        observeMessages()
        TdLibManager.loadChatHistory(chatId)
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            TdLibManager.currentMessages.collect { messages ->
                runOnUiThread {
                    adapter.updateList(messages)
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.smoothScrollToPosition(0)
                    }
                }
            }
        }
    }
}
