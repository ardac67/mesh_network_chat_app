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

class ChatItemAdapter(
    private val context: Context,
    private val itemList: MutableList<ChatGlobal>,
    private val listener: OnSessionClickListener
) : RecyclerView.Adapter<ChatItemAdapter.MyViewHolder>() {

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.chatTitleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.chatSubtitleTextView)
        val connectSocketImage: ImageView = itemView.findViewById(R.id.connectSocketImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = itemList[position]
        Log.d("Adapter", "Binding peer: ${item.sessionName} at position $position")
        holder.titleTextView.text = item.sessionName ?: "Unknown Device"
        holder.descriptionTextView.text = item.ip

        holder.connectSocketImage.setOnClickListener {
            Log.d("Adapter", "Connect icon clicked for: ${item.sessionName}")
            listener.onSessionClicked(item) // Trigger the listener with the selected peer
        }
    }



    override fun getItemCount(): Int {
        Log.d("Adapter", "Item count: ${itemList.size}")
        return itemList.size
    }

}

