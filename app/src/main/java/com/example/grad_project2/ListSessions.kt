package com.example.grad_project2
import ChatItemAdapter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import com.example.grad_project2.SocketConnection
import java.net.Socket

class ListSessions : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatItemAdapter
    private lateinit var progressBar : ProgressBar
    private val items = mutableListOf<ChatGlobal>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_sessions)

        progressBar = findViewById(R.id.progressBar)
        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val connection = SocketConnection(lifecycleScope) // could be lifecycle scope
        adapter = ChatItemAdapter(this,items,connection)
        recyclerView.adapter = adapter
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        // Start listening for broadcasts

        connection.listenForBroadcasts { ip, ports, senderIp ->
            // Check for duplicates
            val existingDescriptions = items.map { it.port }.toSet()
            val newItems = ports.mapNotNull { port ->
                val description = port
                if (!existingDescriptions.contains(description)) {
                    ChatGlobal(
                        ip = ip,
                        port = description.toInt()
                    )
                } else {
                    null // Skip if already exists
                }
            }

            if (newItems.isNotEmpty()) {
                items.addAll(newItems)
                adapter.notifyDataSetChanged()
            }
            runOnUiThread {
                if (progressBar.visibility == View.VISIBLE) {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }
    /* // look at this
    fun onSessionClicked(ip: String, port: Int,socketConnection:SocketConnection) {
        socketConnection.subscribeToSession(ip, port) { message ->
            // Handle the received message
            // For example, update the UI or notify the user
            Log.d("SessionMessage", "New message: $message")
        }
    }
     */
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_list_sessions)
//        val connection = SocketConnection()
//        //connection.serverConnectionTest("192.168.56.1",8080)
//        //val socketToList = connection.discoverAndScan("192.168.1", 1..1024)
//        //connection.listenForBroadcasts()
//        // Sample data
//
//        val items = listOf(
//            ChatGlobal("Item 1", "This is the first item"),
//            ChatGlobal("Item 2", "This is the second item"),
//            ChatGlobal("Item 3", "This is the third item")
//        )
//        /*
//        val discoveredItems = socketToList.flatMap { (ip, ports) ->
//            ports.map { port ->
//                ChatGlobal(
//                    title = "Device: $ip",
//                    description = "Open Port: $port"
//                )
//            }
//        }
//        */
//        // Find RecyclerView
//        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
//
//        // Set LayoutManager
//        recyclerView.layoutManager = LinearLayoutManager(this)
//
//        // Set Adapter
//        //recyclerView.adapter = ChatItemAdapter(discoveredItems)
//        recyclerView.adapter = ChatItemAdapter(items)
//
//    }
    override fun onDestroy() {
        super.onDestroy()
        // Optionally, cancel all active subscriptions
        items.forEach { session ->
            session.subscriptionJob?.cancel()
        }
    }
}