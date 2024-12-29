package com.example.grad_project2.model

data class Message(
    val text: String,
    val isSentByMe: Boolean,
    val timestamp: Long,
    val type: String,
    val nick: String,
    val ip: String,
    val from: String? = null,
    val id:String? = null,
    val relayedFrom:String? =null
)
