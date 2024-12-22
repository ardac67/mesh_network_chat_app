package com.example.grad_project2

import com.example.grad_project2.model.Message


interface OnMessageReceivedListener {
    fun onMessageReceived(message: Message)
}
