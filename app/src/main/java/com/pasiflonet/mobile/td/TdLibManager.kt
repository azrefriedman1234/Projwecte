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

object TdLibManager {
    private var client: Client? = null
    
    // מצב התחברות
    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState

    // רשימת צ'אטים חיה (מסודרת לפי Order)
    private val _chatList = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chatList: StateFlow<List<TdApi.Chat>> = _chatList
    
    // מפה פנימית לניהול מהיר של צ'אטים
    private val chatsMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val orderedChatIds = ArrayList<Long>()

    // הודעות בצ'אט הפעיל
    private val _currentMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val currentMessages: StateFlow<List<TdApi.Message>> = _currentMessages

    fun init(context: Context) {
        if (client != null) return
        
        // יצירת קליינט עם הנדלר לעדכונים בזמן אמת
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
                // אנחנו לא מוסיפים לרשימה הסופית עד שאין UpdateChatPosition כדי לשמור על סדר
            }
            is TdApi.UpdateChatPosition -> {
                if (update.position.list.constructor == TdApi.ChatListMain.CONSTRUCTOR) {
                    synchronized(orderedChatIds) {
                        orderedChatIds.remove(update.chatId)
                        if (update.position.order != 0L) {
                            // הכנסה ממוינת פשוטה (בפרויקט גדול נדרש מיון יעיל יותר)
                            orderedChatIds.add(0, update.chatId) 
                        }
                    }
                    updateChatListFlow()
                }
            }
            is TdApi.UpdateNewMessage -> {
                // הוספת הודעה חדשה בזמן אמת לרשימה אם אנחנו בצ'אט הרלוונטי
                val currentList = _currentMessages.value.toMutableList()
                if (currentList.isNotEmpty() && currentList.first().chatId == update.message.chatId) {
                    currentList.add(0, update.message) // הוספה להתחלה
                    _currentMessages.value = currentList
                }
                
                // עדכון הצ'אט ברשימה (הודעה אחרונה)
                val chat = chatsMap[update.message.chatId]
                if (chat != null) {
                    // כאן היה צריך לעדכן את ה-lastMessage של הצ'אט, אבל TDLib שולח UpdateChatLastMessage בנפרד
                }
            }
            is TdApi.UpdateFile -> {
                // עדכון קריטי! כשהורדה מסתיימת, אנחנו צריכים לרענן את המסך
                // כדי שהכפתור "עריכה" יופיע
                val file = update.file
                if (file.local.isDownloadingCompleted) {
                   // טריגר לרענון ה-UI (בצורה גסה מעדכנים את כל הרשימה כדי שהאדפטר יזהה שינוי)
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
        val params = TdApi.TdlibParameters()
        params.databaseDirectory = File(context.filesDir, "tdlib").absolutePath
        params.useMessageDatabase = true
        params.useSecretChats = true
        params.apiId = 94575 
        params.apiHash = "a3406de6ea6f6634d0b115682859e988"
        params.systemLanguageCode = "en"
        params.deviceModel = "Pasiflonet Mobile"
        params.applicationVersion = "1.0"
        
        client?.send(TdApi.SetTdlibParameters(params), null)
    }

    // --- Public Actions ---

    fun sendPhone(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, TdApi.PhoneNumberAuthenticationSettings()), null)
    }

    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), null)
    }

    fun loadChatHistory(chatId: Long) {
        // איפוס הרשימה הנוכחית
        _currentMessages.value = emptyList()
        
        // שליפת 50 הודעות אחרונות
        client?.send(TdApi.GetChatHistory(chatId, 0, 0, 50, false)) { result ->
            if (result is TdApi.Messages) {
                _currentMessages.value = result.messages.toList()
                
                // בדיקה אוטומטית: אם יש הודעות מדיה בלי קובץ - תוריד אותן!
                result.messages.forEach { msg ->
                    downloadMediaIfNeeded(msg)
                }
            }
        }
    }
    
    fun downloadFile(fileId: Int) {
        client?.send(TdApi.DownloadFile(fileId, 32), null)
    }
    
    private fun downloadMediaIfNeeded(msg: TdApi.Message) {
        val content = msg.content
        var file: TdApi.File? = null
        
        when(content) {
            is TdApi.MessagePhoto -> file = content.photo.sizes.lastOrNull()?.photo
            is TdApi.MessageVideo -> file = content.video.video
        }
        
        if (file != null && !file.local.isDownloadingCompleted) {
            // התחלת הורדה אוטומטית כדי שיהיה מוכן לעריכה
            downloadFile(file.id)
        }
    }
    
    fun getClient(): Client? = client
}
