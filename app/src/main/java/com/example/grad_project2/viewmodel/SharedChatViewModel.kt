package com.example.grad_project2.viewmodel


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.grad_project2.model.ChatGlobal
import com.example.grad_project2.model.Message

class SharedChatViewModel : ViewModel() {
    private val _incomingMessage = MutableLiveData<Message>()
    val incomingMessage: LiveData<Message> get() = _incomingMessage
    private val _connectedEndpoints = MutableLiveData<Set<String>>(emptySet())
    val connectedEndpoints: LiveData<Set<String>> get() = _connectedEndpoints
    var messagesPrivate :MutableMap<String,Boolean> = mutableMapOf()
    val relayedMessages = mutableSetOf<String>()
    var publicConnections = mutableSetOf<String>()
    var mapNameEndpoint : MutableMap<String,String> = mutableMapOf()

    fun addConnection(value: String) {
        // Create a new set (immutable) that adds the new value
        val currentSet = _connectedEndpoints.value ?: emptySet()
        _connectedEndpoints.value = currentSet + value
    }
    fun postMessage(message: Message) {
        _incomingMessage.postValue(message)
    }
    fun removeConnection(value: String) {
        val currentSet = _connectedEndpoints.value ?: emptySet()
        _connectedEndpoints.value = currentSet - value
    }
    fun clearConnections(){
        _connectedEndpoints.postValue(emptySet())
    }
    fun setMessagesPrivacy(key: String, mode: String) {
        messagesPrivate[key] = when (mode.lowercase()) {
            "public" -> true
            "private" -> false
            else -> throw IllegalArgumentException("Invalid mode: Use 'public' or 'private'")
        }
    }

    // Get privacy for a specific key
    fun getMessagesPrivacy(key: String): Boolean? {
        return messagesPrivate.get(key)// Default to false if the key doesn't exist
    }

    // Remove a specific privacy setting
    fun removeMessagesPrivacy(key: String) {
        messagesPrivate.remove(key)
    }

    // Check if a key exists in privacy settings
    fun hasMessagesPrivacy(key: String): Boolean {
        return messagesPrivate.containsKey(key)
    }
    fun addMessagesPrivacy(key: String, value: Boolean) {
        messagesPrivate[key] = value
        println("Added/Updated privacy setting: $key -> $value")
    }
}
