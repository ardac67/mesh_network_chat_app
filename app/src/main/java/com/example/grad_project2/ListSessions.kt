package com.example.grad_project2

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class ListSessions : AppCompatActivity(), OnSessionClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatItemAdapter
    private lateinit var progressBar: ProgressBar
    private val items = mutableListOf<ChatGlobal>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_sessions)

        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val connection = SocketConnection(lifecycleScope)

        // Initialize the adapter **before** setting it to RecyclerView
        adapter = ChatItemAdapter(this, items, connection, this)
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        // Start listening for broadcasts
        connection.listenForBroadcasts { ip, ports, senderIp ->
            // Check for duplicates
            val existingPorts = items.map { it.port }.toSet()
            val newItems = ports.mapNotNull { port ->
                if (!existingPorts.contains(port)) {
                    ChatGlobal(
                        ip = ip,
                        port = port.toInt()
                    )
                } else {
                    null // Skip if already exists
                }
            }

            if (newItems.isNotEmpty()) {
                items.addAll(newItems)
                adapter.notifyDataSetChanged()
                Log.d("ListSessions", "Added ${newItems.size} new items")
            }

            // Update UI on the main thread
            runOnUiThread {
                if (progressBar.visibility == View.VISIBLE) {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onSessionClicked(item: ChatGlobal,socketConnection: SocketConnection) {
        if (item.isSubscribed) {
            // Unsubscribe logic
            item.isSubscribed = false
            item.subscriptionJob?.cancel()
            item.subscriptionJob = null
            adapter.notifyItemChanged(items.indexOf(item))
            Toast.makeText(this, "Unsubscribed from ${item.ip}", Toast.LENGTH_SHORT).show()
            Log.d("ListSessions", "Unsubscribed from ${item.ip}")
        } else {
            // Subscribe logic
            socketConnection.subscribeToSession(
                ip = item.ip,
                port = item.port,
                connectTimeoutMillis = 5000,
                onMessageReceived = { message ->
                    Log.d("SessionMessage", "Received from ${item.ip}: $message")
                    runOnUiThread {
                        item.unreadMessages++
                        item.time = JSONObject(message).optString("timestamp", "N/A")
                        adapter.notifyItemChanged(items.indexOf(item))
                        Toast.makeText(this, "Message from ${item.ip}: $message", Toast.LENGTH_SHORT).show()
                        Log.d("ListSessions", "Updated unreadMessages for ${item.ip}")
                    }
                },
                onSubscriptionSuccess = {
                    runOnUiThread {
                        item.isSubscribed = true
                        adapter.notifyItemChanged(items.indexOf(item))
                        Toast.makeText(this, "Subscribed to ${item.ip}", Toast.LENGTH_SHORT).show()
                        Log.d("ListSessions", "Subscribed to ${item.ip}")
                    }
                },
                onSubscriptionFailed = { exception ->
                    runOnUiThread {
                        Toast.makeText(this, "Failed to subscribe to ${item.ip}: ${exception.message}", Toast.LENGTH_SHORT).show()
                        Log.e("ListSessions", "Failed to subscribe to ${item.ip}: ${exception.message}")
                    }
                }
            )?.let { job ->
                // Store the subscription job
                item.subscriptionJob = job
                Log.d("ListSessions", "Stored subscription job for ${item.ip}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all active subscriptions
        items.forEach { session ->
            session.subscriptionJob?.cancel()
        }
    }
}
