package com.example.grad_project2.model

data class ChatGlobal(
    val ip: String,
    val port: Int,
    val isHostMe: Boolean,
    val sessionName: String? = null,
    var amIConnected: Boolean = false
)
