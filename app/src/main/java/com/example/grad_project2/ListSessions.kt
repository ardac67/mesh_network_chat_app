// ListSessions.kt (Updated Excerpt)
package com.example.grad_project2

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.ListSessions.ListSessions.sharedSocketConnection
import com.example.grad_project2.ListSessions.ListSessions.username
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID



class ListSessions : AppCompatActivity() {

    object ListSessions {
        var sharedSocketConnection: SocketConnection? = null
        var sharedTcpServer: TcpServer? = null

        // Callback for the host to receive messages
        var hostMessageListener: OnMessageReceivedListener? = null
        var username:String = ""
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatItemAdapter
    private lateinit var progressBar: ProgressBar
    private val items = mutableListOf<ChatGlobal>()
    private lateinit var localIPAddress: String
    private lateinit var deviceId: String
    private lateinit var sessionManager: SessionManager
    private lateinit var socketConnection: SocketConnection
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_sessions)
        val editIcon = findViewById<View>(R.id.editIcon)
        editIcon.setOnClickListener {
            showSettingsModal()
        }
        userPreferences = UserPreferences(this)
        deviceId = getDeviceIdm()
        Log.d("ListSessionsActivity", "Device ID: $deviceId")

        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        socketConnection = SocketConnection(lifecycleScope)
        localIPAddress = getLocalIpAddress(this)
        Log.d("ListSessions", "Local IP Address: $localIPAddress")

        sessionManager = SessionManager(scope = lifecycleScope, ipAddress = localIPAddress)
        adapter = ChatItemAdapter(this, items, socketConnection, object : OnSessionClickListener {
            override fun onSessionClicked(item: ChatGlobal, socketConnection: SocketConnection) {
                if (item.isHostMe) {
                    // Host logic: Start ChatActivity as host
                    val intent = Intent(this@ListSessions, ChatActivity::class.java)
                    intent.putExtra("IS_HOST", true)
                    intent.putExtra("HOST_IP", item.ip)
                    intent.putExtra("HOST_PORT", item.port)
                    startActivity(intent)
                } else {
                    if (item.isSubscribed) {
                        // Unsubscribe logic
                        item.isSubscribed = false
                        item.subscriptionJob?.cancel()
                        item.subscriptionJob = null
                        adapter.notifyItemChanged(items.indexOf(item))
                        Toast.makeText(this@ListSessions, "Unsubscribed from ${item.ip}:${item.port}", Toast.LENGTH_SHORT).show()
                    } else {
                        // Subscribe logic
                        socketConnection.subscribeToSession(
                            ip = item.ip,
                            port = item.port,
                            connectTimeoutMillis = 5000,
                            onMessageReceived = { message ->
                                if (!message.isNullOrEmpty()) {
                                    val sessionIndex = items.indexOfFirst { it.ip == item.ip && it.port == item.port }
                                    val json = JSONObject(message)
                                    val parsed = json.getString("message")
                                    //val time = json.getString("time")
                                    if (sessionIndex != -1) {
                                        runOnUiThread {
                                            items[sessionIndex].lastMessage = parsed // Update the last message
                                            items[sessionIndex].time = formatTimestamp(System.currentTimeMillis())
                                            adapter.notifyItemChanged(sessionIndex) // Notify the adapter
                                        }
                                    }
                                }
                            },
                            onSubscriptionSuccess = {
                                runOnUiThread {
                                    item.isSubscribed = true
                                    adapter.notifyItemChanged(items.indexOf(item))
                                    Toast.makeText(this@ListSessions, "Subscribed to ${item.ip}:${item.port}", Toast.LENGTH_SHORT).show()

                                    // Store the current socketConnection in companion
                                    sharedSocketConnection = socketConnection

                                    // Start ChatActivity as client
                                    val intent = Intent(this@ListSessions, ChatActivity::class.java)
                                    intent.putExtra("IS_HOST", false)
                                    intent.putExtra("HOST_IP", item.ip)
                                    intent.putExtra("HOST_PORT", item.port)
                                    startActivity(intent)
                                }
                            },
                            onSubscriptionFailed = { exception ->
                                runOnUiThread {
                                    Toast.makeText(this@ListSessions, "Failed to subscribe: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )?.let { job ->
                            item.subscriptionJob = job
                        }
                    }
                }
            }
        })
        recyclerView.adapter = adapter

        progressBar.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        val createSessionButton = findViewById<FrameLayout>(R.id.createSession)
        createSessionButton.setOnClickListener {
            openCreateSessionModal()
        }
    }

    private fun openCreateSessionModal() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_create_session)

        val createSessionButton = dialog.findViewById<Button>(R.id.createSessionButton)
        val connectClientButton = dialog.findViewById<Button>(R.id.joinSession)
        val ipEditText = dialog.findViewById<TextInputEditText>(R.id.ipEditText)
        val portEditText = dialog.findViewById<TextInputEditText>(R.id.portEditText)
        val sessionName = dialog.findViewById<TextInputEditText>(R.id.sessionNameEditText)
        // Pre-fill IP with host's local IP
        ipEditText.setText(localIPAddress)
        ipEditText.isEnabled = true
        ipEditText.isFocusable = true

        createSessionButton.setOnClickListener {
            val ip = localIPAddress
            val portStr = portEditText.text.toString().trim()

            if (portStr.isEmpty()) {
                Toast.makeText(this, "Please enter a Port.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val port = portStr.toIntOrNull()
            if (port == null || port !in 1..65535) {
                Toast.makeText(this, "Invalid Port Number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Define a message listener for the host
            val messageListener = object : OnMessageReceivedListener {
                override fun onMessageReceived(message: Message) {
                    // Forward the message to the host's ChatActivity
                    ListSessions.hostMessageListener?.onMessageReceived(message)
                }
            }

            sessionManager.createSession(port, messageListener)

            val newSession = ChatGlobal(ip = ip, port = port, isHostMe = true, sessionName = sessionName.text.toString())
            items.add(newSession)
            adapter.notifyItemInserted(items.size - 1)

            Toast.makeText(this, "Server started on $ip:$port", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        connectClientButton.setOnClickListener {
            ipEditText.isEnabled = true
            ipEditText.isFocusable = true

            val ip = ipEditText.text.toString().trim()
            val portStr = portEditText.text.toString().trim()

            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Please enter both IP and Port.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val port = portStr.toIntOrNull()
            if (port == null || port !in 1..65535) {
                Toast.makeText(this, "Invalid Port Number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newSession = ChatGlobal(ip = ip, port = port, isHostMe = (ip == localIPAddress),sessionName = sessionName.text.toString())
            items.add(newSession)
            adapter.notifyItemInserted(items.size - 1)
            Toast.makeText(this, "Session Added: $ip:$port. Click it to connect.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()

        val window = dialog.window
        if (window != null) {
            val metrics = Resources.getSystem().displayMetrics
            val width = (metrics.widthPixels * 0.90).toInt()
            val height = WindowManager.LayoutParams.WRAP_CONTENT
            window.setLayout(width, height)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.stopAllSessions()
    }

    fun getLocalIpAddress(context: Context): String {
        // Implementation as before
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return "0.0.0.0"
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "0.0.0.0"

            val ipAddress: String = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    getIpFromNetworkInterface("wlan0") ?: "0.0.0.0"
                }
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    getIpFromNetworkInterface("rmnet0") ?: "0.0.0.0"
                }
                else -> {
                    getIpFromNetworkInterface("eth0") ?: "0.0.0.0"
                }
            }

            return ipAddress
        } catch (ex: Exception) {
            Log.e("Utility", "Error getting local IP address: ${ex.message}")
            return "0.0.0.0"
        }
    }

    private fun getDeviceIdm(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    private fun getIpFromNetworkInterface(interfaceName: String): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                if (networkInterface.name.equals(interfaceName, ignoreCase = true)) {
                    val addresses = networkInterface.inetAddresses
                    for (address in Collections.list(addresses)) {
                        if (!address.isLoopbackAddress && address is InetAddress) {
                            val ip = address.hostAddress
                            if (ip.contains(":")) continue // Skip IPv6
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Utility", "Error getting IP from interface $interfaceName: ${e.message}")
        }
        return null
    }
    private fun showSettingsModal() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_dialog, null)

        val usernameInput = view.findViewById<EditText>(R.id.usernameInput)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        // Pre-fill username if exists
        usernameInput.setText(userPreferences.getUsername())

        saveButton.setOnClickListener {
            val newUsername = usernameInput.text.toString()
            if (newUsername.isNotEmpty()) {
                userPreferences.setUsername(newUsername)
                username = newUsername
                bottomSheetDialog.dismiss()
            } else {
                usernameInput.error = "Username cannot be empty"
            }
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
