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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        checkApiAndInit()
    }

    private fun setupButtons() {
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
                runOnUiThread { handleAuthState(state) }
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState?) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                binding.loginContainer.visibility = View.VISIBLE
                binding.etPhone.visibility = View.VISIBLE
                binding.btnSendCode.visibility = View.VISIBLE
                binding.etCode.visibility = View.GONE
                binding.btnVerify.visibility = View.GONE
                binding.tvStatus.text = "Enter your phone number"
            }
            is TdApi.AuthorizationStateWaitCode -> {
                // כאן אנחנו חושפים את השדה של הקוד
                binding.loginContainer.visibility = View.VISIBLE
                binding.etPhone.visibility = View.GONE
                binding.btnSendCode.visibility = View.GONE
                binding.etCode.visibility = View.VISIBLE
                binding.btnVerify.visibility = View.VISIBLE
                binding.tvStatus.text = "Enter the code sent to your Telegram"
            }
            is TdApi.AuthorizationStateReady -> {
                binding.loginContainer.visibility = View.GONE
                binding.rvChats.visibility = View.VISIBLE
                binding.btnSettings.visibility = View.VISIBLE
                // מעבר למסך הגדרות או טעינת צ'אטים...
            }
        }
    }
}
