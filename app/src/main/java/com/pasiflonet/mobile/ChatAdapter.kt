package com.pasiflonet.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pasiflonet.mobile.databinding.ItemMessageRowBinding
import org.drinkless.tdlib.TdApi
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class ChatAdapter(private var messages: List<TdApi.Message>, private val onDetailsClick: (TdApi.Message) -> Unit) : RecyclerView.Adapter<ChatAdapter.RowHolder>() {
    fun updateList(newMessages: List<TdApi.Message>) { messages = newMessages; notifyDataSetChanged() }
    class RowHolder(val b: ItemMessageRowBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder = RowHolder(ItemMessageRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val msg = messages[position]
        holder.b.tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.date.toLong() * 1000))
        var text = ""; var type = "Text"
        when (msg.content) {
            is TdApi.MessageText -> { text = (msg.content as TdApi.MessageText).text.text; type = "📝" }
            is TdApi.MessagePhoto -> { text = (msg.content as TdApi.MessagePhoto).caption.text; type = "📷" }
            is TdApi.MessageVideo -> { text = (msg.content as TdApi.MessageVideo).caption.text; type = "🎥" }
        }
        holder.b.tvMsgText.text = if (text.isEmpty()) "No Caption" else text
        holder.b.tvMediaType.text = type
        holder.b.btnDetails.setOnClickListener { onDetailsClick(msg) }
    }
    override fun getItemCount() = messages.size
}
