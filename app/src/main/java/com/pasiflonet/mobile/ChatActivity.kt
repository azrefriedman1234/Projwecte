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
    private lateinit var chatAdapter: ChatAdapter
    private var chatId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getLongExtra("CHAT_ID", 0)
        val chatTitle = intent.getStringExtra("CHAT_TITLE") ?: "Chat"
        
        // עכשיו tvChatTitle קיים ב-XML
        binding.tvChatTitle.text = chatTitle

        chatAdapter = ChatAdapter(emptyList()) { filePath, isVideo, originalText ->
            val intent = Intent(this, DetailsActivity::class.java).apply {
                putExtra("FILE_PATH", filePath)
                putExtra("IS_VIDEO", isVideo)
                putExtra("MESSAGE_TEXT", originalText)
            }
            startActivity(intent)
        }

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
            reverseLayout = true // הודעות חדשות למטה (כמו בטלגרם)
        }
        binding.rvMessages.adapter = chatAdapter

        observeMessages()
        TdLibManager.loadChatHistory(chatId)
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            TdLibManager.currentMessages.collect { messages ->
                runOnUiThread {
                    chatAdapter.updateList(messages)
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(0)
                    }
                }
            }
        }
    }
}
