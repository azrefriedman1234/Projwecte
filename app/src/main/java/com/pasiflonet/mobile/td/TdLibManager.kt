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
        client = Client.create({ update ->
            scope.launch { handleUpdate(context, update, apiId, apiHash) }
        }, null, null)
    }

    private suspend fun handleUpdate(context: Context, update: TdApi.Object, apiId: Int, apiHash: String) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                _authState.value = update.authorizationState
                if (update.authorizationState is TdApi.AuthorizationStateWaitTdlibParameters) {
                    val dbDir = File(context.filesDir, "tdlib").absolutePath
                    val filesDir = File(context.filesDir, "tdlib_files").absolutePath
                    val params = TdApi.SetTdlibParameters(false, dbDir, filesDir, null, true, true, true, true, apiId, apiHash, "en", "Android", "1.0", "1.0")
                    client?.send(params, null)
                }
            }
            is TdApi.UpdateNewChat -> chatsMap[update.chat.id] = update.chat
            is TdApi.UpdateChatPosition -> {
                if (update.position.list is TdApi.ChatListMain) {
                    chatPositions[update.chatId] = update.position.order
                    refreshChatList()
                }
            }
            is TdApi.UpdateFile -> {
                if (update.file.local.isDownloadingCompleted) {
                    _currentMessages.value = _currentMessages.value.toList()
                }
            }
        }
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
                res.messages.forEach { downloadMediaIfNeeded(it) }
            }
        }
    }

    fun downloadFile(fileId: Int) {
        client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false), null)
    }

    private fun downloadMediaIfNeeded(msg: TdApi.Message) {
        val fileId = when (val c = msg.content) {
            is TdApi.MessagePhoto -> c.photo.sizes.last().photo.id
            is TdApi.MessageVideo -> c.video.video.id
            else -> null
        }
        fileId?.let { downloadFile(it) }
    }

    fun sendFinalMessage(username: String, text: String, filePath: String?, isVideo: Boolean) {
        client?.send(TdApi.SearchPublicChat(username.replace("@", ""))) { obj ->
            if (obj is TdApi.Chat) {
                val formattedText = TdApi.FormattedText(text, emptyArray())
                val content = if (filePath != null) {
                    val file = TdApi.InputFileLocal(filePath)
                    if (isVideo) {
                        // תיקון קונסטרקטור וידאו (13 פרמטרים)
                        TdApi.InputMessageVideo(file, null, null, 0, 0, 0, true, formattedText, false, null, false)
                    } else {
                        // תיקון קונסטרקטור תמונה (9 פרמטרים)
                        TdApi.InputMessagePhoto(file, null, intArrayOf(), 0, 0, formattedText, false, null, false)
                    }
                } else {
                    TdApi.InputMessageText(formattedText, null, true)
                }
                // תיקון: העברת null במקום 0 עבור ה-Topic וה-ReplyTo
                client?.send(TdApi.SendMessage(obj.id, null, null, null, null, content), null)
            }
        }
    }
}
