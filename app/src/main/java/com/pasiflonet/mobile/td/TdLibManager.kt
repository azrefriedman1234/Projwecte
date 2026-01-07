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

object TdLibManager {
    private var client: Client? = null
    // סקופ ששורד גם כשהאקטיביטי נסגר
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: Context? = null

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState

    private val _currentMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val currentMessages: StateFlow<List<TdApi.Message>> = _currentMessages
    
    // מפה למעקב אחרי קבצים שיורדים
    private val downloadingFiles = ConcurrentHashMap<Int, Boolean>()

    fun init(context: Context, apiId: Int, apiHash: String) {
        appContext = context.applicationContext // שמירת קונטקסט לשימוש ברקע
        if (client != null) return
        try { System.loadLibrary("tdjni") } catch (e: Exception) {}
        
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
            is TdApi.UpdateFile -> {
                // מעקב אחרי הורדות
                if (update.file.local.isDownloadingCompleted) {
                    downloadingFiles[update.file.id] = true
                }
            }
        }
    }

    // פונקציה חדשה: קבלת נתיב קובץ (סינכרוני - מחזיר רק אם קיים)
    suspend fun getFilePath(fileId: Int): String? {
        return suspendCancellableCoroutine { cont ->
            client?.send(TdApi.GetFile(fileId)) { obj ->
                if (obj is TdApi.File && obj.local.isDownloadingCompleted) {
                    cont.resume(obj.local.path, null)
                } else {
                    cont.resume(null, null)
                }
            }
        }
    }

    // פונקציית הקסם: עיבוד ושליחה ברקע מלא
    fun processAndSendInBackground(
        fileId: Int,
        thumbPath: String, // לגיבוי
        isVideo: Boolean,
        caption: String,
        targetUsername: String,
        rects: List<BlurRect>,
        logoUri: Uri?,
        lX: Float, lY: Float, lScale: Float
    ) {
        scope.launch {
            Log.d("BG_PROCESS", "Starting background process for file $fileId")
            
            // 1. וידוא שהקובץ המלא ירד
            var fullPath: String? = getFilePath(fileId)
            var attempts = 0
            
            // אם הקובץ לא קיים, מורידים ומחכים (עד 60 שניות)
            if (fullPath == null) {
                client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false), null)
                while (fullPath == null && attempts < 60) {
                    delay(1000)
                    fullPath = getFilePath(fileId)
                    attempts++
                    Log.d("BG_PROCESS", "Waiting for file... $attempts")
                }
            }

            // אם עדיין אין קובץ מלא, מנסים להשתמש ב-thumbnail כברירת מחדל (רק לתמונות)
            val inputPath = fullPath ?: thumbPath
            if (!File(inputPath).exists()) {
                Log.e("BG_PROCESS", "Failed to download file $fileId")
                return@launch
            }

            // 2. עיבוד (Watermark/Blur)
            val ctx = appContext ?: return@launch
            val outExtension = if (isVideo) "mp4" else "jpg"
            val outPath = File(ctx.cacheDir, "bg_sent_${System.currentTimeMillis()}.$outExtension").absolutePath

            // אם זה וידאו או שהקובץ מלא, מבצעים עיבוד
            // הערה: MediaProcessor יודע להתמודד עם הנתיבים
            MediaProcessor.processContent(
                ctx, inputPath, outPath, isVideo, 
                rects, logoUri, lX, lY, lScale
            ) { success ->
                if (success) {
                    // 3. שליחה
                    sendFinalMessage(targetUsername, caption, outPath, isVideo)
                } else {
                    Log.e("BG_PROCESS", "Processing failed")
                }
            }
        }
    }

    fun sendPhone(phone: String) = client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null), null)
    fun sendCode(code: String) = client?.send(TdApi.CheckAuthenticationCode(code), null)
    fun sendPassword(password: String) = client?.send(TdApi.CheckAuthenticationPassword(password), null)
    fun downloadFile(fileId: Int) = client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false), null)

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
