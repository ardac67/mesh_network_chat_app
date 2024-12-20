// TcpServer.kt
package com.example.grad_project2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class TcpServer(
    private val scope: CoroutineScope,
    val port: Int,
    public var messageListener: OnMessageReceivedListener,
) {
    private var serverSocket: ServerSocket? = null
    private val clients = mutableListOf<ClientConnection>()

    fun startServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                Log.d("TcpServer", "Server started on port $port")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d("TcpServer", "Client connected: ${socket.inetAddress.hostAddress}:${socket.port}")
                    val clientConnection = ClientConnection(socket)
                    clients.add(clientConnection)

                    launch {
                        handleClient(clientConnection)
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpServer", "Error: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(clientConnection: ClientConnection) {
        withContext(Dispatchers.IO) {
            val client = clientConnection.socket
            val reader = clientConnection.reader
            val writer = clientConnection.writer

            // Welcome message
            //val welcomeMsg = JSONObject().apply {
                //put("message", "Welcome! You are connected to the server.")
            //}.toString()
            //writer.println(welcomeMsg)

            try {
                var line: String? = ""
                while (client.isConnected && reader.readLine().also { line = it } != null) {
                    line?.let {
                        Log.d("TcpServer", "Received: $it from ${client.inetAddress.hostAddress}:${client.port}")
                        try {
                            val json = JSONObject(it)
                            val message = json.getString("message")
                            val nick = json.getString("nick")
                            val ip  = json.getString("ip")
                            val msg = Message(
                                text = message,
                                isSentByMe = false,
                                timestamp = System.currentTimeMillis(),
                                type = "string",
                                nick = nick,
                                ip = ip
                            )
                            // Notify the listener (ChatActivity)
                            messageListener.onMessageReceived(msg)

                            // Optional: Send acknowledgment to the sender only
                            val response = JSONObject().apply {
                                put("message", "Message received.")
                                put("ip","1231")
                                put("nick","test")
                            }.toString()
                            writer.println(response)
                        } catch (e: Exception) {
                            Log.e("TcpServer", "Invalid JSON: ${e.message}")
                            val errorMsg = JSONObject().apply {
                                put("error", "Invalid JSON format.")
                            }.toString()
                            writer.println(errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpServer", "Client connection error: ${e.message}")
            } finally {
                Log.d("TcpServer", "Client disconnected: ${client.inetAddress.hostAddress}:${client.port}")
                clients.remove(clientConnection)
                client.close()
                // Notify about the disconnection
                val disconnectionMessage = Message(
                    text = "Client at ${client.inetAddress.hostAddress} disconnected.",
                    isSentByMe = false,
                    timestamp = System.currentTimeMillis(),
                    type = "system",
                    nick = "Server",
                    ip = client.inetAddress.hostAddress
                )
                Log.d("DisconnectMessage", "Client disconnected: $disconnectionMessage")
                messageListener.onMessageReceived(disconnectionMessage)

            }
        }
    }

    fun stopServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                clients.forEach { it.socket.close() }
                clients.clear()
                Log.d("TcpServer", "Server stopped on port $port")
            } catch (e: Exception) {
                Log.e("TcpServer", "Error stopping server: ${e.message}")
            }
        }
    }

    fun broadcastToClients(message: String) {
        val json = JSONObject().apply {
            put("message", message)
            put("ip",getLocalIpAddress())
        }.toString()
        scope.launch(Dispatchers.IO) {
            clients.forEach { client->
                try {
                    // Ensure the writer is not null and the socket is connected
                    if (client.socket.isConnected) {
                        client.writer.println(json)
                    } else {
                        Log.e("TcpServer", "Client socket is disconnected or writer is null: ${client.socket.inetAddress.hostAddress}")
                    }
                } catch (e: Exception) {
                    Log.e("TcpServer", "Error broadcasting to client: ${e.message}")
                }
            }
        }
    }

    private class ClientConnection(val socket: Socket) {
        val reader: BufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer: PrintWriter = PrintWriter(socket.getOutputStream(), true)
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
