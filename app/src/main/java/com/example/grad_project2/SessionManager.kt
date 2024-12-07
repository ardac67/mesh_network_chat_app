// SessionManager.kt
package com.example.grad_project2

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionManager(
    private val scope: CoroutineScope,
    private val ipAddress: String
) {

    private val tcpServers = mutableListOf<TcpServer>()
    private val activePorts = mutableListOf<Int>()

    // Aktif portların listesini güvenli şekilde al
    fun getActivePorts(): List<Int> {
        return synchronized(activePorts) { activePorts.toList() }
    }

    // Yeni bir oturum oluştur
    fun createSession(port: Int, onMessageReceived: (ip: String, port: Int, message: String) -> Unit) {
        // TCP sunucusunu başlat
        val tcpServer = TcpServer(scope, port, onMessageReceived)
        tcpServers.add(tcpServer)
        tcpServer.startServer()

        // Aktif portlara ekle
        synchronized(activePorts) {
            activePorts.add(port)
            Log.d("SessionManager", "Added port: $port")
        }
    }

    // Bir oturumu kaldır
    fun removeSession(port: Int) {
        // Belirli bir port için TCP sunucusunu bul ve durdur
        val server = tcpServers.find { it.port == port }
        server?.stopServer()
        tcpServers.remove(server)

        // Aktif portlardan çıkar
        synchronized(activePorts) {
            activePorts.remove(port)
            Log.d("SessionManager", "Removed port: $port")
        }
    }

    // Tüm oturumları durdur
    fun stopAllSessions() {
        // Tüm TCP sunucularını durdur
        tcpServers.forEach { it.stopServer() }
        tcpServers.clear()

        // Aktif portları temizle
        synchronized(activePorts) {
            activePorts.clear()
            Log.d("SessionManager", "Cleared all active ports")
        }
    }
}
