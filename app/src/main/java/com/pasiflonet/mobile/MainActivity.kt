package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pasiflonet.mobile.databinding.ActivityMainBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChatListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. הגדרת כפתור ההגדרות - קודם כל ולפני הכל
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 2. הגדרת כפתורי לוגין
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

        // 3. הגדרת הטבלה (RecyclerView)
        adapter = ChatListAdapter(emptyList()) { chat ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("CHAT_ID", chat.id)
                putExtra("CHAT_TITLE", chat.title)
            }
            startActivity(intent)
        }
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = adapter

        // 4. התחלה
        checkApiAndInit()
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
            } else {
                binding.apiContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun observeData() {
        // האזנה לסטטוס התחברות
        lifecycleScope.launch {
            TdLibManager.authState.collect { state ->
                runOnUiThread {
                    when (state) {
                        is TdApi.AuthorizationStateWaitPhoneNumber -> {
                            binding.loginContainer.visibility = View.VISIBLE
                            binding.phoneLayout.visibility = View.VISIBLE
                            binding.codeLayout.visibility = View.GONE
                            binding.tvStatus.text = "Enter Phone Number"
                        }
                        is TdApi.AuthorizationStateWaitCode -> {
                            binding.loginContainer.visibility = View.VISIBLE
                            binding.phoneLayout.visibility = View.GONE
                            binding.codeLayout.visibility = View.VISIBLE
                            binding.tvStatus.text = "Enter Code from Telegram"
                        }
                        is TdApi.AuthorizationStateReady -> {
                            binding.loginContainer.visibility = View.GONE
                            binding.rvChats.visibility = View.VISIBLE
                            binding.btnSettings.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        // האזנה לרשימת הצ'אטים
        lifecycleScope.launch {
            TdLibManager.chatList.collect { chats ->
                runOnUiThread {
                    adapter.updateList(chats)
                }
            }
        }
    }
}
