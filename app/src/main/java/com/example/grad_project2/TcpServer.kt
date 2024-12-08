// TcpServer.kt
package com.example.grad_project2

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TcpServer(
    private val scope: CoroutineScope,
    val port: Int,
    private val messageListener: OnMessageReceivedListener
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
            val welcomeMsg = JSONObject().apply {
                put("message", "Welcome! You are connected to the server.")
            }.toString()
            writer.println(welcomeMsg)

            try {
                var line: String? = ""
                while (client.isConnected && reader.readLine().also { line = it } != null) {
                    line?.let {
                        Log.d("TcpServer", "Received: $it from ${client.inetAddress.hostAddress}:${client.port}")
                        try {
                            val json = JSONObject(it)
                            val message = json.getString("message")
                            val msg = Message(
                                text = message,
                                isSentByMe = false,
                                timestamp = System.currentTimeMillis(),
                                type = "string"
                            )
                            // Notify the listener (ChatActivity)
                            messageListener.onMessageReceived(msg)

                            // Optional: Send acknowledgment to the sender only
                            val response = JSONObject().apply {
                                put("message", "Message received.")
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
        }.toString()
        scope.launch(Dispatchers.IO) {
            clients.forEach {
                it.writer.println(json)
            }
        }
    }

    private class ClientConnection(val socket: Socket) {
        val reader: BufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer: PrintWriter = PrintWriter(socket.getOutputStream(), true)
    }
}
