package com.example.grad_project2

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: ListSessions
) : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // Check if Wi-Fi P2P is enabled
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                activity.isWifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                // Request the peer list
                manager.requestPeers(channel) { peers ->
                    activity.onPeersAvailable(peers.deviceList)
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                if (networkInfo?.isConnected == true) {
                    // We are connected, request connection info
                    manager.requestConnectionInfo(channel) { connectionInfo ->
                        activity.onConnectionInfoAvailable(connectionInfo)
                    }
                    Log.d("Arda Test", "Connected to P2P network")
                } else {
                    Log.d("Arda Test", "Disconnected from P2P network")
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                activity.updateThisDevice(device)
            }
        }
    }

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val refreshedPeers = peerList.deviceList

        if (refreshedPeers.isNotEmpty()) {
            // Handle the updated peer list
            Log.d("Arda Test", "Peers available: ${refreshedPeers.size}")
        } else {
            Log.d("Arda Test", "No peers found")
        }
    }


}
