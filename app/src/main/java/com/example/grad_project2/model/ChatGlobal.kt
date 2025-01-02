package com.example.grad_project2.model

data class ChatGlobal(
    var ip: String,
    val port: Int,
    val isHostMe: Boolean,
    val sessionName: String? = null,
    var amIConnected: Boolean = false,
    val deviceName : String ?= null,
    var lastMessage: String?= null
)
