package com.example.grad_project2

data class Message(
    val text: String,
    val isSentByMe: Boolean,
    val timestamp: Long,
    val type: String // "string" for text messages
)
