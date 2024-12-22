package com.example.grad_project2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.model.Message
import com.example.grad_project2.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            messages[position].type == "system" -> VIEW_TYPE_SYSTEM
            messages[position].isSentByMe -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false))
            VIEW_TYPE_RECEIVED -> ReceivedMessageViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false))
            VIEW_TYPE_SYSTEM -> SystemMessageViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_system, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
            is SystemMessageViewHolder -> holder.systemMessageTextView.text = message.text
        }
    }

    override fun getItemCount(): Int = messages.size

    // ViewHolder for sent messages
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.sentMessageTextView)
        val timestampTextView: TextView = itemView.findViewById(R.id.sentTimestampTextView)
        val headerTextView: TextView = itemView.findViewById(R.id.sentHeaderTextView)

        fun bind(message: Message) {
            messageTextView.text = message.text
            timestampTextView.text = formatTimestamp(message.timestamp)
        }
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
    // ViewHolder for received messages
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.receivedMessageTextView)
        val timestampTextView: TextView = itemView.findViewById(R.id.receivedTimestampTextView)
        val headerTextView: TextView = itemView.findViewById(R.id.receivedHeaderTextView)

        fun bind(message: Message) {
            messageTextView.text = message.text
            timestampTextView.text = formatTimestamp(message.timestamp)
            headerTextView.text = buildString {
                append("~")
                append(message.nick)
                append(" ")
                append(message.ip)
            }
        }
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val systemMessageTextView: TextView = itemView.findViewById(R.id.systemMessageTextView)
    }

}
