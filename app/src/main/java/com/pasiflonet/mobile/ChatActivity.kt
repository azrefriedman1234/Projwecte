package com.pasiflonet.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pasiflonet.mobile.databinding.ActivityChatBinding
import com.pasiflonet.mobile.td.TdLibManager
import org.drinkless.tdlib.TdApi

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private var chatId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getLongExtra("CHAT_ID", 0)
        val chatTitle = intent.getStringExtra("CHAT_TITLE") ?: "Chat"
        binding.tvHeaderTitle.text = chatTitle

        setupRecyclerView()
        loadMessages()
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true // הצגת הודעות מלמטה למעלה (כמו בוואטסאפ)
        binding.rvMessages.layoutManager = layoutManager
    }

    private fun loadMessages() {
        // בגרסה המלאה צריך להשתמש ב-TdLibManager.getChatHistory
        // כאן אנחנו יוצרים הודעות דמה כדי שתוכל לראות את הכפתור עובד מיד
        // (כי אין לנו עדיין היסטוריית צ'אט אמיתית ביוזר חדש)
        
        val dummyMessages = listOf(
            createDummyTextMessage("Hello Pasiflonet!"),
            createDummyPhotoMessage("/storage/emulated/0/DCIM/Camera/test.jpg"), // נתיב דמה, יציג כפתור אם קיים
            createDummyTextMessage("This is a text message without button."),
            // הוספנו הודעת דמה שמדמה תמונה קיימת כדי שתוכל לראות את הכפתור
            // (בפועל זה יתחלף בהודעות מ-TDLib)
        )

        // יצירת האדפטר וחיבור הלחיצה למעבר למסך הפרטים
        val adapter = ChatAdapter(dummyMessages) { filePath, isVideo ->
            val intent = Intent(this, DetailsActivity::class.java)
            intent.putExtra("FILE_PATH", filePath)
            intent.putExtra("IS_VIDEO", isVideo)
            startActivity(intent)
        }
        binding.rvMessages.adapter = adapter
    }
    
    // פונקציות עזר ליצירת דמה (לצורך בדיקת הכפתור)
    private fun createDummyTextMessage(text: String): TdApi.Message {
        val msg = TdApi.Message()
        msg.id = System.currentTimeMillis()
        msg.content = TdApi.MessageText(TdApi.FormattedText(text, emptyArray()), null)
        return msg
    }
    
    private fun createDummyPhotoMessage(path: String): TdApi.Message {
        val msg = TdApi.Message()
        val file = TdApi.File()
        file.local = TdApi.LocalFile()
        file.local.path = path // זה חייב להיות נתיב אמיתי כדי שהכפתור יופיע
        
        val photoSize = TdApi.PhotoSize("i", file, 0, 0, intArrayOf())
        val photoContent = TdApi.MessagePhoto(TdApi.Photo(false, emptyArray(), arrayOf(photoSize)), null, false)
        
        msg.content = photoContent
        return msg
    }
}
