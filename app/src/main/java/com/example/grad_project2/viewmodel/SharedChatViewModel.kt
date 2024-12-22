package com.example.grad_project2.viewmodel


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.grad_project2.model.Message

class SharedChatViewModel : ViewModel() {
    private val _incomingMessage = MutableLiveData<Message>()
    val incomingMessage: LiveData<Message> get() = _incomingMessage

    fun postMessage(message: Message) {
        _incomingMessage.postValue(message)
    }
}
