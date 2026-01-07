package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pasiflonet.mobile.databinding.ActivityMainBinding
import com.pasiflonet.mobile.td.TdLibManager
import com.pasiflonet.mobile.utils.DataStoreRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.btnSendCode.setOnClickListener {
            val phone = binding.etPhone.text.toString()
            if (phone.isNotEmpty()) {
                TdLibManager.sendPhone(phone)
                binding.tvStatus.text = "Sending request for $phone..."
            }
        }

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString()
            if (code.isNotEmpty()) TdLibManager.sendCode(code)
        }

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
                observeAuthState()
            } else {
                binding.apiContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            TdLibManager.authState.collect { state ->
                runOnUiThread {
                    if (state != null) {
                        handleAuthState(state)
                    }
                }
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        // הצגת הסטטוס הנוכחי לניפוי שגיאות (Debug)
        binding.tvStatus.text = "Current State: ${state.javaClass.simpleName}"

        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.phoneLayout.visibility = View.VISIBLE
                binding.codeLayout.visibility = View.GONE
            }
            is TdApi.AuthorizationStateWaitCode -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.phoneLayout.visibility = View.GONE
                binding.codeLayout.visibility = View.VISIBLE
                binding.tvStatus.text = "Code Sent! Check your Telegram app."
            }
            is TdApi.AuthorizationStateReady -> {
                binding.loginContainer.visibility = View.GONE
                binding.rvChats.visibility = View.VISIBLE
                binding.btnSettings.visibility = View.VISIBLE
                binding.tvStatus.text = "Logged In Successfully"
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                binding.tvStatus.text = "Logging out..."
            }
            is TdApi.AuthorizationStateClosed -> {
                binding.tvStatus.text = "Connection Closed"
            }
        }
    }
}
