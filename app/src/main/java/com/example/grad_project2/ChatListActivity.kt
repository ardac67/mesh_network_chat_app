package com.example.grad_project2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.adapter.ChatItemAdapter
import com.example.grad_project2.interfaces.OnSessionClickListener
import com.example.grad_project2.model.ChatGlobal
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatListActivity : AppCompatActivity() {
    // A set to keep track of connected device endpoints
    private val connectedEndpoints = mutableSetOf<String>()
    private lateinit var adapter: ChatItemAdapter

    // Nearby Connections client
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var recyclerView: RecyclerView
    private val discoveredPeers = mutableListOf<ChatGlobal>()
    private val connectionGraph = mutableMapOf<String, GraphNode>()
    private lateinit var deviceUUID: UUID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_list)

        deviceUUID = generateDeviceUUID(this)
        connectionsClient = Nearby.getConnectionsClient(this)
        requestBluetoothPermissions()
        startAdvertising()
        startDiscovery()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatItemAdapter(this, discoveredPeers, object : OnSessionClickListener {
            override fun onSessionClicked(peer: ChatGlobal) {
                connectionsClient.requestConnection(peer.ip, peer.ip, connectionLifecycleCallback)
            }
        })
        recyclerView.adapter = adapter
    }

    private fun startAdvertising() {
        val connectionsClient = Nearby.getConnectionsClient(this)
        connectionsClient.startAdvertising(
            deviceUUID.toString(), // Replace with your device name or ID
            "com.example.grad_project2",
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(
                    endpointId: String,
                    connectionInfo: ConnectionInfo
                ) {
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                }

                override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        connectedEndpoints.add(endpointId) // Track the connected peer
                        shareConnectionsWithPeer(endpointId) // Share connection info'
                        Log.d("LogArda", "Connected to $endpointId")
                    }
                }

                override fun onDisconnected(endpointId: String) {
                    connectedEndpoints.remove(endpointId)
                    Log.d("Nearby", "Disconnected from $endpointId")
                }
            },
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        )
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept incoming connections
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                Log.d("LogArda", "Successfully connected to $endpointId")
                shareConnectionsWithPeer(endpointId)
                requestConnections(endpointId)
                displayGraph(deviceUUID.toString())
                val intent = Intent(this@ChatListActivity, ChatActivity::class.java).apply {
                    putExtra("endpointId", endpointId)
                    putExtra(
                        "peerName",
                        "Peer Device"
                    ) // You can replace this with actual peer name if available
                }
                startActivity(intent)
            } else {
                Log.e("LogArda", "Failed to connect to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            Log.d("LogArda", "Disconnected from $endpointId")
        }
    }


    private fun startDiscovery() {
        val connectionsClient = Nearby.getConnectionsClient(this)
        connectionsClient.startDiscovery(
            "com.example.grad_project2",
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    //connectionsClient.requestConnection("DeviceName", endpointId, connectionLifecycleCallback)
                    Log.d("LogArda", "Discovered $endpointId")
                    if (discoveredPeers.none { it.ip == endpointId }) {
                        discoveredPeers.add(ChatGlobal(endpointId, 8000, false))
                        adapter.notifyDataSetChanged()
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    discoveredPeers.removeAll { it.ip == endpointId }
                    adapter.notifyDataSetChanged()
                    Log.d("LogArda", "Lost connection to $endpointId")
                }
            },
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        )
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val receivedData = payload.asBytes()?.let { String(it) }

            // Check if the payload is null or empty
            if (receivedData == null) {
                Log.e("LogArda", "Received null payload from $endpointId")
                return
            }

            Log.d("LogArda", "Received payload from $endpointId: $receivedData")


            // Handle JSON payloads
            if (receivedData.startsWith("{") && receivedData.endsWith("}")) {
                try {
                    handleReceivedConnections(receivedData)
                    displayGraph(deviceUUID.toString())
                    val intent = Intent(this@ChatListActivity, ChatActivity::class.java).apply {
                        putExtra("endpointId", endpointId)
                        putExtra(
                            "peerName",
                            "Peer Device"
                        ) // You can replace this with actual peer name if available
                    }
                    startActivity(intent)

                } catch (e: org.json.JSONException) {
                    Log.e("LogArda", "Failed to parse JSON payload: ${e.message}")
                }
            }
            // Handle predefined command "REQUEST_CONNECTIONS"
            else if (receivedData == "REQUEST_CONNECTIONS") {
                Log.d("LogArda", "Handling REQUEST_CONNECTIONS from $endpointId")
                handleConnectionRequest(endpointId)
            }
            // Handle unknown or unexpected payloads
            else {
                Log.w("LogArda", "Received unexpected payload from $endpointId: $receivedData")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d("LogArda", "Payload transfer update from $endpointId: ${update.bytesTransferred}")
        }
    }


    private fun handleReceivedPayload(endpointId: String, data: String) {
        // Parse and process received data
        // For now, just log it
        Log.d("LogArda", "Data from $endpointId: $data")
    }


    fun generateDeviceUUID(context: Context): UUID {
        val androidId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (androidId != null && androidId.isNotEmpty()) {
            UUID.nameUUIDFromBytes(androidId.toByteArray(Charsets.UTF_8))
        } else {
            UUID.randomUUID()
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            val missingPermissions = permissions.filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                requestPermissions(missingPermissions.toTypedArray(), 1001)
            } else {
                Log.d("Permissions", "All Bluetooth permissions already granted")
            }
        } else {
            // For Android versions below 12
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            }
        }
    }

    fun shareConnectionsWithPeer(endpointId: String) {
        val deviceInfo = JSONObject().apply {
            put("deviceId", deviceUUID)
            put("connections", JSONArray(connectedEndpoints))
        }
        val payload = Payload.fromBytes(deviceInfo.toString().toByteArray())
        Nearby.getConnectionsClient(this).sendPayload(endpointId, payload)
    }

    fun handleReceivedConnections(data: String) {
        val deviceInfo = JSONObject(data)
        val deviceId = deviceInfo.getString("deviceId")
        val connections = deviceInfo.getJSONArray("connections")

        val node = connectionGraph.getOrPut(deviceId) { GraphNode(deviceId) }
        for (i in 0 until connections.length()) {
            val connectedDeviceId = connections.getString(i)
            val childNode =
                connectionGraph.getOrPut(connectedDeviceId) { GraphNode(connectedDeviceId) }
            if (!node.connections.contains(childNode)) {
                node.connections.add(childNode)
            }
        }
    }

    fun requestConnections(endpointId: String) {
        val requestPayload = Payload.fromBytes("REQUEST_CONNECTIONS".toByteArray())
        Nearby.getConnectionsClient(this).sendPayload(endpointId, requestPayload)
    }

    fun handleConnectionRequest(endpointId: String) {
        shareConnectionsWithPeer(endpointId)
    }

    fun displayGraph(rootDeviceId: String) {
        val rootNode = connectionGraph[rootDeviceId] ?: return
        renderNode(rootNode, 0)
    }

    fun renderNode(node: GraphNode, level: Int) {
        Log.d("Graph", " ".repeat(level * 2) + node.deviceId)
        node.connections.forEach { renderNode(it, level + 1) }
    }


}