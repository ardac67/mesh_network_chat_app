package com.example.grad_project2
import MessageAdapter
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.ListSessions.ListSessions
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale


class ChatActivity : AppCompatActivity() {
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageView

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private lateinit var socketConnection: SocketConnection
    private var hostIp: String? = null
    private var hostPort: Int? = null
    private var isHost: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Retrieve Intent extras
        isHost = intent.getBooleanExtra("IS_HOST", false)
        hostIp = intent.getStringExtra("HOST_IP")
        hostPort = intent.getIntExtra("HOST_PORT", -1)

        if (hostIp.isNullOrEmpty() || hostPort == null || hostPort == -1) {
            Toast.makeText(this, "Invalid host details", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)

        adapter = MessageAdapter(messages)
        messagesRecyclerView.adapter = adapter
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        val bannerTextView: TextView = findViewById(R.id.topBannerText)

        // Debug logs for isHost and hostIp values
        Log.d("ChatActivity", "isHost: $isHost")
        Log.d("ChatActivity", "hostIp: $hostIp")

        bannerTextView.text = if (isHost) {
            "Host IP: $hostIp,$hostPort".also { Log.d("ChatActivity", "Banner Text: $it") } // Log the banner text
        } else {
            "Host IP: $hostIp,$hostPort".also { Log.d("ChatActivity", "Banner Text: $it") } // Log the banner text
        }


        if (!isHost) {
            // Client logic
            val connection = ListSessions.sharedSocketConnection
            if (connection == null) {
                Toast.makeText(this, "No socket connection available.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            socketConnection = connection

            // Set message listener
            socketConnection.subscribeToSession(
                ip = hostIp!!,
                port = hostPort!!,
                onMessageReceived = { message ->
                    if (!message.isNullOrEmpty()) {
                        val json = org.json.JSONObject(message)
                        val msgText = json.getString("message")
                        val receivedTimestamp = formatTimestamp(System.currentTimeMillis())
                        val nick = json.getString("nick")
                        val ip = json.getString("ip")
                        Log.d("MessageCameOut", msgText)
                        val receivedMsg = Message(
                            text = msgText,
                            isSentByMe = false,
                            timestamp = System.currentTimeMillis(),
                            type = "string",
                            nick= nick,
                            ip = ip
                        )
                        if(!msgText.equals("Message received."))
                        {
                            Log.d("messagemal","$receivedMsg")
                            runOnUiThread {
                                messages.add(receivedMsg)
                                adapter.notifyItemInserted(messages.size - 1)
                                messagesRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        }

                    }
                },
                onSubscriptionSuccess = {
                    runOnUiThread {
                        Toast.makeText(this, "Connected to $hostIp:$hostPort", Toast.LENGTH_SHORT).show()
                    }
                },
                onSubscriptionFailed = { ex ->
                    runOnUiThread {
                        Toast.makeText(this, "Failed to connect: ${ex.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            )
        } else {
            // Host logic: Set up callback to receive messages
            ListSessions.hostMessageListener = object : OnMessageReceivedListener {
                override fun onMessageReceived(message: Message) {
                    Log.d("messagemal","$message")
                    runOnUiThread {
                        messages.add(message)
                        adapter.notifyItemInserted(messages.size - 1)
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        // Send button logic
        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val formattedTimestamp = formatTimestamp(timestamp)

                val newMessage = Message(
                    text = text,
                    isSentByMe = true,
                    timestamp = timestamp,
                    type = "string",
                    ip = "",
                    nick = getLocalIpAddress()
                )
                messages.add(newMessage)
                adapter.notifyItemInserted(messages.size - 1)
                messagesRecyclerView.scrollToPosition(messages.size - 1)
                messageEditText.text.clear()

                if (isHost) {
                    // Host: broadcast to all connected clients
                    val tcpServer = ListSessions.sharedTcpServer
                    if (tcpServer == null) {
                        Log.e("ChatActivity", "No tcpServer instance available for host.")
                    } else {
                        tcpServer.broadcastToClients(text)
                        Log.d("ChatActivity", "Broadcasted message: $text")
                    }
                } else {
                    // Client: send the message to the host via the socket connection
                    socketConnection.sendMessage(text)
                    Log.d("ChatActivity", "Sent message via socket: $text")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isHost) {
            ListSessions.hostMessageListener = null
        }
    }
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val ip = address.hostAddress
                        if (!ip.contains(":")) { // Skip IPv6
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TcpServer", "Error getting local IP address: ${e.message}")
        }
        return "0.0.0.0" // Default fallback
    }
}
