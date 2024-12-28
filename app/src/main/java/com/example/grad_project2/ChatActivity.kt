package com.example.grad_project2

import android.os.Build
import com.example.grad_project2.adapter.MessageAdapter
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.model.Message
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageView
    private lateinit var bannerTextView: TextView

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var endpointId: String
    private lateinit var peerName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Initialize UI Components
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        bannerTextView = findViewById(R.id.topBannerText)

        // Retrieve connection details from Intent
        endpointId = intent.getStringExtra("endpointId") ?: ""
        peerName = intent.getStringExtra("peerName") ?: "Unknown"

        bannerTextView.text = "Connected to $peerName"

        // Initialize RecyclerView
        adapter = MessageAdapter(messages)
        messagesRecyclerView.adapter = adapter
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        // Initialize Nearby Connections Client
        connectionsClient = Nearby.getConnectionsClient(this)

        // Send Button Listener
        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    // Send a Message via Nearby Connections
    private fun sendMessage() {
        val text = messageEditText.text.toString().trim()
        if (text.isNotEmpty()) {
            val timestamp = System.currentTimeMillis()
            val formattedTimestamp = formatTimestamp(timestamp)
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            // Create message JSON
            val messageJson = JSONObject().apply {
                put("message", text)
                put("timestamp", formattedTimestamp)
                put("nick", "You")
                put("ip", getLocalIpAddress())
                put("from",deviceName)
            }

            // Add to local RecyclerView
            val newMessage = Message(
                text = text,
                isSentByMe = true,
                timestamp = timestamp,
                type = "string",
                nick = "You",
                ip = getLocalIpAddress()
            )
            messages.add(newMessage)
            adapter.notifyItemInserted(messages.size - 1)
            messagesRecyclerView.scrollToPosition(messages.size - 1)
            messageEditText.text.clear()

            // Send payload
            val payload = Payload.fromBytes(messageJson.toString().toByteArray())
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d("ChatActivity", "Message sent successfully: $text")
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "Failed to send message: ${e.message}")
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Handle incoming messages
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val receivedData = payload.asBytes()?.let { String(it) }

            if (receivedData == null) {
                Log.e("ChatActivity", "Received null payload from $endpointId")
                return
            }

            Log.d("ChatActivity", "Received payload: $receivedData")

            try {
                val json = JSONObject(receivedData)
                val msgText = json.getString("message")
                val nick = json.getString("nick")
                val timestamp = json.getString("timestamp")
                val ip = json.getString("ip")

                val receivedMessage = Message(
                    text = msgText,
                    isSentByMe = false,
                    timestamp = System.currentTimeMillis(),
                    type = "string",
                    nick = nick,
                    ip = ip
                )

                runOnUiThread {
                    messages.add(receivedMessage)
                    adapter.notifyItemInserted(messages.size - 1)
                    messagesRecyclerView.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                Log.e("ArdaChatActivityArda", "Failed to parse incoming message: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d("ArdaChatActivityArda", "Payload transfer update: ${update.bytesTransferred}")
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (networkInterface in java.util.Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (address in java.util.Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is java.net.InetAddress) {
                        val ip = address.hostAddress
                        if (!ip.contains(":")) { // Skip IPv6
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatActivity", "Error getting local IP address: ${e.message}")
        }
        return "0.0.0.0"
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ChatActivity", "ChatActivity destroyed")
    }
}
