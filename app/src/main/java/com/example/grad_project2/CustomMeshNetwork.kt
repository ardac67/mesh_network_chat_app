package com.example.grad_project2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.Timer
import java.util.TimerTask

class CustomMeshNetwork(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel
) {

    private val routingTable = mutableMapOf<String, RoutingEntry>()
    private val neighbors = mutableSetOf<String>() // Store neighbor IPs
    private var peerDiscoveryListener: ((List<WifiP2pDevice>) -> Unit)? = null

    private val peerDiscoveryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
                Log.d("CustomMeshNetwork", "WIFI_P2P_PEERS_CHANGED_ACTION triggered")
                if (context?.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    wifiP2pManager.requestPeers(channel) { peerList ->
                        val peers = peerList.deviceList.toList()
                        Log.d("CustomMeshNetwork", "Discovered peers: ${peers.size}")
                        peerDiscoveryListener?.invoke(peers) // Notify listener with discovered peers
                    }
                } else {
                    Log.e("CustomMeshNetwork", "Permission denied for accessing peers")
                }
            }
        }
    }


    init {
        // Register the broadcast receiver for peer discovery
        val intentFilter = IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        context.registerReceiver(peerDiscoveryReceiver, intentFilter)

        // Start the server thread for receiving messages
        startServer()
        startRouteMaintenance()
    }

    // Data class for routing entries
    data class RoutingEntry(
        val destination: String,
        val nextHop: String,
        val cost: Int
    )
    fun setPeerDiscoveryListener(listener: (List<WifiP2pDevice>) -> Unit) {
        peerDiscoveryListener = listener
    }

    // Discover peers
    fun discoverPeers() {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("CustomMeshNetwork", "Peer discovery started")
                    requestPeers() // Request peers after starting discovery
                }

                override fun onFailure(reason: Int) {
                    Log.e("CustomMeshNetwork", "Peer discovery failed: $reason")
                    peerDiscoveryListener?.invoke(emptyList()) // Notify empty list on failure
                }
            })
        } else {
            Log.e("CustomMeshNetwork", "Permission denied for discovering peers")
            peerDiscoveryListener?.invoke(emptyList()) // Notify empty list if permission denied
        }
    }

    // Private function to request the list of peers
    private fun requestPeers() {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            wifiP2pManager.requestPeers(channel) { peerList ->
                val peers = peerList.deviceList.toList()
                Log.d("CustomMeshNetwork", "Discovered peers: ${peers.size}")
                peerDiscoveryListener?.invoke(peers) // Notify listener with discovered peers
            }
        } else {
            Log.e("CustomMeshNetwork", "Permission not granted for requesting peers")
            peerDiscoveryListener?.invoke(emptyList()) // Notify empty list if permission is missing
        }
    }


    // Connect to a peer
    fun connectToPeer(device: WifiP2pDevice) {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
            }

            wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("MeshNetwork", "Connection initiated")
                }

                override fun onFailure(reason: Int) {
                    Log.e("MeshNetwork", "Connection failed: $reason")
                }
            })
        } else {
            Log.e("MeshNetwork", "Permission denied for connecting to peers")
        }
    }

    // Start server to listen for incoming messages
    private fun startServer() {
        Thread {
            try {
                val serverSocket = ServerSocket(8888)
                while (true) {
                    val socket = serverSocket.accept()
                    val input = socket.getInputStream().bufferedReader().readLine()
                    Log.d("MeshNetwork", "Received: $input")
                    handleIncomingMessage(input)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // Send a message to a specific IP
    fun sendMessage(ip: String, message: String) {
        Thread {
            try {
                val socket = Socket(ip, 8888)
                val output = socket.getOutputStream()
                output.write((message + "\n").toByteArray())
                output.flush()
                socket.close()
                Log.d("MeshNetwork", "Message sent: $message")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // Broadcast routing table to all neighbors
    private fun broadcastRoutingTable() {
        val routingUpdate = JSONObject().apply {
            put("type", "routing_update")
            val routes = JSONArray()
            routingTable.forEach { (dest, entry) ->
                val route = JSONObject().apply {
                    put("destination", dest)
                    put("nextHop", entry.nextHop)
                    put("cost", entry.cost)
                }
                routes.put(route)
            }
            put("routes", routes)
        }

        neighbors.forEach { neighborIp ->
            sendMessage(neighborIp, routingUpdate.toString())
        }
    }

    // Handle incoming routing updates
    private fun handleRoutingUpdate(jsonMessage: String) {
        val message = JSONObject(jsonMessage)
        val routes = message.getJSONArray("routes")
        for (i in 0 until routes.length()) {
            val route = routes.getJSONObject(i)
            val destination = route.getString("destination")
            val nextHop = route.getString("nextHop")
            val cost = route.getInt("cost") + 1

            val currentEntry = routingTable[destination]
            if (currentEntry == null || cost < currentEntry.cost) {
                routingTable[destination] = RoutingEntry(destination, nextHop, cost)
            }
        }
    }

    // Handle incoming messages
    private fun handleIncomingMessage(jsonMessage: String) {
        val message = JSONObject(jsonMessage)
        val destination = message.getString("destination")
        val data = message.getString("data")

        if (destination == "DeviceID") { // Replace with this device's ID
            Log.d("MeshNetwork", "Message for me: $data")
        } else {
            forwardMessage(jsonMessage)
        }
    }

    // Forward a message to the next hop
    private fun forwardMessage(jsonMessage: String) {
        val message = JSONObject(jsonMessage)
        val destination = message.getString("destination")
        val nextHop = routingTable[destination]?.nextHop

        if (nextHop != null) {
            sendMessage(nextHop, jsonMessage)
        } else {
            Log.e("MeshNetwork", "No route to destination: $destination")
        }
    }

    // Periodically broadcast routing tables and clean up stale routes
    private fun startRouteMaintenance() {
        val routeTimer = Timer()
        routeTimer.schedule(object : TimerTask() {
            override fun run() {
                broadcastRoutingTable()
                cleanUpRoutes()
            }
        }, 0, 5000)
    }

    // Clean up stale routes
    private fun cleanUpRoutes() {
        val iterator = routingTable.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            // Add logic to determine stale routes
        }
    }
}

