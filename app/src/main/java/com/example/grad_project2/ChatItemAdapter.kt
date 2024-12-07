package com.example.grad_project2

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class ChatItemAdapter(
    private val context: Context,
    private val itemList: MutableList<ChatGlobal>,
    private val socketConnection: SocketConnection,
    private val listener: OnSessionClickListener // Added listener
) : RecyclerView.Adapter<ChatItemAdapter.MyViewHolder>() {

    // ViewHolder class to hold references to each item view
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.chatTitleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.chatSubtitleTextView)
        val unreadMessages: TextView = itemView.findViewById(R.id.unreadBadgeTextView)
        val timeSend: TextView = itemView.findViewById(R.id.timeTextView)
    }

    // Inflate the item layout and create ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        Log.d("Adapter", "onCreateViewHolder called")
        return MyViewHolder(view)
    }

    // Bind data to each ViewHolder
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = itemList[position]
        Log.d("Adapter", "Binding position: $position with IP: ${item.ip}")

        // Display "IP - Port" in the title
        holder.titleTextView.text = "${item.ip} - ${item.port}"
        holder.descriptionTextView.text = ""
        holder.unreadMessages.text = item.unreadMessages.toString()
        holder.timeSend.text = item.time

        // Set background based on subscription state
        if (item.isHostMe) {
            holder.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.gradient_background))
            val onlineIndicator = holder.itemView.findViewById<View>(R.id.onlineIndicator)
            onlineIndicator.setBackground(ContextCompat.getDrawable(context, R.drawable.circle_background_green))
            val connectSocketImage = holder.itemView.findViewById<View>(R.id.connectSocketImage)
            connectSocketImage.isClickable = false
            connectSocketImage.alpha = 0.5f
        }
        if (item.isSubscribed) {
            holder.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.gradient_background))
            val onlineIndicator = holder.itemView.findViewById<View>(R.id.onlineIndicator)
            onlineIndicator.setBackground(ContextCompat.getDrawable(context, R.drawable.circle_background_green))
        } else {
            holder.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.not_clicked_session))
            val onlineIndicator = holder.itemView.findViewById<View>(R.id.onlineIndicator)
            onlineIndicator.setBackground(ContextCompat.getDrawable(context, R.drawable.circle_bacground))
        }

        // Set connectSocketImage based on subscription state
        val connectSocketImage = holder.itemView.findViewById<ImageView>(R.id.connectSocketImage)
        if (item.isSubscribed) {
            connectSocketImage.setImageResource(R.drawable.signal_disconnected) // Subscribed state
        } else {
            connectSocketImage.setImageResource(R.drawable.podcasts_connect) // Not subscribed state
        }

        // Set click listener on connectSocketImage
        connectSocketImage.setOnClickListener {
            Log.d("Adapter", "connectSocketImage clicked at position: $position")
            listener.onSessionClicked(item, socketConnection) // Delegate to the activity
        }
    }

    // Return the total number of items
    override fun getItemCount(): Int = itemList.size
}
