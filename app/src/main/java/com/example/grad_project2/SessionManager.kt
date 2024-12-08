// SessionManager.kt
package com.example.grad_project2
import kotlinx.coroutines.CoroutineScope
import com.example.grad_project2.ListSessions.ListSessions

class SessionManager(
    private val scope: CoroutineScope,
    private val ipAddress: String
) {
    private val tcpServers = mutableListOf<TcpServer>()

    fun getActivePorts(): List<Int> {
        return tcpServers.map { it.port }
    }

    fun createSession(port: Int, messageListener: OnMessageReceivedListener) {
        val tcpServer = TcpServer(scope, port, messageListener)
        tcpServers.add(tcpServer)
        tcpServer.startServer()

        // Store the newly created server as the shared server
        ListSessions.sharedTcpServer = tcpServer
    }

    fun stopAllSessions() {
        tcpServers.forEach { it.stopServer() }
        tcpServers.clear()
        ListSessions.sharedTcpServer = null
    }
}

