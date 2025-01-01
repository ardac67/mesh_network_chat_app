package com.example.grad_project2.fragment

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.Database.ChatDatabase
import com.example.grad_project2.Database.ChatEntity
import com.example.grad_project2.R
import com.example.grad_project2.adapter.MessageAdapter
import com.example.grad_project2.model.Message
import com.example.grad_project2.viewmodel.SharedChatViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.google.GoogleEmojiProvider
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatFragment : Fragment() {

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageView
    private lateinit var bannerTextView: TextView
    private lateinit var topBannerSwitch: Switch
    private lateinit var emojiPopup: EmojiPopup
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val photoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedImageUri: Uri? = result.data?.data
                selectedImageUri?.let {
                    sendPhoto(it)
                }
            }
        }
    val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var endpointId: String
    private lateinit var peerName: String
    private val chatDao by lazy {
        ChatDatabase.getDatabase(requireContext()).chatDao()
    }

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
        EmojiManager.install(GoogleEmojiProvider())

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val attachmentButton = view.findViewById<ImageView>(R.id.attachmentButton)
        attachmentButton.setOnClickListener {
            showAttachmentOptions()
        }
        // UI initialization
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        messageEditText = view.findViewById(R.id.messageEditText)
        sendButton = view.findViewById(R.id.sendButton)
        bannerTextView = view.findViewById(R.id.topBannerText)
        topBannerSwitch = view.findViewById(R.id.topBannerSwitch)
        bannerTextView.text = "Connected to $peerName"

        adapter = MessageAdapter(messages)
        messagesRecyclerView.adapter = adapter
        messagesRecyclerView.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        val emojiButton = view.findViewById<ImageView>(R.id.emojiButton)

        // Initialize EmojiPopup
        emojiPopup = EmojiPopup.Builder.fromRootView(view)
            .setOnEmojiPopupShownListener { emojiButton.setImageResource(R.drawable.face) }
            .setOnEmojiPopupDismissListener { emojiButton.setImageResource(R.drawable.face) }
            .build(messageEditText)

        // Toggle Emoji Keyboard
        emojiButton.setOnClickListener {
            emojiPopup.toggle()
        }

        connectionsClient = Nearby.getConnectionsClient(requireContext())

        // Observe incoming messages from SharedViewModel
        sharedViewModel.incomingMessage.observe(viewLifecycleOwner) { message ->
            Log.d("MessageFROM","${message.relayedFrom}, ${peerName}")
            if(sharedViewModel.getMessagesPrivacy(peerName) == true){
                if(message.notify.equals("public")){
                    topBannerSwitch.isChecked = true
                    sharedViewModel.publicConnections.add(peerName)
                    return@observe
                }
                else if(message.notify.equals("private")){
                    topBannerSwitch.isChecked = false
                    sharedViewModel.publicConnections.remove(peerName)
                    return@observe
                }
                messages.add(message)
                adapter.notifyItemInserted(messages.size - 1)
                messagesRecyclerView.scrollToPosition(messages.size - 1)

               val messageObj = JSONObject()
                messageObj.put("to",deviceName)
                messageObj.put("from",message.from)
                messageObj.put("message",message.text)
                messageObj.put("relayedFrom",message.relayedFrom)//maybe can cause a problem
                messageObj.put("nick",message.nick)
                messageObj.put("ip",message.ip)
                messageObj.put("type",message.type)
                insertMessageToDatabase(messageObj)

            }
            else{
                if(message.from.equals(peerName) || message.from.equals("System")
                    || (message.relayedFrom.equals(peerName) && !message.from.equals(deviceName))){
                    if(message.notify.equals("public")){
                        topBannerSwitch.isChecked = true
                        return@observe
                    }
                    else if(message.notify.equals("private")){
                        topBannerSwitch.isChecked = false
                        return@observe
                    }
                    val messageObj = JSONObject()
                    messageObj.put("to",deviceName)
                    messageObj.put("from",message.from)
                    messageObj.put("message",message.text)
                    messageObj.put("relayedFrom",message.relayedFrom)
                    messageObj.put("nick",message.nick)
                    messageObj.put("ip",message.ip)
                    messageObj.put("type",message.type)
                    messages.add(message)
                    adapter.notifyItemInserted(messages.size - 1)
                    messagesRecyclerView.scrollToPosition(messages.size - 1)
                    insertMessageToDatabase(messageObj)
                }
            }
        }

        sendButton.setOnClickListener {
            sendMessage()
        }
        topBannerSwitch.setOnCheckedChangeListener { _, isChecked ->
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            if (isChecked) {
                val timestamp = System.currentTimeMillis()
                val formattedTimestamp = formatTimestamp(timestamp)
                val id = UUID.randomUUID().toString()
                // Create message JSON
                val messageJson = JSONObject().apply {
                    put("message", "public")
                    put("timestamp", formattedTimestamp)
                    put("nick", "You")
                    put("ip", getLocalIpAddress())
                    put("from",deviceName)
                    put("id",id)
                    put("notify","public")
                }

                val payload = Payload.fromBytes(messageJson.toString().toByteArray())
                connectionsClient.sendPayload(endpointId, payload)
                    .addOnSuccessListener {
                        Log.d("ChatFragment", "Message sent successfully: chat made by public")
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatFragment", "Failed to send message: ${e.message}")
                        Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                sharedViewModel.setMessagesPrivacy(peerName,"public")

            } else {
                val timestamp = System.currentTimeMillis()
                val formattedTimestamp = formatTimestamp(timestamp)
                val id = UUID.randomUUID().toString()
                // Create message JSON
                val messageJson = JSONObject().apply {
                    put("message", "private")
                    put("timestamp", formattedTimestamp)
                    put("nick", "You")
                    put("ip", getLocalIpAddress())
                    put("from",deviceName)
                    put("id",id)
                    put("notify","private")
                }

                val payload = Payload.fromBytes(messageJson.toString().toByteArray())
                connectionsClient.sendPayload(endpointId, payload)
                    .addOnSuccessListener {
                        Log.d("ChatFragment", "Message sent successfully: chat made by private")
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatFragment", "Failed to send message: ${e.message}")
                        Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                sharedViewModel.setMessagesPrivacy(peerName,"private")
            }
        }
        loadPeerChats(peerName)
    }
    private fun showAttachmentOptions() {
        val options = arrayOf("Send Photo 📸", "Send Location 📍")
        AlertDialog.Builder(requireContext())
            .setTitle("Choose an Option")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickPhoto()
                    1 -> sendLocation()
                }
            }
            .show()
    }
    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        photoPickerLauncher.launch(intent)
    }

    // Send Photo to Chat

    private fun sendPhoto(imageUri: Uri) {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val timestamp = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        try {
            // ✅ Step 1: Read the image data and encode it as Base64
            val inputStream: InputStream? = context?.contentResolver?.openInputStream(imageUri)
            val byteArrayOutputStream = ByteArrayOutputStream()
            inputStream?.copyTo(byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            // ✅ Step 2: Create JSON Payload
            val imageMessageJson = JSONObject().apply {
                put("type", "photo")
                put("message", base64Image) // Include image data directly
                put("from", deviceName)
                put("timestamp", timestamp)
                put("id", id)
                put("nick", "You")
                put("ip", getLocalIpAddress())
                put("to" , peerName)
            }
            sharedViewModel.relayedMessages.add(id)

            // ✅ Step 3: Send JSON Payload
            val payload = Payload.fromBytes(imageMessageJson.toString().toByteArray())
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d("ChatFragment", "Photo sent successfully: $id")
                }
                .addOnFailureListener { e ->
                    Log.e("ChatFragment", "Failed to send photo: ${e.message}")
                }

            // ✅ Step 4: Update Local Chat UI
            messages.add(
                Message(
                    text = base64Image,
                    isSentByMe = true,
                    timestamp = timestamp,
                    type = "photo",
                    nick = "You",
                    ip = getLocalIpAddress(),
                    id = id,
                )
            )
            adapter.notifyItemInserted(messages.size - 1)
            messagesRecyclerView.scrollToPosition(messages.size - 1)
            insertMessageToDatabase(imageMessageJson)

        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to send photo: ${e.message}")
        }
    }

    private fun sendLocation() {
        // Check for location permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, fetch location
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        Log.d("ChatFragment", "${it.latitude} ${it.longitude}")
                        val locationJson = JSONObject().apply {
                            put("type", "location")
                            put("latitude", it.latitude)
                            put("longitude", it.longitude)
                            put("from", deviceName)
                            put("timestamp", System.currentTimeMillis())
                            put("id", UUID.randomUUID().toString())
                            put("nick", "You")
                            put("ip", getLocalIpAddress())
                            put("message","My location")
                        }

                        val payload = Payload.fromBytes(locationJson.toString().toByteArray())
                        connectionsClient.sendPayload(endpointId, payload)
                            .addOnSuccessListener {
                                Log.d("ChatFragment", "Location sent successfully ")
                            }
                            .addOnFailureListener { e ->
                                Log.e("ChatFragment", "Failed to send location: ${e.message}")
                            }

                        messages.add(
                            Message(
                                text = "Location: Lat ${location.latitude}, Lng ${location.longitude}",
                                isSentByMe = true,
                                timestamp = System.currentTimeMillis(),
                                type = "location",
                                nick = "You",
                                ip = getLocalIpAddress(),
                                latitude = it.latitude,
                                longitude = it.longitude
                            )
                        )
                        adapter.notifyItemInserted(messages.size - 1)
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ChatFragment", "Failed to get location: ${e.message}")
                    Toast.makeText(context, "Failed to get location", Toast.LENGTH_SHORT).show()
                }
        }
    }
    private fun loadPeerChats(peerName: String) {
        lifecycleScope.launch {
            getChatsWithPeer(peerName) { chats ->
                //messages.clear()
                chats.forEach {
                    Log.d("DBTypeCheck", "Type: ${it.type}")
                }
                messages.addAll(chats.map {
                    Message(
                        text = it.text,
                        isSentByMe = it.sender == "${Build.MANUFACTURER} ${Build.MODEL}",
                        timestamp = it.timestamp,
                        type = if (it.type.equals("photo") ) "photo" else "string",
                        nick =  it.sender ?: it.nick ?: "Unknown",
                        ip = it.ip,
                        id = it.uuid ?: UUID.randomUUID().toString()
                    )
                })
                adapter.notifyDataSetChanged()
                messagesRecyclerView.scrollToPosition(messages.size - 1)
            }
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
                put("to",peerName)
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
            sharedViewModel.relayedMessages.add(id)
            insertMessageToDatabase(messageJson)

            // Send payload
            if(sharedViewModel.getMessagesPrivacy(peerName) == false){
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
            else{
                Log.d("Bombarding", "Icerdemisen?")
                sharedViewModel.connectedEndpoints.value?.forEach{
                        endpoints_of_others ->
                    val new_payload = Payload.fromBytes(messageJson.toString().toByteArray())
                    val checkForPublicity=sharedViewModel.mapNameEndpoint.get(endpoints_of_others)
                    if(sharedViewModel.publicConnections.contains(checkForPublicity)){
                        connectionsClient.sendPayload(endpoints_of_others, new_payload)
                            .addOnSuccessListener {
                                Log.d("Bombarding", "Message sent successfully: $text")
                            }
                            .addOnFailureListener { e ->
                                Log.e("Bombarding", "Failed to send message: ${e.message}")
                                Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                            }
                    }

                }
            }
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
    private fun insertMessageToDatabase(messageJson: JSONObject) {
        lifecycleScope.launch {
            val newChat = ChatEntity(
                text = messageJson.getString("message"),
                ip = getLocalIpAddress(),
                nick = messageJson.getString("nick"),
                to = messageJson.getString("to"),
                sender = messageJson.getString("from"),
                relayedFrom = messageJson.optString("relayedFrom", null.toString()),
                type = messageJson.optString("type", null.toString())
            )
            chatDao.insertChat(newChat)
            Log.d("ChatFragment", "Message inserted into database:")
        }
    }
    fun getChatsWithPeer(peerName: String, onResult: (List<ChatEntity>) -> Unit) {
        //clearAllMessages()
        lifecycleScope.launch {
            val chats = chatDao.getChatsWithPeer(peerName)
            onResult(chats)
        }
    }
    private fun clearAllMessages() {
        lifecycleScope.launch {
            try {
                chatDao.deleteAllChats()
                messages.clear()
                adapter.notifyDataSetChanged()
                Toast.makeText(context, "All messages have been cleared.", Toast.LENGTH_SHORT).show()
                Log.d("ChatFragment", "All messages deleted successfully")
            } catch (e: Exception) {
                Log.e("ChatFragment", "Failed to clear messages: ${e.message}")
            }
        }
    }

}
