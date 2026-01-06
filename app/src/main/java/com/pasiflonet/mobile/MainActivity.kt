package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pasiflonet.mobile.databinding.ActivityMainBinding
import com.pasiflonet.mobile.databinding.ItemChatBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val chatsAdapter = ChatsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkApiAndInit()
    }

    private fun setupUI() {
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = chatsAdapter

        binding.btnSaveApi.setOnClickListener {
            val id = binding.etApiId.text.toString().toIntOrNull()
            val hash = binding.etApiHash.text.toString()
            if (id != null && hash.isNotEmpty()) {
                lifecycleScope.launch {
                    DataStoreRepo(this@MainActivity).saveApi(id, hash)
                    checkApiAndInit()
                }
            }
        }
        
        binding.btnSendCode.setOnClickListener { TdLibManager.sendPhone(binding.etPhone.text.toString()) }
        binding.btnVerify.setOnClickListener { TdLibManager.sendCode(binding.etCode.text.toString()) }
        
        // כפתור הגדרות - וידוא שהוא לחיץ תמיד כשהוא גלוי
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkApiAndInit() {
        lifecycleScope.launch {
            val repo = DataStoreRepo(this@MainActivity)
            val apiId = repo.apiId.first()
            val apiHash = repo.apiHash.first()
            if (apiId != null && apiHash != null) {
                binding.apiContainer.visibility = View.GONE
                TdLibManager.init(this@MainActivity, apiId, apiHash)
                observeData()
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            TdLibManager.authState.collect { state ->
                runOnUiThread { handleAuthState(state) }
            }
        }
        lifecycleScope.launch {
            TdLibManager.chatList.collect { chats ->
                runOnUiThread { 
                    chatsAdapter.submitList(chats)
                }
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState?) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.rvChats.visibility = View.GONE
                binding.btnSettings.visibility = View.GONE
            }
            is TdApi.AuthorizationStateReady -> {
                binding.loginContainer.visibility = View.GONE
                binding.rvChats.visibility = View.VISIBLE
                binding.btnSettings.visibility = View.VISIBLE
                binding.btnSettings.bringToFront() // הבטחת נגישות
            }
            else -> {}
        }
    }

    inner class ChatsAdapter : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {
        private var chats = listOf<TdApi.Chat>()
        fun submitList(newChats: List<TdApi.Chat>) {
            chats = newChats
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ChatViewHolder(ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount() = chats.size
        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val chat = chats[position]
            holder.binding.tvChatTitle.text = chat.title
            holder.binding.tvInitials.text = chat.title.take(1)
            holder.binding.tvLastMessage.text = chat.lastMessage?.let { "New message" } ?: "No messages"
            holder.itemView.setOnClickListener {
                startActivity(Intent(this@MainActivity, ChatActivity::class.java).apply {
                    putExtra("CHAT_ID", chat.id)
                    putExtra("CHAT_TITLE", chat.title)
                })
            }
        }
        inner class ChatViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
