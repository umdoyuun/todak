package com.example.todak.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2
    }

    private val messages = mutableListOf<ChatMessage>()
    private val dateFormat = SimpleDateFormat("a h:mm", Locale.KOREA)

    inner class MeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.text_message_me)
        private val textTime: TextView = itemView.findViewById(R.id.text_time_me)

        fun bind(message: ChatMessage) {
            textMessage.text = message.message
            textTime.text = dateFormat.format(Date(message.timestamp))
        }
    }

    inner class OtherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.text_message_other)
        private val textTime: TextView = itemView.findViewById(R.id.text_time_other)
        private val textName: TextView = itemView.findViewById(R.id.text_name)

        fun bind(message: ChatMessage) {
            textMessage.text = message.message
            textTime.text = dateFormat.format(Date(message.timestamp))
            textName.text = "토닥이"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ME -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_user, parent, false)
                MeViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_todak, parent, false)
                OtherViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is MeViewHolder -> holder.bind(message)
            is OtherViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].sender) {
            ChatMessage.Sender.USER -> VIEW_TYPE_ME
            ChatMessage.Sender.TODAK -> VIEW_TYPE_OTHER
        }
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun addMessages(newMessages: List<ChatMessage>) {
        val startPosition = messages.size
        messages.addAll(newMessages)
        notifyItemRangeInserted(startPosition, newMessages.size)
    }

    fun removeMessage(id: Long) {
        val index = messages.indexOfFirst { it.id == id }
        if (index != -1) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun clear() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }
}