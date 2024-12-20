package com.example.grad_project2
import android.util.Log
import android.util.Patterns
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

class SocketConnection(private val scope: CoroutineScope) {
    private var outputWriter: PrintWriter? = null
    var socket: Socket? = null
    private var isSubscribed = false

    fun isValidIpAddress(ip: String): Boolean {
        return Patterns.IP_ADDRESS.matcher(ip).matches()
    }

    fun subscribeToSession(
        ip: String,
        port: Int,
        connectTimeoutMillis: Int = 5000,
        onMessageReceived: (String) -> Unit,
        onSubscriptionSuccess: () -> Unit,
        onSubscriptionFailed: (Exception) -> Unit
    ): Job? {
        if (isSubscribed) {
            Log.w("SocketConnection", "Already subscribed to session for $ip:$port")
            return null // Skip if already subscribed
        }

        if (!isValidIpAddress(ip)) {
            Log.e("SocketError", "Invalid IP Address: $ip")
            scope.launch(Dispatchers.Main) {
                onSubscriptionFailed(Exception("Invalid IP Address"))
            }
            return null
        }

        isSubscribed = true // Mark as subscribed to prevent further calls
        return scope.launch(Dispatchers.IO) {
            try {
                socket = Socket()
                val socketAddress = InetSocketAddress(ip, port)
                Log.d("SocketConnection", "Attempting to connect to $ip:$port")

                socket?.connect(socketAddress, connectTimeoutMillis)
                Log.d("SocketConnection", "Connected to $ip:$port")

                outputWriter = PrintWriter(socket!!.getOutputStream(), true)

                withContext(Dispatchers.Main) {
                    onSubscriptionSuccess()
                }

                val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                while (isActive) {
                    Log.d("isActive", "Received from $isActive")
                    val message = reader.readLine()
                    if (!message.isNullOrEmpty()) {
                        val json = JSONObject(message)
                        val justMessage = json.getString("message")
                        if(!justMessage.equals("Message received.")){
                            val messageNew = Message(
                                text = justMessage,
                                isSentByMe = false,
                                timestamp = System.currentTimeMillis(),
                                type = "string",
                                nick = "nick",
                                ip = "ip"
                            )
                            Log.d("SocketMessage", "Received from $ip:$port - $messageNew")
                            //withContext(Dispatchers.Main) {
                            //onMessageReceived(message)
                            //}
                            ListSessions.SocketConnectionManager.notifyListeners(this@SocketConnection, messageNew)
                        }

                    } else {
                        // If message is empty or null, it might mean server disconnected or closed the stream
                        continue
                    }
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.e("SocketError", "Timeout to $ip:$port")
                withContext(Dispatchers.Main) {
                    onSubscriptionFailed(e)
                }
            } catch (e: Exception) {
                Log.e("SocketError", "Error on $ip:$port - ${e.message}")
                withContext(Dispatchers.Main) {
                    onSubscriptionFailed(e)
                }
            } finally {
                try {
                    socket?.close()
                    Log.d("SocketConnection", "Socket closed for $ip:$port")
                } catch (e: Exception) {
                    Log.e("SocketError", "Error closing socket: ${e.message}")
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val json = JSONObject().apply {
            put("message", text)
            put("ip",getLocalIpAddress())
            put("nick","arda")
        }.toString()

        scope.launch(Dispatchers.IO) {
            outputWriter?.println(json)
            Log.d("TcpServer", "Sent: $json")
        }
    }
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val ip = address.hostAddress
                        if (!ip.contains(":")) { // Skip IPv6
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TcpServer", "Error getting local IP address: ${e.message}")
        }
        return "0.0.0.0" // Default fallback
    }
}
