package com.example.grad_project2.viewmodel


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.grad_project2.model.Message

class SharedChatViewModel : ViewModel() {
    private val _incomingMessage = MutableLiveData<Message>()
    val incomingMessage: LiveData<Message> get() = _incomingMessage
    private val _connectedEndpoints = MutableLiveData<Set<String>>(emptySet())
    val connectedEndpoints: LiveData<Set<String>> get() = _connectedEndpoints

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
}
