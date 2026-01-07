package com.pasiflonet.mobile.td

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pasiflonet.mobile.utils.BlurRect
import com.pasiflonet.mobile.utils.MediaProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

object TdLibManager {
    private var client: Client? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: Context? = null

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState

    private val _currentMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val currentMessages: StateFlow<List<TdApi.Message>> = _currentMessages
    
    fun init(context: Context, apiId: Int, apiHash: String) {
        appContext = context.applicationContext
        if (client != null) return
        try { System.loadLibrary("tdjni") } catch (e: Exception) {}
        
        // יצירת הקליינט עם משתנה לטיפול בחריגות
        client = Client.create({ update ->
            scope.launch { handleUpdate(update, apiId, apiHash) }
        }, null, null)
    }

    private suspend fun handleUpdate(update: TdApi.Object, apiId: Int, apiHash: String) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                _authState.value = update.authorizationState
                when (update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        val ctx = appContext ?: return
                        val dbDir = File(ctx.filesDir, "tdlib").absolutePath
                        val filesDir = File(ctx.filesDir, "tdlib_files").absolutePath
                        val params = TdApi.SetTdlibParameters(false, dbDir, filesDir, null, true, true, true, true, apiId, apiHash, "en", "Android", "1.0", "1.0")
                        client?.send(params, null)
                    }
                    is TdApi.AuthorizationStateReady -> {
                        client?.send(TdApi.LoadChats(null, 20), null)
                    }
                }
            }
            is TdApi.UpdateNewMessage -> {
                val current = _currentMessages.value.toMutableList()
                current.add(0, update.message)
                _currentMessages.value = current
            }
        }
    }

    // פונקציות אימות משופרות עם דיווח שגיאות
    fun sendPhone(phone: String, onError: (String) -> Unit) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            if (result is TdApi.Error) onError("Phone Error: ${result.message}")
        }
    }

    fun sendCode(code: String, onError: (String) -> Unit) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) onError("Code Error: ${result.message}")
        }
    }

    fun sendPassword(password: String, onError: (String) -> Unit) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) onError("Password Error: ${result.message}")
        }
    }

    // פונקציות קבצים (ללא שינוי)
    suspend fun getFilePath(fileId: Int): String? {
        return suspendCancellableCoroutine { cont ->
            client?.send(TdApi.GetFile(fileId)) { obj ->
                if (obj is TdApi.File && obj.local.isDownloadingCompleted) cont.resume(obj.local.path)
                else cont.resume(null)
            }
        }
    }

    fun downloadFile(fileId: Int) = client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false), null)

    fun processAndSendInBackground(fileId: Int, thumbPath: String, isVideo: Boolean, caption: String, targetUsername: String, rects: List<BlurRect>, logoUri: Uri?, lX: Float, lY: Float, lScale: Float) {
        scope.launch {
            var fullPath: String? = getFilePath(fileId)
            var attempts = 0
            if (fullPath == null) {
                client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false), null)
                while (fullPath == null && attempts < 30) { delay(1000); fullPath = getFilePath(fileId); attempts++ }
            }
            val inputPath = fullPath ?: thumbPath
            if (!File(inputPath).exists()) return@launch

            val ctx = appContext ?: return@launch
            val outExtension = if (isVideo) "mp4" else "jpg"
            val outPath = File(ctx.cacheDir, "bg_sent_${System.currentTimeMillis()}.$outExtension").absolutePath
            
            MediaProcessor.processContent(ctx, inputPath, outPath, isVideo, rects, logoUri, lX, lY, lScale) { success ->
                if (success) sendFinalMessage(targetUsername, caption, outPath, isVideo)
            }
        }
    }

    fun sendFinalMessage(username: String, text: String, filePath: String?, isVideo: Boolean) {
        client?.send(TdApi.SearchPublicChat(username.replace("@", ""))) { obj ->
            if (obj is TdApi.Chat) {
                val formattedText = TdApi.FormattedText(text, emptyArray())
                val content = if (filePath != null) {
                    val file = TdApi.InputFileLocal(filePath)
                    if (isVideo) TdApi.InputMessageVideo(file, null, null, 0, intArrayOf(), 0, 0, 0, true, formattedText, false, null, false)
                    else TdApi.InputMessagePhoto(file, null, intArrayOf(), 0, 0, formattedText, false, null, false)
                } else TdApi.InputMessageText(formattedText, null, true)
                client?.send(TdApi.SendMessage(obj.id, null, null, null, null, content), null)
            }
        }
    }
}
