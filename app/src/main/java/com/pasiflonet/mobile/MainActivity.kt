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
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        observeAuth()
    }

    private fun setupButtons() {
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

    private fun observeAuth() {
        lifecycleScope.launch {
            TdLibManager.authState.collect { state ->
                runOnUiThread { handleState(state) }
            }
        }
    }

    private fun handleState(state: TdApi.AuthorizationState?) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.etPhone.visibility = View.VISIBLE
                binding.btnSendCode.visibility = View.VISIBLE
                binding.tvStatus.text = "Waiting for Phone Number..."
            }
            is TdApi.AuthorizationStateWaitCode -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.etPhone.visibility = View.GONE
                binding.btnSendCode.visibility = View.GONE
                binding.etCode.visibility = View.VISIBLE
                binding.btnVerify.visibility = View.VISIBLE
                binding.tvStatus.text = "Enter Code sent to Telegram"
            }
            is TdApi.AuthorizationStateReady -> {
                binding.loginContainer.visibility = View.GONE
                binding.rvChats.visibility = View.VISIBLE
                binding.btnSettings.visibility = View.VISIBLE
                loadChats()
            }
            else -> {
                 binding.tvStatus.text = "Status: ${state?.javaClass?.simpleName}"
            }
        }
    }

    private fun loadChats() {
        // Here we would populate the RecyclerView
        // For now, let's open a dummy chat item if clicked or just toast
        Toast.makeText(this, "Logged In! Chats would appear here.", Toast.LENGTH_SHORT).show()
    }
}
