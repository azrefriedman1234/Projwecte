package com.pasiflonet.mobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pasiflonet.mobile.databinding.ItemMessageBinding
import com.pasiflonet.mobile.td.TdLibManager
import org.drinkless.tdlib.TdApi
import java.io.File

class ChatAdapter(
    private var messages: List<TdApi.Message>,
    private val onDetailsClick: (String, Boolean) -> Unit
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    fun updateList(newMessages: List<TdApi.Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    class MessageViewHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val content = message.content

        holder.binding.cardThumbnail.visibility = View.GONE
        holder.binding.btnProcess.visibility = View.GONE
        holder.binding.ivThumbnail.setImageDrawable(null)

        var filePath: String? = null
        var isVideo = false
        var fileIdToDownload: Int? = null

        when (content) {
            is TdApi.MessageText -> {
                holder.binding.tvContent.text = content.text.text
            }
            is TdApi.MessagePhoto -> {
                holder.binding.tvContent.text = ""
                holder.binding.cardThumbnail.visibility = View.VISIBLE
                
                val bigPhoto = content.photo.sizes.lastOrNull()?.photo
                if (bigPhoto != null) {
                    val path = bigPhoto.local.path
                    if (path.isNotEmpty() && File(path).exists()) {
                        // הקובץ קיים! הצג תמונה וכפתור
                        filePath = path
                        holder.binding.ivThumbnail.load(File(path))
                    } else {
                        // הקובץ לא קיים, סימן להורדה
                        holder.binding.ivThumbnail.setBackgroundColor(0xFFEEEEEE.toInt())
                        fileIdToDownload = bigPhoto.id
                    }
                }
                isVideo = false
            }
            is TdApi.MessageVideo -> {
                holder.binding.tvContent.text = "Video"
                holder.binding.cardThumbnail.visibility = View.VISIBLE
                
                val videoFile = content.video.video
                val path = videoFile.local.path
                
                if (path.isNotEmpty() && File(path).exists()) {
                    filePath = path
                    // נסה לטעון תמונה ממוזערת אם יש
                    val thumbPath = content.video.thumbnail?.file?.local?.path
                    if (thumbPath != null && File(thumbPath).exists()) {
                         holder.binding.ivThumbnail.load(File(thumbPath))
                    } else {
                         holder.binding.ivThumbnail.setBackgroundColor(0xFF000000.toInt())
                    }
                } else {
                    fileIdToDownload = videoFile.id
                }
                isVideo = true
            }
            else -> holder.binding.tvContent.text = "Message Type: ${content.javaClass.simpleName}"
        }

        // לוגיקה חכמה: כפתור עריכה או הורדה
        if (filePath != null) {
            holder.binding.btnProcess.text = "Edit & Watermark"
            holder.binding.btnProcess.visibility = View.VISIBLE
            holder.binding.btnProcess.setOnClickListener {
                onDetailsClick(filePath, isVideo)
            }
        } else if (fileIdToDownload != null) {
            // אם הקובץ חסר - הצג כפתור הורדה (או הורד אוטומטית)
            holder.binding.btnProcess.text = "Downloading..."
            holder.binding.btnProcess.visibility = View.VISIBLE
            holder.binding.btnProcess.setOnClickListener {
                 // במקרה של לחיצה ידנית (אם ההורדה האוטומטית נכשלה)
                 TdLibManager.downloadFile(fileIdToDownload)
            }
            // הערה: TdLibManager כבר מנסה להוריד אוטומטית כשההודעה נטענת
        }
    }

    override fun getItemCount(): Int = messages.size
}
