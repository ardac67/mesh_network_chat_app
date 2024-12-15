package com.example.grad_project2

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ListSessions : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatItemAdapter
    private val discoveredPeers = mutableListOf<ChatGlobal>()

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var customMeshNetwork: CustomMeshNetwork

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_sessions)

        // UI setup
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ChatItemAdapter(this, discoveredPeers, object : OnSessionClickListener {
            override fun onSessionClicked(peer: ChatGlobal) {
                connectToPeer(peer)
            }
        })
        recyclerView.adapter = adapter

        // Initialize Wi-Fi P2P Manager and Mesh Network
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        customMeshNetwork = CustomMeshNetwork(this, wifiP2pManager, channel)

        // Request permissions and start peer discovery
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            startMeshNetwork()
        }
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
        customMeshNetwork.discoverPeers()

        // Automatically update UI with discovered peers
        customMeshNetwork.setPeerDiscoveryListener { peers ->
            Log.d("ListSessions", "Discovered ${peers.size} peers.")
            runOnUiThread { // Ensure UI update happens on the main thread'
                //discoveredPeers.clear() // Clear the existing list
                Log.d("ListSessions", "Updating UI with ${peers.size} new peers")
                //iscoveredPeers.clear()
                peers.forEach { peer ->
                    Log.d("ListSessions", "Peer: ${peer.deviceName} - ${peer.deviceAddress}")
                    // Add each discovered peer to the list
                    val chatItem = ChatGlobal(
                        ip = peer.deviceAddress,
                        port = 0,
                        isHostMe = false,
                        sessionName = peer.deviceName
                    )
                    discoveredPeers.add(
                        chatItem
                    )
                    adapter.notifyItemInserted(discoveredPeers.size - 1)
                }
                adapter.notifyDataSetChanged() // N otify the adapter to update the RecyclerView
            }
        }
    }

    private fun connectToPeer(peer: ChatGlobal) {
        // Example: Connect to a discovered peer
        val wifiP2pDevice = WifiP2pDevice().apply { deviceAddress = peer.ip }
        customMeshNetwork.connectToPeer(wifiP2pDevice)
        Toast.makeText(this, "Connecting to ${peer.sessionName}", Toast.LENGTH_SHORT).show()
    }
}
