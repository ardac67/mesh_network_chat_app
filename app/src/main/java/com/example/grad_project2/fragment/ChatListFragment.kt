package com.example.grad_project2.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.GraphNode
import com.example.grad_project2.interfaces.OnSessionClickListener
import com.example.grad_project2.R
import com.example.grad_project2.activity.ChatSessionsActivity
import com.example.grad_project2.adapter.ChatItemAdapter
import com.example.grad_project2.model.ChatGlobal
import com.example.grad_project2.model.Message
import com.example.grad_project2.viewmodel.SharedChatViewModel
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
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class ChatListFragment : Fragment() {
    // A set to keep track of connected device endpoints
    private val connectedEndpoints = mutableSetOf<String>()
    private lateinit var adapter: ChatItemAdapter

    // Nearby Connections client
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var recyclerView: RecyclerView
    private val discoveredPeers = mutableListOf<ChatGlobal>()
    private val connectionGraph = mutableMapOf<String, GraphNode>()
    private lateinit var deviceUUID: UUID

    private val sharedViewModel: SharedChatViewModel by activityViewModels()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val receivedData = payload.asBytes()?.let { String(it) }

            if (receivedData == null) {
                Log.e("ChatListFragment", "Received null payload from $endpointId")
                return
            }

            if (discoveredPeers.none { it.ip == endpointId }){
                //Log.d("ZAART", "arrt: $receivedData")
                discoveredPeers.add(ChatGlobal(endpointId, 8000, false, amIConnected = false))
                adapter.notifyDataSetChanged()
            }

            Log.d("ChatListFragment", "Received payload: $receivedData")
            Log.d("ChatListFragment", "Payload received from $endpointId: $receivedData")
            Log.d("ChatListFragment", "Current connected endpoints: $connectedEndpoints")

            if (receivedData.startsWith("{") && receivedData.endsWith("}")) {
                try {
                    val json = JSONObject(receivedData)
                    val msgText = json.getString("message")
                    val nick = json.getString("nick")
                    val timestamp = json.getString("timestamp")
                    val ip = json.getString("ip")

                    val message = Message(
                        text = msgText,
                        isSentByMe = false,
                        timestamp = System.currentTimeMillis(),
                        type = "string",
                        nick = nick,
                        ip = ip,
                        from = json.getString("from")
                    )

                    // Share the message with ChatFragment using ViewModel
                    sharedViewModel.postMessage(message)

                } catch (e: org.json.JSONException) {
                    try {
                        val json = JSONObject(receivedData)
                        val deviceId = json.getString("from")
                        val connections = json.getJSONArray("connections")

                        Log.d("GraphDebug", "Processing connections for deviceId: $deviceId")

                        // Add or update the node for the incoming deviceId
                        val node = connectionGraph.getOrPut(deviceId) { GraphNode(deviceId) }

                        // Ensure a connection exists between local device and incoming deviceId
                        val localNode = connectionGraph.getOrPut(deviceUUID.toString()) { GraphNode(deviceUUID.toString()) }
                        if (!localNode.connections.contains(node)) {
                            localNode.connections.add(node)
                            Log.d("GraphDebug", "Linked local device (${deviceUUID}) with incoming device ($deviceId)")
                        }

                        // Process all the connections for the incoming node
                        for (i in 0 until connections.length()) {
                            val connectedDeviceId = connections.getString(i)
                            val childNode = connectionGraph.getOrPut(connectedDeviceId) { GraphNode(connectedDeviceId) }

                            if (!node.connections.contains(childNode)) {
                                node.connections.add(childNode)
                                Log.d("GraphDebug", "Linked $deviceId to $connectedDeviceId")
                            }
                        }

                        Log.d("GraphDebug", "Updated Graph Nodes: ${connectionGraph.keys}")
                        displayGraph(deviceUUID.toString())

                    } catch (e: JSONException) {
                        Log.e("ChatListFragment", "Failed to parse connections payload: ${e.message}")
                    }
                }
            }// Handle predefined command "REQUEST_CONNECTIONS"
            else if (receivedData == "REQUEST_CONNECTIONS") {
                Log.d("ChatListFragment", "Handling REQUEST_CONNECTIONS from $endpointId")
                handleConnectionRequest(endpointId)
            }
            // Handle unknown or unexpected payloads
            else {
                Log.w("ChatListFragment", "Received unexpected payload from $endpointId: $receivedData")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d("ChatListFragment", "Payload transfer update: ${update.bytesTransferred}")
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceUUID = generateDeviceUUID()
        connectionsClient = Nearby.getConnectionsClient(requireContext())
        connectionGraph.getOrPut(deviceUUID.toString()) { GraphNode(deviceUUID.toString()) }
        Log.d("GraphDebug", "Local device added to graph: ${deviceUUID}")
        requestBluetoothPermissions()
        startAdvertising()
        startDiscovery()
    ////ef54025c-2ad0-32fd-8279-2b0c21ea00e5
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ChatItemAdapter(requireContext(), discoveredPeers, object :
            OnSessionClickListener {
            override fun onSessionClicked(peer: ChatGlobal) {
                if(!peer.amIConnected){
                    connectionsClient.requestConnection(peer.ip, peer.ip, connectionLifecycleCallback)
                }
                else{
                    (activity as? ChatSessionsActivity)?.navigateToChatFragment(peer.ip, peer.deviceName.toString())
                }

            }
        })
        recyclerView.adapter = adapter

        requestBluetoothPermissions()
        startDiscovery()
    }

    private fun startAdvertising() {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        println("Device Name: $deviceName")
        val connectionsClient = Nearby.getConnectionsClient(requireContext())
        connectionsClient.startAdvertising(
            deviceName, // Replace with your device name or ID
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


    private fun startDiscovery() {
        val connectionsClient = Nearby.getConnectionsClient(requireContext())
        connectionsClient.startDiscovery(
            "com.example.grad_project2",
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    //connectionsClient.requestConnection("DeviceName", endpointId, connectionLifecycleCallback)
                    val deviceName = info.endpointName
                    Log.d("LogArda", "Discovered $endpointId")
                    if (discoveredPeers.none { it.ip == endpointId }) {
                        discoveredPeers.add(ChatGlobal(endpointId, 8000, false, amIConnected = false, deviceName = deviceName))
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

    fun generateDeviceUUID(): UUID {
        val androidId =
            Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        return if (androidId != null && androidId.isNotEmpty()) {
            UUID.nameUUIDFromBytes(androidId.toByteArray(Charsets.UTF_8))
        } else {
            UUID.randomUUID()
        }
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
                val endpointName = discoveredPeers.firstOrNull { it.ip == endpointId }?.deviceName
                    ?: "Unknown Device"
                shareConnectionsWithPeer(endpointId)
                requestConnections(endpointId)
                displayGraph(deviceUUID.toString())
                (activity as? ChatSessionsActivity)?.navigateToChatFragment(endpointId, endpointName)
            } else {
                Log.e("LogArda", "Failed to connect to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("ChatListFragment", "Disconnected from $endpointId")
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
                ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                requestPermissions(missingPermissions.toTypedArray(), 1001)
            } else {
                Log.d("Permissions", "All Bluetooth permissions already granted")
            }
        } else {
            // For Android versions below 12
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
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
        Nearby.getConnectionsClient(requireContext()).sendPayload(endpointId, payload)
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
        Nearby.getConnectionsClient(requireContext()).sendPayload(endpointId, requestPayload)
    }

    fun handleConnectionRequest(endpointId: String) {
        shareConnectionsWithPeer(endpointId)
    }

    fun displayGraph(rootDeviceId: String) {
        if (!connectionGraph.containsKey(rootDeviceId)) {
            Log.e("GraphDebugError", "Root deviceId ($rootDeviceId) not found in connectionGraph!")
            Log.e("GraphDebugError", "Available keys in graph: ${connectionGraph.keys}")
            return
        }
        val rootNode = connectionGraph[rootDeviceId]
        if (rootNode == null) {
            Log.e("GraphDebugError", "Root node is null for deviceId: $rootDeviceId")
            return
        }
        renderNode(rootNode, 0)
    }


    fun renderNode(node: GraphNode, level: Int) {
        Log.d("Zarting", " ".repeat(level * 2) + node.deviceId)
        node.connections.forEach { renderNode(it, level + 1) }
    }
    private fun refreshNearbyConnections() {
        Log.d("Nearby", "Refreshing Nearby Connections...")

        // Stop all existing connections
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()

        // Clear UI lists
        discoveredPeers.clear()
        connectedEndpoints.clear()
        adapter.notifyDataSetChanged()

        // Restart advertising and discovery
        startAdvertising()
        startDiscovery()
    }


}
