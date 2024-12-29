package com.example.grad_project2.fragment

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.R
import com.example.grad_project2.adapter.MessageAdapter
import com.example.grad_project2.model.Message
import com.example.grad_project2.viewmodel.SharedChatViewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatFragment : Fragment() {

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageView
    private lateinit var bannerTextView: TextView

    val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var endpointId: String
    private lateinit var peerName: String

    companion object {
        private const val ARG_ENDPOINT_ID = "endpointId"
        private const val ARG_PEER_NAME = "peerName"

        fun newInstance(endpointId: String, peerName: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ENDPOINT_ID, endpointId)
                    putString(ARG_PEER_NAME, peerName)
                }
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            endpointId = it.getString(ARG_ENDPOINT_ID) ?: ""
            peerName = it.getString(ARG_PEER_NAME) ?: "Unknown"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    private val sharedViewModel: SharedChatViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        // UI initialization
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        messageEditText = view.findViewById(R.id.messageEditText)
        sendButton = view.findViewById(R.id.sendButton)
        bannerTextView = view.findViewById(R.id.topBannerText)

        bannerTextView.text = "Connected to $peerName"

        adapter = MessageAdapter(messages)
        messagesRecyclerView.adapter = adapter
        messagesRecyclerView.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }

        connectionsClient = Nearby.getConnectionsClient(requireContext())

        // Observe incoming messages from SharedViewModel
        sharedViewModel.incomingMessage.observe(viewLifecycleOwner) { message ->
            Log.d("MessageFROM","${message.relayedFrom}, ${peerName}")
            if(message.from.equals(peerName) || message.from.equals("System")
                || (message.relayedFrom.equals(peerName) && !message.from.equals(deviceName))){
                messages.add(message)
                adapter.notifyItemInserted(messages.size - 1)
                messagesRecyclerView.scrollToPosition(messages.size - 1)
            }
        }

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    // Send a Message via Nearby Connections
    private fun sendMessage() {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val text = messageEditText.text.toString().trim()
        if (text.isNotEmpty()) {
            val timestamp = System.currentTimeMillis()
            val formattedTimestamp = formatTimestamp(timestamp)
            val id = UUID.randomUUID().toString()
            // Create message JSON
            val messageJson = JSONObject().apply {
                put("message", text)
                put("timestamp", formattedTimestamp)
                put("nick", "You")
                put("ip", getLocalIpAddress())
                put("from",deviceName)
                put("id",id)
            }

            // Add to local RecyclerView
            val newMessage = Message(
                text = text,
                isSentByMe = true,
                timestamp = timestamp,
                type = "string",
                nick = "You",
                ip = getLocalIpAddress(),
                id = id
            )
            messages.add(newMessage)
            adapter.notifyItemInserted(messages.size - 1)
            messagesRecyclerView.scrollToPosition(messages.size - 1)
            messageEditText.text.clear()

            // Send payload
            val payload = Payload.fromBytes(messageJson.toString().toByteArray())
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d("ChatFragment", "Message sent successfully: $text")
                }
                .addOnFailureListener { e ->
                    Log.e("ChatFragment", "Failed to send message: ${e.message}")
                    Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
        }
    }
    fun generateDeviceUUID(): UUID {
        val androidId =
            Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        return if (androidId != null && androidId.isNotEmpty()) {
            UUID.nameUUIDFromBytes(androidId.toByteArray(Charsets.UTF_8))
        } else {
            UUID.randomUUID()
        }
    }

    // Handle incoming messages
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val receivedData = payload.asBytes()?.let { String(it) }

            if (receivedData == null) {
                Log.e("ChatFragment", "Received null payload from $endpointId")
                return
            }

            Log.d("ChatFragment", "Received payload: $receivedData")

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

                activity?.runOnUiThread {
                    messages.add(receivedMessage)
                    adapter.notifyItemInserted(messages.size - 1)
                    messagesRecyclerView.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Failed to parse incoming message: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d("ChatFragment", "Payload transfer update: ${update.bytesTransferred}")
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
            Log.e("ChatFragment", "Error getting local IP address: ${e.message}")
        }
        return "0.0.0.0"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("ChatFragment", "ChatFragment destroyed")
    }
}
