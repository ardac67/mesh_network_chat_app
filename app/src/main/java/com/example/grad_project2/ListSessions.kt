package com.example.grad_project2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.adapter.ChatItemAdapter
import com.example.grad_project2.interfaces.OnMessageReceivedListener
import com.example.grad_project2.interfaces.OnSessionClickListener
import com.example.grad_project2.model.ChatGlobal
import com.example.grad_project2.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ListSessions : AppCompatActivity() {

    var isWifiP2pEnabled: Boolean = false
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatItemAdapter
    private val discoveredPeers = mutableListOf<ChatGlobal>()
    private val intentFilter = IntentFilter()
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var manager: WifiP2pManager
    private lateinit var receiver: WiFiDirectBroadcastReceiver
    private val peers = mutableListOf<WifiP2pDevice>()


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_sessions)
        // Indicates a change in the Wi-Fi Direct status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        // Indicates the state of Wi-Fi Direct connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        // Initialize Wi-Fi P2P Manager and Mesh Network
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)
        // UI setup
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ChatItemAdapter(this, discoveredPeers, object : OnSessionClickListener {
            override fun onSessionClicked(peer: ChatGlobal) {
                connectToPeer(peer)
            }

            override fun onSessionLongClicked(peer: ChatGlobal) {
                TODO("Not yet implemented")
            }
        })
        recyclerView.adapter = adapter
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WiFiDirect", "Peer discovery started successfully.")
            }

            override fun onFailure(reasonCode: Int) {
                Log.d("WiFiDirect", "Peer discovery failed: $reasonCode")
            }
        })

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMeshNetwork()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startMeshNetwork() {
        // Discover peers using the CustomMeshNetwork class
    }

    @SuppressLint("MissingPermission")
    private fun connectToPeer(peer: ChatGlobal) {
        //Toast.makeText(this, "CLICKED", Toast.LENGTH_SHORT).show()
        val matchedDevice = peers.find { it.deviceName == peer.ip }
        val config = WifiP2pConfig().apply {
            if (matchedDevice != null) {
                deviceAddress = matchedDevice.deviceAddress
                wps.setup = WpsInfo.PBC
            }
        }
        if(matchedDevice != null){
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {
                    // WiFiDirectBroadcastReceiver notifies us. Ignore for now.
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(
                        this@ListSessions,
                        "Connect failed. Retry.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    fun onPeersAvailable(deviceList: Collection<WifiP2pDevice>) {
        val devices = deviceList
        if (devices != peers) {
            peers.clear()
            peers.addAll(devices)
            // If an AdapterView is backed by this data, notify it
            // of the change. For instance, if you have a ListView of
            // available peers, trigger an update.
            val updatedChatItems = devices.map { peer ->
                ChatGlobal(
                    ip = peer.deviceName,
                    port = 0,
                    isHostMe = false,
                )
            }
            adapter.apply {
                itemList.clear()
                itemList.addAll(updatedChatItems)
                notifyDataSetChanged() // Notify the RecyclerView to refresh
            }
            Toast.makeText(this, "Device Found", Toast.LENGTH_SHORT).show()

            // Perform any other updates needed based on the new list of
            // peers connected to the Wi-Fi P2P network.
        }

        if (peers.isEmpty()) {
            Log.d("Arda Test", "No devices found")
            return
        }
    }

    fun onConnectionInfoAvailable(connectionInfo: WifiP2pInfo) {
        if (connectionInfo.groupFormed) {
            if (connectionInfo.isGroupOwner) {
                val tcpServer = TcpServer(
                    CoroutineScope(Dispatchers.IO),
                    8000,
                    object : OnMessageReceivedListener {
                        override fun onMessageReceived(message: Message) {
                            Log.d(
                                "ChatActivity1212",
                                "Message received on server: ${message.text}"
                            )
                        }
                    })
                tcpServer.startServer()
                SocketConnectionManager.server = tcpServer
                tcpServer.messageListener = object : OnMessageReceivedListener {
                    override fun onMessageReceived(message: Message) {
                        Log.d(
                            "ChatActivity3131313131",
                            "Message received on server: ${message.text}"
                        )
                    }
                }
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("IS_HOST", true)
                    putExtra("HOST_IP", connectionInfo.groupOwnerAddress.hostAddress)
                    putExtra("HOST_PORT", 8000)
                }
                startActivity(intent)
            } else {
                val groupOwnerIp = connectionInfo.groupOwnerAddress.hostAddress
                Log.d("WiFiDirect", "This device is a client. Connecting to group owner at ${connectionInfo.groupOwnerAddress.hostAddress}.")
                val existingConnection = SocketConnectionManager.getConnections().find { connection ->
                    connection.socket?.inetAddress?.hostAddress == groupOwnerIp
                }
                val socketConnection = SocketConnection(CoroutineScope(Dispatchers.IO))
                socketConnection.subscribeToSession(
                    ip = groupOwnerIp,
                    port = 8000,
                    connectTimeoutMillis = 5000,
                    onMessageReceived = { message ->
                        Log.d("WiFiDirect", "Message received from server: $message")
                        // Handle received messages
                    },
                    onSubscriptionSuccess = {
                        Log.d("WiFiDirect", "Connected to group owner at $groupOwnerIp")
                    },
                    onSubscriptionFailed = { error ->
                        Log.e(
                            "WiFiDirect",
                            "Failed to connect to group owner: ${error.message}"
                        )
                    }
                )
                SocketConnectionManager.addConnection(socketConnection)
                SocketConnectionManager.addListener(
                    socketConnection,
                    object : OnMessageReceivedListener {
                        override fun onMessageReceived(message: Message) {
                            if (message.text != "Message received.") {
                                Log.d("WiFiDirect", "Message in ListSessions: ${message.text}")
                            }
                        }
                    })
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("IS_HOST", false)
                    putExtra("HOST_IP", connectionInfo.groupOwnerAddress.hostAddress)
                    putExtra("HOST_PORT", 8000)
                }
                startActivity(intent)
            }
        } else {
            Log.d("WiFiDirect", "Group not formed.")
        }
    }


    fun disconnect() {
        TODO("Not yet implemented")
    }

    fun updateThisDevice(device: WifiP2pDevice?) {
        if (device == null) {
            Log.d("Arda Test", "Device information is unavailable.")
            return
        }

        // Log device information for debugging


        // Update the UI with this device's information (if applicable)
        //val updatedDevice =
        //adapter.notifyItemChanged()
    }

    /** register the BroadcastReceiver with the intent values to be matched  */
    public override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
    }

    public override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val refreshedPeers = peerList.deviceList
        if (refreshedPeers != peers) {
            peers.clear()
            peers.addAll(refreshedPeers)

            // If an AdapterView is backed by this data, notify it
            // of the change. For instance, if you have a ListView of
            // available peers, trigger an update.
            val updatedChatItems = refreshedPeers.map { peer ->
                ChatGlobal(
                    ip = peer.deviceName,
                    port = 0,
                    isHostMe = false,
                )
            }
            adapter.apply {
                itemList.clear()
                itemList.addAll(updatedChatItems)
                notifyDataSetChanged() // Notify the RecyclerView to refresh
            }

            // Perform any other updates needed based on the new list of
            // peers connected to the Wi-Fi P2P network.
        }

        if (peers.isEmpty()) {
            Log.d("Arda Test", "No devices found")
            return@PeerListListener
        }
    }
    private fun getDeviceStatus(status: Int): String {
        return when (status) {
            WifiP2pDevice.AVAILABLE -> "Available"
            WifiP2pDevice.INVITED -> "Invited"
            WifiP2pDevice.CONNECTED -> "Connected"
            WifiP2pDevice.FAILED -> "Failed"
            WifiP2pDevice.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }
    }

    object SocketConnectionManager {
        private val socketConnections = mutableListOf<SocketConnection>()
        private val connectionListeners = mutableMapOf<SocketConnection, MutableList<OnMessageReceivedListener>>()
        var server: TcpServer? = null
        fun addConnection(connection: SocketConnection) {
            socketConnections.add(connection)
            connectionListeners[connection] = mutableListOf()
        }

        fun removeConnection(connection: SocketConnection) {
            socketConnections.remove(connection)
            connectionListeners.remove(connection)
            connection.socket?.close()
        }

        fun getConnections(): List<SocketConnection> {
            return socketConnections
        }

        fun clearConnections() {
            socketConnections.forEach { it.socket?.close() }
            socketConnections.clear()
        }
        fun printAll(){
            socketConnections.forEach { Log.d("Sockets",it.socket?.inetAddress.toString())}
        }
        fun addListener(connection: SocketConnection, listener: OnMessageReceivedListener) {
            connectionListeners[connection]?.add(listener)
        }

        fun removeListener(connection: SocketConnection, listener: OnMessageReceivedListener) {
            connectionListeners[connection]?.remove(listener)
        }

        fun notifyListeners(connection: SocketConnection, message: Message) {
            connectionListeners[connection]?.forEach { it.onMessageReceived(message) }
        }
    }


}
