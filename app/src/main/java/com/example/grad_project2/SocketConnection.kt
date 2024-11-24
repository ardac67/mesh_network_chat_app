package com.example.grad_project2
import java.net.InetAddress
import java.net.Socket
import android.util.Log
import android.util.Patterns
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress

typealias BroadcastListener = (ip: String, ports: List<Int>, senderIp: String) -> Unit

class SocketConnection(private val scope: CoroutineScope) {
    fun serverConnectionTest(ip: String, port: Int) {
         if (!isValidIpAddress(ip)) {
             Log.e("SocketError", "Invalid IP Address")
             return
         }
        scope.launch(Dispatchers.IO) {
            try {
                // Create a socket to connect to the server
                val socket = Socket(ip, port)

                // Send a message to the server
                val message = "Hello from Android"
                val outputStream: OutputStream = socket.getOutputStream()
                outputStream.write(message.toByteArray())
                outputStream.flush()

                // Receive response from the server
                val inputStream = socket.getInputStream()
                val response = inputStream.bufferedReader().readLine()
                Log.d("SocketResponse", "Response: $response")

                // Close the connection
                socket.close()
            } catch (e: Exception) {
                Log.e("SocketError", "Error: ${e.message}")
            }
         }
    }
    fun isValidIpAddress(ip: String): Boolean {
        return Patterns.IP_ADDRESS.matcher(ip).matches()
    }

    fun listenForBroadcasts(onBroadcastReceived: BroadcastListener) {
        scope.launch(Dispatchers.IO)  {
            try {
                val socket = DatagramSocket(8888, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (true) {
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress

                    Log.d("Broadcast", "Received: $message from $senderIp")

                    // Parse the JSON message
                    try {
                        val jsonObject = JSONObject(message)
                        //val ip = jsonObject.getString("ip")
                        val ip = senderIp // if broadcast made from 0.0.0.0 senderIp is enough
                        val portsArray = jsonObject.getJSONArray("ports")
                        val ports = mutableListOf<Int>()
                        for (i in 0 until portsArray.length()) {
                            ports.add(portsArray.getInt(i))
                        }

                        Log.d("Broadcast", "IP: $ip, Ports: $ports")

                        // Pass the data back via the callback on the main thread
                        withContext(Dispatchers.Main) {
                            onBroadcastReceived(ip, ports, senderIp)
                        }
                    } catch (e: Exception) {
                        Log.e("JSONError", "Failed to parse JSON: ${e.message}")
                    }

                    // Remove the break to continue listening indefinitely
                    // break
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun subscribeToSession(
        ip: String,
        port: Int,
        connectTimeoutMillis: Int = 5000, // 5 seconds connection timeout
        onMessageReceived: (String) -> Unit,
        onSubscriptionSuccess: () -> Unit,
        onSubscriptionFailed: (Exception) -> Unit
    ): Job? {
        if (!isValidIpAddress(ip)) {
            Log.e("SocketError", "Invalid IP Address: $ip")
            onSubscriptionFailed(Exception("Invalid IP Address"))
            return null
        }

        return scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                // Initialize socket
                socket = Socket()
                val socketAddress = InetSocketAddress(ip, port)
                Log.d("SocketConnection", "Attempting to connect to $ip:$port with timeout $connectTimeoutMillis ms")

                // Attempt to connect with timeout
                socket.connect(socketAddress, connectTimeoutMillis)
                Log.d("SocketConnection", "Successfully connected to $ip:$port")

                // Notify subscription success on the main thread
                withContext(Dispatchers.Main) {
                    onSubscriptionSuccess()
                }

                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))

                // Continuously read data from the socket
                while (isActive) {
                    val message = reader.readLine()
                    if (message == null) {
                        // Connection closed by the server
                        Log.d("SocketConnection", "Connection closed by server: $ip:$port")
                        break
                    } else {
                        Log.d("SocketMessage", "Received from $ip:$port - $message")
                        withContext(Dispatchers.Main) {
                            onMessageReceived(message)
                        }
                    }
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.e("SocketError", "Connection timed out to $ip:$port")
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
                    Log.e("SocketError", "Error closing socket for $ip:$port - ${e.message}")
                }
            }
        }
    }
    /*
    fun subscribeToSession(ip: String, port: Int, onMessageReceived: (String) -> Unit): Job? {
        if (!isValidIpAddress(ip)) {
            Log.e("", "Invalid IP Address")
            return null
        }
        return  scope.launch(Dispatchers.IO)  {
            var socket: Socket? = null
            try {
                socket = Socket(ip, port)
                val inputStream = socket.getInputStream()
                val reader = inputStream.bufferedReader()

                // Continuously read data from the socket
                while (isActive) {
                    val message = reader.readLine()
                    if (message == null) {
                        // Connection closed by the server
                        Log.d("SocketConnection", "Connection closed by server")
                        break
                    } else {
                        Log.d("SocketMessage", "Received: $message")
                        withContext(Dispatchers.Main) {
                            onMessageReceived(message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketError", "Error: ${e.message}")
            } finally {
                try {
                    socket?.close()
                    Log.d("SocketConnection", "Socket closed")
                } catch (e: Exception) {
                    Log.e("SocketError", "Error closing socket: ${e.message}")
                }
            }
        }
    }
     */
    /*
    fun listenForBroadcasts(onBroadcastReceived: (dataMap: HashMap<String, Any>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket(8888, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (true) {
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress

                    Log.d("Broadcast", "Received: $message from $senderIp")

                    // Parse the JSON message
                    try {
                        val jsonObject = JSONObject(message)
                        val ip = jsonObject.getString("ip")
                        val portsArray = jsonObject.getJSONArray("ports")
                        val ports = mutableListOf<Int>()
                        for (i in 0 until portsArray.length()) {
                            ports.add(portsArray.getInt(i))
                        }

                        // Create a HashMap with the data, including senderIp
                        val dataMap = hashMapOf<String, Any>(
                            "ip" to ip,
                            "ports" to ports,
                            "senderIp" to senderIp
                        )

                        // Invoke the callback with the dataMap
                        withContext(Dispatchers.Main) {
                            onBroadcastReceived(dataMap)
                        }
                    } catch (e: JSONException) {
                        Log.e("JSONError", "Failed to parse JSON: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    */
    /*
    fun listenForBroadcasts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket(8888, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (true) {
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress

                    Log.d("Broadcast", "Received: $message from $senderIp")

                    // Parse the JSON message
                    try {
                        val jsonObject = JSONObject(message)
                        val ip = jsonObject.getString("ip")
                        val portsArray = jsonObject.getJSONArray("ports")
                        val ports = mutableListOf<Int>()
                        for (i in 0 until portsArray.length()) {
                            ports.add(portsArray.getInt(i))
                        }

                        Log.d("Broadcast", "IP: $ip, Ports: $ports")

                        // Use the IP and ports to connect to the server
                        //for (port in ports) {
                            //connectToServer(ip, port)
                        //}
                    } catch (e: Exception) {
                        Log.e("JSONError", "Failed to parse JSON: ${e.message}")
                    }

                    // Break if you only need to connect once

                    break
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
     */
    /*
    fun listenForBroadcasts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket(8888, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (true) {
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    Log.d("Broadcast", "Received: $message from ${packet.address.hostAddress}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
     */

}

