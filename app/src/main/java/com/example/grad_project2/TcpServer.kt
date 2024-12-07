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
    private val port: Int,
    private val onMessageReceived: (ip: String, port: Int, message: String) -> Unit
) {

    private var serverSocket: ServerSocket? = null
    private val clients = mutableListOf<Socket>()

    fun startServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                Log.d("TcpServer", "Server started on port $port")

                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    Log.d("TcpServer", "Client connected: ${client.inetAddress.hostAddress}:${client.port}")
                    clients.add(client)

                    // Handle client in a separate coroutine
                    launch {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpServer", "Error: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)

                // Send a welcome message
                val welcomeMsg = JSONObject().apply {
                    put("message", "Welcome! You are connected to the server.")
                }.toString()
                writer.println(welcomeMsg)

                var line: String = ""
                while (client.isConnected && reader.readLine().also { line = it } != null) {
                    Log.d("TcpServer", "Received: $line from ${client.inetAddress.hostAddress}:${client.port}")

                    // Parse JSON message
                    try {
                        val json = JSONObject(line!!)
                        val message = json.getString("message")
                        onMessageReceived(client.inetAddress.hostAddress, client.port, message)

                        // Send acknowledgment
                        val response = JSONObject().apply {
                            put("status", "Message received.")
                        }.toString()
                        writer.println(response)
                    } catch (e: Exception) {
                        Log.e("TcpServer", "Invalid JSON format: ${e.message}")
                        val errorMsg = JSONObject().apply {
                            put("error", "Invalid JSON format.")
                        }.toString()
                        writer.println(errorMsg)
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpServer", "Client connection error: ${e.message}")
            } finally {
                Log.d("TcpServer", "Client disconnected: ${client.inetAddress.hostAddress}:${client.port}")
                clients.remove(client)
                client.close()
            }
        }
    }

    fun stopServer() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                clients.forEach { it.close() }
                clients.clear()
                Log.d("TcpServer", "Server stopped on port $port")
            } catch (e: Exception) {
                Log.e("TcpServer", "Error stopping server: ${e.message}")
            }
        }
    }
}
