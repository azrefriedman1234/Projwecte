package com.pasiflonet.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pasiflonet.mobile.databinding.ItemChatBinding
import org.drinkless.tdlib.TdApi

class ChatListAdapter(
    private var chats: List<TdApi.Chat>,
    private val onClick: (TdApi.Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    fun updateList(newChats: List<TdApi.Chat>) {
        chats = newChats
        notifyDataSetChanged()
    }

    class ChatViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.binding.tvChatName.text = chat.title
        
        val lastMsg = chat.lastMessage?.content
        val preview = when(lastMsg) {
            is TdApi.MessageText -> lastMsg.text.text
            is TdApi.MessagePhoto -> "📷 Photo"
            is TdApi.MessageVideo -> "🎥 Video"
            else -> "Message"
        }
        holder.binding.tvLastMessage.text = preview

        holder.itemView.setOnClickListener { onClick(chat) }
    }

    override fun getItemCount() = chats.size
}
