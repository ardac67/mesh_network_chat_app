package com.example.grad_project2.adapter

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.grad_project2.model.Message
import com.example.grad_project2.R
import org.w3c.dom.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SENT_IMAGE = 3
        private const val VIEW_TYPE_RECEIVED_IMAGE = 4
        private const val VIEW_TYPE_SYSTEM = 5
        private const val VIEW_TYPE_SENT_LOCATION = 6
        private const val VIEW_TYPE_RECEIVED_LOCATION = 7
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        Log.d("BindTypeWhat","${message.isSentByMe}")
        Log.d("BindTypeWhat", message.type)
        return when {
            message.type == "system" -> VIEW_TYPE_SYSTEM
            message.type == "photo" && message.isSentByMe -> VIEW_TYPE_SENT_IMAGE
            message.type == "photo" -> VIEW_TYPE_RECEIVED_IMAGE
            message.type == "location" && message.isSentByMe -> VIEW_TYPE_SENT_LOCATION
            message.type == "location" -> VIEW_TYPE_RECEIVED_LOCATION
            message.isSentByMe -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d("ReceivedImageViewHolder", viewType.toString())
        return when (viewType) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
            )
            VIEW_TYPE_RECEIVED -> ReceivedMessageViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
            )
            VIEW_TYPE_SENT_IMAGE -> SentImageViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_image_sent, parent, false)
            )
            VIEW_TYPE_RECEIVED_IMAGE -> ReceivedImageViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_image_received, parent, false)
            )
            VIEW_TYPE_SYSTEM -> SystemMessageViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_system, parent, false)
            )
            VIEW_TYPE_SENT_LOCATION -> SentLocationViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_location_sent, parent, false)
            )
            VIEW_TYPE_RECEIVED_LOCATION -> ReceivedLocationViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_location_received, parent, false)
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
            is SystemMessageViewHolder -> holder.systemMessageTextView.text = message.text
            is SentImageViewHolder -> holder.bind(message)
            is ReceivedImageViewHolder -> holder.bind(message)
            is SentLocationViewHolder -> holder.bind(message)
            is ReceivedLocationViewHolder -> holder.bind(message)

        }
    }
    class SentImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.sentImageView)
        private val textView: TextView = itemView.findViewById(R.id.sentTimestampTextView)
        fun bind(message: Message) {
            if (message.text.isNotEmpty()) {
                val base64Image = message.text
                val delayMs = message.delay ?: 0L
                textView.text = message.nick + " " + formatTimestamp(message.timestamp)
                // ✅ Decode Base64 into Bitmap
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                Log.e("SentImageViewHolder", "Binding")
                Glide.with(itemView.context)
                    .load(bitmap) // Ensure this URI is valid

                    .into(imageView)
            } else {
                Log.e("SentImageViewHolder", "Empty URI for image message")
            }
        }
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    // 🖼️ Received Image ViewHolder
    class ReceivedImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.receivedImageView)
        private val textView: TextView = itemView.findViewById(R.id.receivedTimestampTextView)
        fun bind(message: Message) {
            val base64Image = message.text
            // ✅ Decode Base64 into Bitmap
            val delayMs = message.delay ?: 0L
            textView.text = message.nick + " " + formatTimestamp(message.timestamp) + " " + delayMs
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            Log.d("ReceivedImageViewHolder", "Binding image with URI: ${message.text}")
            if (message.text.isNotEmpty()) {
                Log.e("SentImageViewHolder", "Binding")
                Glide.with(itemView.context)
                    .load(bitmap) // Ensure this URI is valid
                    .into(imageView)
            } else {
                Log.e("SentImageViewHolder", "Empty URI for image message")
            }
        }
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
    class SentLocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.text)
        private val locationTextView: TextView = itemView.findViewById(R.id.sentLocationOverlay)

        @SuppressLint("SetTextI18n")
        fun bind(message: Message) {
            Log.d("ZARTINGEN", "UUUUUU")
            locationTextView.text = "📍 Lat: ${message.latitude}, Lng: ${message.longitude}"
            text.text = message.nick + " " + formatTimestamp(message.timestamp)
            itemView.setOnClickListener {
                val uri = Uri.parse("geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                itemView.context.startActivity(intent)
            }
        }
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }


    class ReceivedLocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.text)
        private val locationImageView: ImageView = itemView.findViewById(R.id.receivedLocationPlaceholder)
        private val locationTextView: TextView = itemView.findViewById(R.id.receivedLocationOverlay)

        @SuppressLint("SetTextI18n")
        fun bind(message: Message) {
            val delayMs = message.delay ?: 0L
            locationTextView.text = "📍 Lat: ${message.latitude}, Lng: ${message.longitude}"
            text.text = "From: " + message.nick + " " + formatTimestamp(message.timestamp) + " Delay:" + delayMs
            itemView.setOnClickListener {
                val uri = Uri.parse("geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                itemView.context.startActivity(intent)
            }
        }
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }



    override fun getItemCount(): Int = messages.size

    // ViewHolder for sent messages
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.sentMessageTextView)
        val timestampTextView: TextView = itemView.findViewById(R.id.sentTimestampTextView)
        val headerTextView: TextView = itemView.findViewById(R.id.sentHeaderTextView)

        fun bind(message: Message) {
            headerTextView.text = buildString {
                append("~")
                append(message.nick)
            }
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

        @SuppressLint("SetTextI18n")
        fun bind(message: Message) {

            val delayMs = message.delay ?: 0L
            //Log.d("Send Time vs CurrentTime", "sendTime: $sendTime, currentTime: ${System.currentTimeMillis()}")
            messageTextView.text = message.text
            timestampTextView.text = "${formatTimestamp(message.timestamp)} Delay: $delayMs"
            headerTextView.text = buildString {
                append("~")
                append(message.nick)
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
