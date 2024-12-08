package com.example.grad_project2
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.ListSessions.ListSessions
import com.example.grad_project2.ListSessions.ListSessions.isHost

class ChatActivity() : AppCompatActivity() {
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageView

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private lateinit var socketConnection: SocketConnection
    private var hostIp: String? = null
    private var hostPort: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

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

        // Assuming you have a global reference or a way to get the same socketConnection
        // instance used by ListSessions. For demonstration, assume it's a singleton or stored in an Application class.
        if(!isHost){
            val connection = ListSessions.sharedSocketConnection
            if (connection == null) {
                Toast.makeText(this, "No socket connection available.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            socketConnection = connection

            // Set message listener
            // This callback triggers whenever a new message arrives from the server
            // parse it and show in UI
            socketConnection.subscribeToSession(
                ip = hostIp!!,
                port = hostPort!!,
                onMessageReceived = { message ->
                    if (message.isNullOrEmpty()) {
                        return@subscribeToSession
                    }
                    val json = org.json.JSONObject(message)
                    val msgText = json.getString("message")
                    Log.d("MessageCameOut",msgText)
                    val receivedMsg = Message(
                        text = msgText,
                        isSentByMe = false,
                        timestamp = System.currentTimeMillis(),
                        type = "string"
                    )
                    runOnUiThread {
                        messages.add(receivedMsg)
                        adapter.notifyItemInserted(messages.size - 1)
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
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
        }
        // ChatActivity.kt (Excerpt)
        sendButton.setOnClickListener {
            val text = messageEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                val newMessage = Message(
                    text = text,
                    isSentByMe = true,
                    timestamp = System.currentTimeMillis(),
                    type = "string"
                )
                messages.add(newMessage)
                adapter.notifyItemInserted(messages.size - 1)
                messagesRecyclerView.scrollToPosition(messages.size - 1)
                messageEditText.text.clear()

                if (ListSessions.isHost) {
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
}
