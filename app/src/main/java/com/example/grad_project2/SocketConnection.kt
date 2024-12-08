package com.example.grad_project2
import android.util.Log
import android.util.Patterns
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class SocketConnection(private val scope: CoroutineScope) {
    private var outputWriter: PrintWriter? = null
    private var socket: Socket? = null

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
        if (!isValidIpAddress(ip)) {
            Log.e("SocketError", "Invalid IP Address: $ip")
            scope.launch(Dispatchers.Main) {
                onSubscriptionFailed(Exception("Invalid IP Address"))
            }
            return null
        }
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
                        Log.d("SocketMessage", "Received from $ip:$port - $message")
                        withContext(Dispatchers.Main) {
                            onMessageReceived(message)
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
        }.toString()
        scope.launch(Dispatchers.IO) {
            outputWriter?.println(json)
            Log.d("SocketConnection", "Sent: $json")
        }
    }
}
