package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pasiflonet.mobile.databinding.ActivityMainBinding
import com.pasiflonet.mobile.databinding.ItemChatBinding
import com.pasiflonet.mobile.td.TdLibManager
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
        observeData()
    }

    private fun setupUI() {
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = chatsAdapter
        
        binding.btnSendCode.setOnClickListener {
            val phone = binding.etPhone.text.toString()
            if (phone.isNotEmpty()) TdLibManager.sendPhone(phone)
        }
        
        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString()
            if (code.isNotEmpty()) TdLibManager.sendCode(code)
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
        // לוגיקת UI של Login זהה לקודם, רק מחובר לנתונים אמיתיים
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.etPhone.visibility = View.VISIBLE
                binding.btnSendCode.visibility = View.VISIBLE
                binding.tvStatus.text = "Enter Phone Number"
            }
            is TdApi.AuthorizationStateWaitCode -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.etPhone.visibility = View.GONE
                binding.btnSendCode.visibility = View.GONE
                binding.etCode.visibility = View.VISIBLE
                binding.btnVerify.visibility = View.VISIBLE
                binding.tvStatus.text = "Enter Code"
            }
            is TdApi.AuthorizationStateReady -> {
                binding.loginContainer.visibility = View.GONE
                binding.rvChats.visibility = View.VISIBLE
                binding.btnSettings.visibility = View.VISIBLE
            }
            else -> {}
        }
    }

    // --- Inner Adapter Class ---
    inner class ChatsAdapter : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {
        private var chats = listOf<TdApi.Chat>()

        fun submitList(newChats: List<TdApi.Chat>) {
            chats = newChats
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ChatViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val chat = chats[position]
            holder.binding.tvChatTitle.text = chat.title
            holder.binding.tvInitials.text = chat.title.take(1).uppercase()
            
            // הצגת הודעה אחרונה (בפרויקט מלא צריך לפרסר את סוג ההודעה)
            holder.binding.tvLastMessage.text = "Last message ID: ${chat.lastMessage?.id ?: 0}"

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, ChatActivity::class.java)
                intent.putExtra("CHAT_ID", chat.id)
                intent.putExtra("CHAT_TITLE", chat.title)
                startActivity(intent)
            }
        }

        override fun getItemCount() = chats.size
        
        inner class ChatViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
