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
    private val onDetailsClick: (String, Boolean, String) -> Unit
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

        var filePath: String? = null
        var isVideo = false
        var fileIdToDownload: Int? = null
        var originalText = ""

        when (content) {
            is TdApi.MessageText -> {
                originalText = content.text.text
                holder.binding.tvContent.text = originalText
            }
            is TdApi.MessagePhoto -> {
                originalText = content.caption.text
                holder.binding.tvContent.text = if (originalText.isEmpty()) "Photo" else originalText
                holder.binding.cardThumbnail.visibility = View.VISIBLE
                val photo = content.photo.sizes.lastOrNull()?.photo
                if (photo != null) {
                    if (photo.local.isDownloadingCompleted) {
                        filePath = photo.local.path
                        holder.binding.ivThumbnail.load(File(filePath))
                    } else {
                        fileIdToDownload = photo.id
                    }
                }
            }
            is TdApi.MessageVideo -> {
                originalText = content.caption.text
                holder.binding.tvContent.text = if (originalText.isEmpty()) "Video" else originalText
                holder.binding.cardThumbnail.visibility = View.VISIBLE
                val video = content.video.video
                if (video.local.isDownloadingCompleted) {
                    filePath = video.local.path
                    val thumb = content.video.thumbnail?.file?.local?.path
                    if (thumb != null) holder.binding.ivThumbnail.load(File(thumb))
                } else {
                    fileIdToDownload = video.id
                }
                isVideo = true
            }
        }

        if (filePath != null) {
            holder.binding.btnProcess.visibility = View.VISIBLE
            holder.binding.btnProcess.text = "Edit Media"
            holder.binding.btnProcess.setOnClickListener {
                onDetailsClick(filePath, isVideo, originalText)
            }
        } else if (fileIdToDownload != null) {
            holder.binding.btnProcess.visibility = View.VISIBLE
            holder.binding.btnProcess.text = "Downloading..."
            holder.binding.btnProcess.setOnClickListener {
                TdLibManager.downloadFile(fileIdToDownload)
            }
        }
    }

    override fun getItemCount() = messages.size
}
