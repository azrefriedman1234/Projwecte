package com.pasiflonet.mobile.td

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

object TdLibManager {
    private var client: Client? = null
    
    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState

    private val _chatList = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chatList: StateFlow<List<TdApi.Chat>> = _chatList
    
    private val chatsMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val orderedChatIds = Collections.synchronizedList(ArrayList<Long>())

    private val _currentMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val currentMessages: StateFlow<List<TdApi.Message>> = _currentMessages

    fun init(context: Context) {
        if (client != null) return
        
        client = Client.create({ update ->
            CoroutineScope(Dispatchers.IO).launch {
                handleUpdate(context, update)
            }
        }, null, null)
    }

    private fun handleUpdate(context: Context, update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                _authState.value = update.authorizationState
                if (update.authorizationState is TdApi.AuthorizationStateWaitTdlibParameters) {
                    sendTdLibParams(context)
                }
            }
            is TdApi.UpdateNewChat -> {
                chatsMap[update.chat.id] = update.chat
            }
            is TdApi.UpdateChatPosition -> {
                if (update.position.list.constructor == TdApi.ChatListMain.CONSTRUCTOR) {
                    synchronized(orderedChatIds) {
                        orderedChatIds.remove(update.chatId)
                        if (update.position.order != 0L) {
                            // הוספה פשוטה להתחלה (בפועל נדרש מיון לפי order)
                            orderedChatIds.add(0, update.chatId) 
                        }
                    }
                    updateChatListFlow()
                }
            }
            is TdApi.UpdateNewMessage -> {
                val currentList = _currentMessages.value.toMutableList()
                if (currentList.isNotEmpty() && currentList.first().chatId == update.message.chatId) {
                    currentList.add(0, update.message)
                    _currentMessages.value = currentList
                }
            }
            is TdApi.UpdateFile -> {
                val file = update.file
                if (file.local.isDownloadingCompleted) {
                   // רענון הרשימה כדי לעדכן כפתורים
                   _currentMessages.value = _currentMessages.value.toList()
                }
            }
        }
    }
    
    private fun updateChatListFlow() {
        val list = ArrayList<TdApi.Chat>()
        synchronized(orderedChatIds) {
            for (id in orderedChatIds) {
                chatsMap[id]?.let { list.add(it) }
            }
        }
        _chatList.value = list
    }

    private fun sendTdLibParams(context: Context) {
        // תיקון קריטי: העברת הפרמטרים ישירות לקונסטרקטור במקום דרך אובייקט
        val databaseDir = File(context.filesDir, "tdlib").absolutePath
        val filesDir = File(context.filesDir, "tdlib_files").absolutePath
        
        val request = TdApi.SetTdlibParameters(
            false,               // use_test_dc
            databaseDir,         // database_directory
            filesDir,            // files_directory
            null,                // database_encryption_key (null = default)
            true,                // use_file_database
            true,                // use_chat_info_database
            true,                // use_message_database
            true,                // use_secret_chats
            94575,               // api_id
            "a3406de6ea6f6634d0b115682859e988", // api_hash
            "en",                // system_language_code
            "Pasiflonet Mobile", // device_model
            "1.0",               // system_version
            "1.0"                // application_version
        )
        
        client?.send(request, null)
    }

    fun sendPhone(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, TdApi.PhoneNumberAuthenticationSettings()), null)
    }

    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), null)
    }

    fun loadChatHistory(chatId: Long) {
        _currentMessages.value = emptyList()
        client?.send(TdApi.GetChatHistory(chatId, 0, 0, 50, false)) { result ->
            if (result is TdApi.Messages) {
                _currentMessages.value = result.messages.toList()
                result.messages.forEach { msg ->
                    downloadMediaIfNeeded(msg)
                }
            }
        }
    }
    
    fun downloadFile(fileId: Int) {
        // תיקון: הוספת כל הפרמטרים החסרים (offset=0, limit=0, sync=false)
        client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false), null)
    }
    
    private fun downloadMediaIfNeeded(msg: TdApi.Message) {
        val content = msg.content
        var file: TdApi.File? = null
        
        when(content) {
            is TdApi.MessagePhoto -> file = content.photo.sizes.lastOrNull()?.photo
            is TdApi.MessageVideo -> file = content.video.video
        }
        
        if (file != null && !file.local.isDownloadingCompleted) {
            downloadFile(file.id)
        }
    }
    
    fun getClient(): Client? = client
}
