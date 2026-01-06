package com.pasiflonet.mobile.td

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object TdLibManager {
    private var client: Client? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentApiId: Int = 0
    private var currentApiHash: String = ""

    init {
        // טעינת הספרייה של טלגרם - קריטי למניעת מסך לבן/קריסה
        try {
            System.loadLibrary("tdjni")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState

    private val _chatList = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chatList: StateFlow<List<TdApi.Chat>> = _chatList

    private val _currentMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val currentMessages: StateFlow<List<TdApi.Message>> = _currentMessages

    private val chatsMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val chatPositions = ConcurrentHashMap<Long, Long>()

    fun init(context: Context, apiId: Int, apiHash: String) {
        if (client != null) return
        currentApiId = apiId
        currentApiHash = apiHash
        
        client = Client.create({ update ->
            scope.launch { handleUpdate(context, update) }
        }, null, null)
    }

    private suspend fun handleUpdate(context: Context, update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                _authState.value = update.authorizationState
                if (update.authorizationState is TdApi.AuthorizationStateWaitTdlibParameters) {
                    sendParams(context)
                }
            }
            is TdApi.UpdateNewChat -> {
                chatsMap[update.chat.id] = update.chat
                refreshChatList()
            }
            is TdApi.UpdateChatPosition -> {
                if (update.position.list is TdApi.ChatListMain) {
                    chatPositions[update.chatId] = update.position.order
                    refreshChatList()
                }
            }
            is TdApi.UpdateNewMessage -> {
                val current = _currentMessages.value.toMutableList()
                if (current.isNotEmpty() && current.first().chatId == update.message.chatId) {
                    current.add(0, update.message)
                    _currentMessages.value = current
                }
            }
        }
    }

    private fun sendParams(context: Context) {
        val dbDir = File(context.filesDir, "tdlib").absolutePath
        val filesDir = File(context.filesDir, "tdlib_files").absolutePath
        val params = TdApi.SetTdlibParameters(false, dbDir, filesDir, null, true, true, true, true, currentApiId, currentApiHash, "en", "Android", "1.0", "1.0")
        client?.send(params, null)
    }

    private fun refreshChatList() {
        val sorted = chatsMap.values.filter { chatPositions.containsKey(it.id) }.sortedByDescending { chatPositions[it.id] }
        _chatList.value = sorted
    }

    fun sendPhone(phone: String) = client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null), null)
    fun sendCode(code: String) = client?.send(TdApi.CheckAuthenticationCode(code), null)
    
    fun loadChatHistory(chatId: Long) {
        client?.send(TdApi.GetChatHistory(chatId, 0, 0, 50, false)) { res ->
            if (res is TdApi.Messages) {
                _currentMessages.value = res.messages.toList()
            }
        }
    }

    fun sendFinalMessage(username: String, text: String, filePath: String?, isVideo: Boolean) {
        client?.send(TdApi.SearchPublicChat(username.replace("@", ""))) { obj ->
            if (obj is TdApi.Chat) {
                val formattedText = TdApi.FormattedText(text, emptyArray())
                val content = if (filePath != null) {
                    val file = TdApi.InputFileLocal(filePath)
                    if (isVideo) {
                        TdApi.InputMessageVideo(file, null, null, 0, intArrayOf(), 0, 0, 0, true, formattedText, false, null, false)
                    } else {
                        TdApi.InputMessagePhoto(file, null, intArrayOf(), 0, 0, formattedText, false, null, false)
                    }
                } else {
                    TdApi.InputMessageText(formattedText, null, true)
                }
                client?.send(TdApi.SendMessage(obj.id, null, null, null, null, content), null)
            }
        }
    }
}
