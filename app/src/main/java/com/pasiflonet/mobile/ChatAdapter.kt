package com.pasiflonet.mobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pasiflonet.mobile.databinding.ItemMessageBinding
import org.drinkless.tdlib.TdApi
import java.io.File

class ChatAdapter(
    private val messages: List<TdApi.Message>,
    private val onDetailsClick: (String, Boolean) -> Unit // Callback function
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val content = message.content

        // ברירת מחדל: הסתרת תמונה וכפתור
        holder.binding.cardThumbnail.visibility = View.GONE
        holder.binding.btnProcess.visibility = View.GONE
        holder.binding.tvContent.text = "Unsupported message type"

        var filePath: String? = null
        var isVideo = false

        // בדיקה: האם זו הודעת טקסט, תמונה או וידאו?
        when (content) {
            is TdApi.MessageText -> {
                holder.binding.tvContent.text = content.text.text
                // אם אתה רוצה כפתור גם לטקסט, מחק את ה-if למטה. 
                // כרגע: מציג כפתור רק למדיה.
            }
            is TdApi.MessagePhoto -> {
                holder.binding.tvContent.text = "Photo"
                holder.binding.cardThumbnail.visibility = View.VISIBLE
                
                // שליפת הנתיב המקומי של התמונה הגדולה ביותר
                val photoSize = content.photo.sizes.lastOrNull()
                val localPath = photoSize?.photo?.local?.path
                
                if (localPath != null && File(localPath).exists()) {
                    filePath = localPath
                    holder.binding.ivThumbnail.load(File(localPath))
                } else {
                    // כאן צריך להוסיף לוגיקה להורדת הקובץ מ-TDLib אם הוא לא קיים
                    holder.binding.ivThumbnail.setBackgroundColor(0xFFCCCCCC.toInt())
                }
                isVideo = false
            }
            is TdApi.MessageVideo -> {
                holder.binding.tvContent.text = "Video (${content.video.duration}s)"
                holder.binding.cardThumbnail.visibility = View.VISIBLE
                
                val localPath = content.video.video.local?.path
                if (localPath != null && File(localPath).exists()) {
                    filePath = localPath
                    // טעינת Thumbnail של הוידאו
                    val thumbPath = content.video.thumbnail?.file?.local?.path
                    if (thumbPath != null) holder.binding.ivThumbnail.load(File(thumbPath))
                }
                isVideo = true
            }
        }

        // אם יש קובץ מדיה - הצג את כפתור העריכה!
        if (filePath != null) {
            holder.binding.btnProcess.visibility = View.VISIBLE
            holder.binding.btnProcess.setOnClickListener {
                onDetailsClick(filePath, isVideo)
            }
        }
    }

    override fun getItemCount(): Int = messages.size
}
