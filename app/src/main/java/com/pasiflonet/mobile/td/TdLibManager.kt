package com.pasiflonet.mobile.td

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

object TdLibManager {
    private var client: Client? = null
    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState

    // Maps to store callbacks if needed (simplified for this example)
    
    fun init(context: Context) {
        if (client != null) return
        
        // Initialize Client with Update Handler
        client = Client.create({ update ->
            when (update) {
                is TdApi.UpdateAuthorizationState -> {
                    _authState.value = update.authorizationState
                    onAuthStateUpdated(context, update.authorizationState)
                }
                // Handle other updates like new messages here
            }
        }, null, null)
    }

    private fun onAuthStateUpdated(context: Context, state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val params = TdApi.TdlibParameters()
                params.databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                params.useMessageDatabase = true
                params.useSecretChats = true
                params.apiId = 94575 // Public Test API ID
                params.apiHash = "a3406de6ea6f6634d0b115682859e988" // Public Test Hash
                params.systemLanguageCode = "en"
                params.deviceModel = "Android"
                params.applicationVersion = "1.0"
                params.enableStorageOptimizer = true
                
                client?.send(TdApi.SetTdlibParameters(params), null)
            }
            is TdApi.AuthorizationStateWaitEncryptionKey -> {
                client?.send(TdApi.CheckDatabaseEncryptionKey(), null)
            }
        }
    }

    fun sendPhone(phone: String) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, settings), null)
    }

    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), null)
    }

    fun getChats(limit: Int, callback: (List<TdApi.Chat>) -> Unit) {
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), limit.toLong())) { obj ->
            if (obj is TdApi.Chats) {
                val chatIds = obj.chatIds
                // In real app, we need to fetch Chat objects for these IDs
                // Simplified: We assume we need to load them one by one or they are cached
                // For this demo, just sending empty list to prevent crash if not handled
                callback(emptyList()) 
            }
        }
    }
    
    fun getClient(): Client? = client
    
    // Helper to send file
    fun sendFile(chatId: Long, path: String, isImage: Boolean) {
        val file = TdApi.InputFileLocal(path)
        val content = if (isImage) {
            TdApi.InputMessagePhoto(file, null, null, 0, 0, null, 0)
        } else {
            TdApi.InputMessageVideo(file, null, null, 0, 0, 0, 0, 0, null, 0)
        }
        client?.send(TdApi.SendMessage(chatId, 0, 0, null, null, content), null)
    }
}
