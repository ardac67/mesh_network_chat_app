package com.example.grad_project2.fragment

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import com.example.grad_project2.Database.ChatDatabase

class ChatListFragment : Fragment() {
    // A set to keep track of connected device endpoints
    val connectedEndpoints = mutableSetOf<String>()
    private lateinit var adapter: ChatItemAdapter

    // Nearby Connections client
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var recyclerView: RecyclerView
    private val discoveredPeers = mutableListOf<ChatGlobal>()
    private val connectionGraph = mutableMapOf<String, GraphNode>()
    private lateinit var deviceUUID: String
    private lateinit var connectionProgressBar: ProgressBar
    private lateinit var localName:String
    private val connectionTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val CONNECTION_TIMEOUT_MS = 20000L // 10 seconds timeout
    private val chatDao by lazy {
        ChatDatabase.getDatabase(requireContext()).chatDao()
    }
    //


    private val sharedViewModel: SharedChatViewModel by activityViewModels()

    private fun relayMessage(receivedMessage: JSONObject) {
        Log.d("RelayDebugWork","Inside of relay function")
        val messageId = receivedMessage.getString("id") // Ensure each message has a unique 'id'
        if (sharedViewModel.relayedMessages.contains(messageId)) {
            Log.d("RelayMessages", "Message already relayed, skipping: $messageId")
            return
        }

        // Mark message as relayed
        sharedViewModel.relayedMessages.add(messageId)
        val connectedPeerNames = discoveredPeers
            .filter { it.amIConnected } // Filter only connected peers
            .map { it.deviceName } // Extract the device names
        val ip = receivedMessage.getString("ip")
        /*
        sharedViewModel.connectedEndpoints.value?.forEach { endPoint ->
            if (endPoint != ip) {
                //sharedViewModel.publicConnections.
                Log.d("RelayDebugWork","Cannot match ip and endpoint")
                receivedMessage.put("relayedFrom",deviceUUID)
                val payload = Payload.fromBytes(receivedMessage.toString().toByteArray())
                connectionsClient.sendPayload(endPoint, payload)
                    .addOnSuccessListener {
                        Log.d("RelayMessages", "Message relayed successfully to $endPoint")
                    }
                    .addOnFailureListener { e ->
                        Log.e("RelayMessages", "Failed to send message: ${e.message}")
                        Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
            }
        }
         */
        // Step 1: Filter connected peers that are also in publicConnections
        val validPeers = discoveredPeers
            .filter { it.amIConnected && sharedViewModel.publicConnections.contains(it.deviceName) } // Ensure connection and publicity

// Step 2: Iterate over connectedEndpoints and match with valid peers
        //val ip = receivedMessage.getString("ip")

        sharedViewModel.connectedEndpoints.value?.forEach { endPoint ->
            if (endPoint != ip) {
                // Find the corresponding ChatGlobal object
                val targetPeer = validPeers.find { it.ip == endPoint }

                if (targetPeer != null) {
                    Log.d(" ", "Relaying message to valid public endpoint: $endPoint")

                    receivedMessage.put("relayedFrom", deviceUUID)
                    val payload = Payload.fromBytes(receivedMessage.toString().toByteArray())

                    connectionsClient.sendPayload(endPoint, payload)
                        .addOnSuccessListener {
                            Log.d("RelayMessages", "Message relayed successfully to $endPoint")
                        }
                        .addOnFailureListener { e ->
                            Log.e("RelayMessages", "Failed to send message: ${e.message}")
                            Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.d("RelayDebugWork", "Endpoint $endPoint is not in valid public connections.")
                }
            }
        }

    }


    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val receivedData = payload.asBytes()?.let { String(it) }

            if (receivedData == null) {
                Log.e("ChatListFragment", "Received null payload from $endpointId")
                return
            }

            /*
            if (discoveredPeers.none { it.ip == endpointId }){
                //Log.d("ZAART", "arrt: $receivedData")
                discoveredPeers.add(ChatGlobal(endpointId, 8000, false, amIConnected = false))
                adapter.notifyDataSetChanged()
            }

             */

            Log.d("ChatListFragment", "Received payload: $receivedData")
            Log.d("ChatListFragment", "Payload received from $endpointId: $receivedData")
            //Log.d("ChatListFragment", "Current connected endpoints: $connectedEndpoints")

            if (receivedData.startsWith("{") && receivedData.endsWith("}")) {
                try {
                    val json = JSONObject(receivedData)
                    val relayedFrom = json.optString("relayedFrom")?.takeIf { it.isNotEmpty() } ?: "Unknown"
                    if(sharedViewModel.relayedMessages.contains(json.getString("id"))){
                        return
                    }
                    val notifyValue = json.optString("notify", "")
                    if(notifyValue.equals("public")){
                        val whereItIS = json.getString("from")
                        sharedViewModel.setMessagesPrivacy(whereItIS,"public")
                        sharedViewModel.publicConnections.add(whereItIS)
                        //return
                    }
                    else if(notifyValue.equals("private")){
                        val whereItIS = json.getString("from")
                        sharedViewModel.setMessagesPrivacy(whereItIS,"private")
                        sharedViewModel.publicConnections.remove(whereItIS)
                        //return
                    }
                    val msgText = json.getString("message")
                    val nick = json.getString("nick")
                    val timestamp = json.getString("timestamp")
                    val ip = json.getString("ip")
                    val notify = json.optString("notify")?.takeIf { it.isNotEmpty() } ?: "Unknown"
                    val type = json.optString("type")?.takeIf { it.isNotEmpty() } ?: "string"
                    val latitude = if (json.has("latitude") && !json.isNull("latitude")) json.optDouble("latitude") else null
                    val longitude = if (json.has("longitude") && !json.isNull("longitude")) json.optDouble("longitude") else null
                    val message = Message(
                        text = msgText,
                        isSentByMe = false,
                        timestamp = System.currentTimeMillis(),
                        type = type,
                        nick = nick,
                        ip = ip,
                        from = json.getString("from"),
                        relayedFrom = relayedFrom,
                        notify = notify,
                        latitude = latitude,
                        longitude =longitude
                    )
                    val chatPeer = discoveredPeers.find { it.deviceName == json.getString("from") }
                    Log.d("ardaaaaaaa", "$chatPeer")
                    if (chatPeer != null) {
                        if(chatPeer.amIConnected){
                            chatPeer?.let {
                                it.lastMessage = msgText
                                val position = discoveredPeers.indexOf(it)
                                Log.d("ChatListFragment", "Updating lastMessage for peer at position $position: $msgText")
                                if (position != -1) {
                                    activity?.runOnUiThread {
                                        adapter.notifyItemChanged(position)
                                    }
                                } else {
                                    Log.e("ChatListFragment", "Peer not found in list when trying to update lastMessage")
                                }
                            }
                        }
                    }

                    // Share the message with ChatFragment using ViewModel
                    sharedViewModel.postMessage(message)
                    if(!deviceUUID.equals(json.getString("from")) && sharedViewModel.getMessagesPrivacy(json.getString("from")) == true){
                        Log.d("Farting","Farting")
                        //discoveredPeers.forEach{
                            //chatGlobal ->
                            //if(sharedViewModel.publicConnections.contains(chatGlobal.deviceName)){
                                //Log.d("I will relay to device:","${chatGlobal.deviceName}")
                                    if(notifyValue==""){
                                        relayMessage(json)
                                    }

                            //}
                        //}
                    }
                } catch (e: org.json.JSONException) {
                    Log.e("WhatIsException", "Failed to parse connections payload: ${e.message}")
                    try {
                        val json = JSONObject(receivedData)
                        val deviceId = json.getString("from")
                        val connections = json.getJSONArray("connections")
                        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                        Log.d("MeshNetworkProcess", "Processing connections for deviceId: $deviceId")

                        // Add or update the node for the incoming deviceId
                        val node = connectionGraph.getOrPut(deviceId) { GraphNode(deviceId) }

                        // Ensure a connection exists between local device and incoming deviceId
                        val localNode = connectionGraph.getOrPut(deviceName) { GraphNode(deviceName) }
                        if (!localNode.connections.contains(node)) {
                            localNode.connections.add(node)
                            Log.d("MeshNetworkProcess", "Linked local device (${deviceUUID}) with incoming device ($deviceId)")
                        }

                        // Process all the connections for the incoming node
                        for (i in 0 until connections.length()) {
                            val connectedDeviceId = connections.getString(i)
                            val childNode = connectionGraph.getOrPut(connectedDeviceId) { GraphNode(connectedDeviceId) }

                            if (!node.connections.contains(childNode)) {
                                node.connections.add(childNode)
                                Log.d("MeshNetworkProcess", "Linked $deviceId to $connectedDeviceId")
                            }
                        }

                        Log.d("MeshNetworkProcess", "Updated Graph Nodes: ${connectionGraph.keys}")
                        //displayGraph(deviceName)

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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        deviceUUID = deviceName
        connectionsClient = Nearby.getConnectionsClient(requireContext())
        connectionGraph.getOrPut(deviceUUID.toString()) { GraphNode(deviceUUID.toString()) }
        Log.d("GraphDebug", "Local device added to graph: ${deviceUUID}")
        requestBluetoothPermissions()
        startAdvertising()
        startDiscovery()
    ////ef54025c-2ad0-32fd-8279-2b0c21ea00e5
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        connectionProgressBar = view.findViewById(R.id.connectionProgressBar)
        adapter = ChatItemAdapter(requireContext(), discoveredPeers, object :
            OnSessionClickListener {
            override fun onSessionClicked(peer: ChatGlobal) {
                if(!peer.amIConnected){
                    //connectionsClient.requestConnection(peer.ip, peer.ip, connectionLifecycleCallback)
                    attemptConnectionWithTimeout(peer.ip)
                    showLoadingBar()
                }
                else{
                    (activity as? ChatSessionsActivity)?.navigateToChatFragment(peer.ip, peer.deviceName.toString())
                }

            }

            override fun onSessionLongClicked(peer: ChatGlobal) {
                showDisconnectDialog(peer)
            }
        })
        recyclerView.adapter = adapter
        val createSession: FrameLayout = view.findViewById(R.id.createSession)
        createSession.setOnClickListener {
            refreshNearbyConnections()
        }
        val graphVisual:ImageView = view.findViewById((R.id.editIcon))
        graphVisual.setOnClickListener{
            displayGraph(deviceName)
        }
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
                    Log.d("DeviceNameBABACIk:",connectionInfo.endpointName)
                    val peerExists = discoveredPeers.any { it.ip == endpointId || it.deviceName == connectionInfo.endpointName }

                    if (!peerExists) {
                        Log.w("NearbyConnection", "Rejecting unknown connection request from $endpointId (${connectionInfo.endpointName})")
                        connectionsClient.rejectConnection(endpointId)
                        connectionTimeoutHandler.removeCallbacksAndMessages(null)
                        Toast.makeText(context, "Rejected connection from unknown device: ${connectionInfo.endpointName}", Toast.LENGTH_SHORT).show()
                        return
                    }
                    activity?.runOnUiThread {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Connection Request")
                            .setMessage("${connectionInfo.endpointName} wants to connect. Accept?")
                            .setPositiveButton("Accept") { dialog, which ->
                                connectionsClient.acceptConnection(endpointId, payloadCallback)
                            }
                            .setNegativeButton("Reject") { dialog, which ->
                                connectionsClient.rejectConnection(endpointId)
                                connectionTimeoutHandler.removeCallbacksAndMessages(null)
                                Toast.makeText(context, "Connection rejected", Toast.LENGTH_SHORT).show()
                            }
                            .setCancelable(false)
                            .show()
                    }
                }

                override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                    connectionTimeoutHandler.removeCallbacksAndMessages(null)
                    if (result.status.isSuccess) {
                        // 1) Mark the endpoint as connected
                        hideLoadingBar()
                        sharedViewModel.addConnection(endpointId)
                        discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = true

                        // 2) Log success
                        Log.d("LogArda", "Connected to $endpointId")

                        // 3) Retrieve the friendly name
                        val endpointName = discoveredPeers.firstOrNull { it.ip == endpointId }?.deviceName ?: "Unknown Device"
                        sharedViewModel.mapNameEndpoint[endpointId] = endpointName
                        Log.d("LogArda", "Successfully connected to $endpointName")
                        // 4) Share your existing connections with the newly connected peer
                        shareConnectionsWithPeer(endpointId)
                        discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = true
                        // 5) Request their connections (to build your graph if needed)
                        requestConnections(endpointId)
                        //displayGraph(deviceUUID)
                        sharedViewModel.addMessagesPrivacy(endpointName,false)
                        // 6) Immediately open the chat
                        (activity as? ChatSessionsActivity)?.navigateToChatFragment(endpointId, endpointName)
                    }
                    else{
                        Toast.makeText(context, "Connection rejected1", Toast.LENGTH_SHORT).show()
                        discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = false
                    }
                }


                override fun onDisconnected(endpointId: String) {
                    sharedViewModel.removeConnection(endpointId)
                    Log.d("LogArda", "Disconnected from $endpointId")
                    discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = false

                    discoveredPeers.find { it.ip == endpointId }?.let {
                        it.amIConnected = false
                    }
                    adapter.notifyDataSetChanged()
                    connectionsClient.stopDiscovery()


                    // 2) Log
                    Log.d("LogArda", "Disconnected from $endpointId")

                    // 3) Post a "disconnected" message to the chat
                    val systemMessage = Message(
                        text = "User $endpointId has disconnected.",
                        isSentByMe = false,
                        timestamp = System.currentTimeMillis(),
                        type = "system",
                        nick = "System",       // or any nickname you want to indicate it's a system message
                        ip = endpointId,       // or "N/A" if you prefer
                        from = "System"        // so ChatFragment knows it's a system message
                    )
                    sharedViewModel.postMessage(systemMessage)
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
                    val existingPeer = discoveredPeers.find { it.deviceName == deviceName }
                    if (existingPeer != null) {
                        Log.d("LogArda", "Duplicate device detected: $deviceName (endpointId: $endpointId)")
                        if (!existingPeer.amIConnected) {
                            // Update the IP (endpointId) in case it's a re-discovery
                            existingPeer.ip = endpointId
                            adapter.notifyDataSetChanged()
                        }
                    } else {
                        // Add as a new peer if not already discovered
                        discoveredPeers.add(ChatGlobal(endpointId, 8000, false, amIConnected = false, deviceName = deviceName))
                        adapter.notifyDataSetChanged()
                        Log.d("LogArda", "New device added: $deviceName (endpointId: $endpointId)")
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    //discoveredPeers.removeAll { !it.amIConnected && it.ip == endpointId }
                    //adapter.notifyDataSetChanged()
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
            connectionTimeoutHandler.removeCallbacksAndMessages(null)
            if (result.status.isSuccess) {
                // 1) Mark the endpoint as connected locally
                hideLoadingBar()
                sharedViewModel.addConnection(endpointId)
                discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = true

                // 2) Log success
                Log.d("LogArda", "Successfully connected to $endpointId")

                // 3) Retrieve the friendly name (if any)
                val endpointName = discoveredPeers.firstOrNull { it.ip == endpointId }?.deviceName
                    ?: "Unknown Device"
                Log.d("LogArda", "Successfully connected to $endpointName")
                discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = true
                sharedViewModel.addMessagesPrivacy(endpointName,false)
                sharedViewModel.mapNameEndpoint[endpointId] = endpointName
                // 4) Share existing connections with the newly connected peer
                shareConnectionsWithPeer(endpointId)

                // 5) Request connections from that peer (to build a graph, if you need it)
                requestConnections(endpointId)
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                //displayGraph(deviceName)

                // 6) Immediately navigate to the chat
                (activity as? ChatSessionsActivity)?.navigateToChatFragment(endpointId, endpointName)
            } else {
                Log.e("LogArda", "Failed to connect to $endpointId")
                discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = false
                hideLoadingBar()
                Toast.makeText(context, "Connection rejected2", Toast.LENGTH_SHORT).show()
            }
        }


        override fun onDisconnected(endpointId: String) {
            Log.d("LogArda", "Disconnected from $endpointId")
            sharedViewModel.removeConnection(endpointId)
            //Log.d("LogArda", "Disconnected from $endpointId")
            discoveredPeers.firstOrNull { it.ip == endpointId }?.amIConnected = false

            discoveredPeers.find { it.ip == endpointId }?.let {
                it.amIConnected = false
            }
            adapter.notifyDataSetChanged()
            connectionsClient.stopDiscovery()
            startDiscovery()

            // 2) Log
           //Log.d("LogArda", "Disconnected from $endpointId")

            // 3) Post a "disconnected" message to the chat
            val systemMessage = Message(
                text = "User $endpointId has disconnected.",
                isSentByMe = false,
                timestamp = System.currentTimeMillis(),
                type = "system",
                nick = "System",       // or any nickname you want to indicate it's a system message
                ip = endpointId,       // or "N/A" if you prefer
                from = "System"        // so ChatFragment knows it's a system message
            )
            sharedViewModel.postMessage(systemMessage)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            val missingPermissions = permissions.filter {
                ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                requestPermissions(missingPermissions.toTypedArray(), 1001)
            } else {
                Log.d("LogArda", "All Bluetooth permissions already granted")
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
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val deviceInfo = JSONObject().apply {
            put("deviceId", deviceUUID)
            put("connections", JSONArray(sharedViewModel.connectedEndpoints.value))
            put("from",deviceName)
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
        Log.d("GraphVisual", " ".repeat(level * 2) + node.deviceId)
        node.connections.forEach { renderNode(it, level + 1) }
    }
    private fun refreshNearbyConnections() {
        Log.d("Nearby313131313131313", "Refreshing Nearby Connections...")

        // Stop all existing connections
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()

        // Clear UI lists
        discoveredPeers.clear()
        sharedViewModel.clearConnections()
        adapter.notifyDataSetChanged()

        // Restart advertising and discovery
        startAdvertising()
        startDiscovery()
        hideLoadingBar()
    }
    private fun showLoadingBar() {
        activity?.runOnUiThread {
            connectionProgressBar.visibility = View.VISIBLE
        }
    }
    private fun hideLoadingBar() {
        activity?.runOnUiThread {
            connectionProgressBar.visibility = View.GONE
        }
    }

    private fun attemptConnectionWithTimeout(endpointId: String) {
        // Start the connection request
        connectionsClient.requestConnection(endpointId, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                Log.d("LogArda", "Connection request sent to $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e("LogArda", "Failed to request connection: ${e.message}")
                hideLoadingBar()
                Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                connectionsClient.stopDiscovery()
                startDiscovery()
            }

        // Start the timeout countdown
        connectionTimeoutHandler.postDelayed({
            val peer = discoveredPeers.find { it.ip == endpointId }
            if (peer != null && !peer.amIConnected) {
                Log.w("LogArda", "Connection to $endpointId timed out.")
                //connectionsClient.stopAllEndpoints()
                peer.amIConnected = false
                adapter.notifyDataSetChanged()
                hideLoadingBar()
                Toast.makeText(context, "Connection to ${peer.deviceName} timed out.", Toast.LENGTH_SHORT).show()
            }
        }, CONNECTION_TIMEOUT_MS)
    }
    private fun showDisconnectDialog(peer: ChatGlobal) {
        AlertDialog.Builder(requireContext())
            .setTitle("Disconnect")
            .setMessage("Do you want to disconnect from ${peer.deviceName}?")
            .setPositiveButton("Yes") { _, _ ->
                disconnectPeer(peer)
            }
            .setNegativeButton("No", null)
            .setCancelable(true)
            .show()
    }

    private fun disconnectPeer(peer: ChatGlobal) {
        if (peer.amIConnected) {
            connectionsClient.disconnectFromEndpoint(peer.ip)
            peer.amIConnected = false
            adapter.notifyDataSetChanged()
            // Remove from shared ViewModel
            sharedViewModel.removeConnection(peer.ip)

            Toast.makeText(context, "${peer.deviceName} disconnected.", Toast.LENGTH_SHORT).show()
            Log.d("ChatListFragment", "${peer.deviceName} disconnected successfully.")
        } else {
            Toast.makeText(context, "${peer.deviceName} is already disconnected.", Toast.LENGTH_SHORT).show()
            Log.w("ChatListFragment", "Attempted to disconnect ${peer.deviceName}, but it was already disconnected.")
        }
    }



}
