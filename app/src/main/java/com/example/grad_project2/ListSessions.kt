package com.example.grad_project2

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

class ListSessions : AppCompatActivity(), OnSessionClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatItemAdapter
    private lateinit var progressBar: ProgressBar
    private val items = mutableListOf<ChatGlobal>()
    private lateinit var socketConnection: SocketConnection
    private lateinit var localIPAddress: String
    private val tcpServers = mutableListOf<TcpServer>()
    private lateinit var sessionManager: SessionManager
    private lateinit var udpBroadcaster: UdpBroadcaster
    private lateinit var deviceId: String // Cihaz UUID'si
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_sessions)
        deviceId = getDeviceIdm()
        Log.d("ListSessionsActivity", "Device ID: $deviceId")
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        socketConnection = SocketConnection(lifecycleScope,deviceId)

        // Kendi IP adresinizi alın
        localIPAddress = socketConnection.getLocalIPAddress()
        Log.d("ListSessions", "Local IP Address: $localIPAddress")

        // Initialize the adapter **before** setting it to RecyclerView
        adapter = ChatItemAdapter(this, items, socketConnection, this)
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        val createSession = findViewById<FrameLayout>(R.id.createSession)

        createSession.setOnClickListener {
            openCreateSessionModal()
        }
        // Initialize SessionManager first
        sessionManager = SessionManager(
            scope = lifecycleScope,
            ipAddress = localIPAddress
        )

        // Initialize UdpBroadcaster with a lambda that accesses sessionManager's getActivePorts()
        udpBroadcaster = UdpBroadcaster(
            ip = localIPAddress,
            getPorts = { sessionManager.getActivePorts() },
            broadcastPort = 8888,
            interval = 5000,
            deviceId
        )

        // Start broadcasting


        lifecycleScope.launch(Dispatchers.IO) {
            udpBroadcaster.startBroadcasting()
        }
        // Start listening for broadcasts
        socketConnection.listenForBroadcasts { ip, ports, senderIp ->
            // Check for duplicates based on IP and Port
            val newItems = ports.mapNotNull { port ->
                val portInt = port
                if (portInt != null && !items.any { it.ip == ip && it.port == portInt }) {
                    ChatGlobal(
                        ip = ip,
                        port = portInt,
                        isHostMe = (ip == localIPAddress) // Kendi IP adresinizle karşılaştırın
                    )
                } else {
                    null // Skip if already exists or invalid port
                }
            }

            if (newItems.isNotEmpty()) {
                runOnUiThread {
                    val startPosition = items.size
                    items.addAll(newItems)
                    adapter.notifyItemRangeInserted(startPosition, newItems.size)
                    Log.d("ListSessions", "Added ${newItems.size} new items")
                }
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

    override fun onSessionClicked(item: ChatGlobal, socketConnection: SocketConnection) {
        if (item.isHostMe) {
            // Kendi oturumunuzdaysanız, farklı bir işlem yapabilirsiniz veya kullanıcıyı bilgilendirebilirsiniz
            Toast.makeText(this, "You are the host of this session.", Toast.LENGTH_SHORT).show()
            //Log.d("ListSessions", "User is the host of this session: ${item.ip}:${item.port}")
            item.isSubscribed = false
            item.subscriptionJob = null
            return
        }

        if (item.isSubscribed) {
            // Unsubscribe logic
            item.isSubscribed = false
            item.subscriptionJob?.cancel()
            item.subscriptionJob = null
            adapter.notifyItemChanged(items.indexOf(item))
            Toast.makeText(this, "Unsubscribed from ${item.ip}:${item.port}", Toast.LENGTH_SHORT).show()
            Log.d("ListSessions", "Unsubscribed from ${item.ip}:${item.port}")
        } else {
            // Subscribe logic
            socketConnection.subscribeToSession(
                ip = item.ip,
                port = item.port,
                connectTimeoutMillis = 5000,
                onMessageReceived = { message ->
                    Log.d("SessionMessage", "Received from ${item.ip}:${item.port} - $message")
                    runOnUiThread {
                        item.unreadMessages++
                        item.time = JSONObject(message).optString("timestamp", "N/A")
                        adapter.notifyItemChanged(items.indexOf(item))
                        Toast.makeText(this, "Message from ${item.ip}:${item.port}: $message", Toast.LENGTH_SHORT).show()
                        Log.d("ListSessions", "Updated unreadMessages for ${item.ip}:${item.port}")
                    }
                },
                onSubscriptionSuccess = {
                    runOnUiThread {
                        item.isSubscribed = true
                        adapter.notifyItemChanged(items.indexOf(item))
                        Toast.makeText(this, "Subscribed to ${item.ip}:${item.port}", Toast.LENGTH_SHORT).show()
                        Log.d("ListSessions", "Subscribed to ${item.ip}:${item.port}")
                    }
                },
                onSubscriptionFailed = { exception ->
                    runOnUiThread {
                        Toast.makeText(this, "Failed to subscribe to ${item.ip}:${item.port}: ${exception.message}", Toast.LENGTH_SHORT).show()
                        Log.e("ListSessions", "Failed to subscribe to ${item.ip}:${item.port}: ${exception.message}")
                    }
                }
            )?.let { job ->
                // Store the subscription job
                item.subscriptionJob = job
                Log.d("ListSessions", "Stored subscription job for ${item.ip}:${item.port}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all active subscriptions
        items.forEach { session ->
            session.subscriptionJob?.cancel()
        }
        tcpServers.forEach { it.stopServer() }
        tcpServers.clear()
    }

    private fun openCreateSessionModal() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_create_session)

        val createSessionButton = dialog.findViewById<Button>(R.id.createSessionButton)
        val ipEditText = dialog.findViewById<TextInputEditText>(R.id.ipEditText)
        val portEditText = dialog.findViewById<TextInputEditText>(R.id.portEditText)

        createSessionButton.setOnClickListener {
            val ip = localIPAddress
            val portStr = portEditText.text.toString().trim()

            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Please enter both IP and Port.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.IP_ADDRESS.matcher(ip).matches()) {
                Toast.makeText(this, "Invalid IP Address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val port = portStr.toIntOrNull()
            if (port == null || port !in 1..65535) {
                Toast.makeText(this, "Invalid Port Number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create a new ChatGlobal item
            val newSession = ChatGlobal(
                ip = ip,
                port = port,
                isHostMe = true// Kendi IP adresinizle karşılaştırın
            )

            // Check for duplicates based on IP and Port
            val isDuplicate = items.any { it.ip == newSession.ip && it.port == newSession.port }
            if (isDuplicate) {
                Toast.makeText(this, "Session with this IP and Port already exists.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Add the new session to the list and notify the adapter
            items.add(newSession)
            adapter.notifyItemInserted(items.size - 1)
            
            sessionManager.createSession(port = port) { senderIp, senderPort, message ->
                runOnUiThread {
                    // Gelen mesajları işle
                    Toast.makeText(
                        this,
                        "Message from $senderIp:$senderPort: $message",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("ListSessionsActivity", "Message from $senderIp:$senderPort: $message")

                    // İlgili oturuma unreadMessages sayısını artır
                    val session = items.find { it.ip == senderIp && it.port == senderPort }
                    session?.let {
                        it.unreadMessages++
                        it.time = "1" // Örneğin, timestamp değeri ekleyin
                        adapter.notifyItemChanged(items.indexOf(it))
                    }
                }
            }

            Toast.makeText(this, "Session Created: $ip:$port", Toast.LENGTH_SHORT).show()
            Log.d("ListSessions", "Created new session: $ip:$port")

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
    fun getLocalIpAddress(context: Context): String {
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
                            if (ip.contains(":")) {
                                // IPv6 adresini atla
                                continue
                            }
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
}

