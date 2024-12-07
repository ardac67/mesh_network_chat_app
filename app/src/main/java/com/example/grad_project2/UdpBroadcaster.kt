// UdpBroadcaster.kt
package com.example.grad_project2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpBroadcaster(
    private val ip: String,
    private val getPorts: () -> List<Int>, // Fonksiyon olarak port listesini al
    private val broadcastPort: Int = 8888,
    private val interval: Long = 5000, // 5 saniye
    private val deviceId: String
) {

    suspend fun startBroadcasting() {
        withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val broadcastAddress = InetAddress.getByName("0.0.0.0")

                while (isActive) {
                    val ports = getPorts() // Güncel port listesini al
                    Log.d("UdpBroadcaster", "Current ports: $ports") // Debug log

                    val jsonMsg = JSONObject().apply {
                        put("ip", ip)
                        put("ports", JSONArray(ports)) // Ports array olarak ekle
                        put("deviceId", deviceId)
                    }.toString()

                    val sendData = jsonMsg.toByteArray()
                    val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, broadcastPort)

                    socket.send(sendPacket)
                    Log.d("UdpBroadcaster", "Broadcast message sent: $jsonMsg")

                    delay(interval)
                }

                socket.close()
            } catch (e: Exception) {
                Log.e("UdpBroadcaster", "Failed to send broadcast: ${e.message}")
            }
        }
    }
}
