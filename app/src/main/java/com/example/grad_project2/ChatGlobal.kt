package com.example.grad_project2

import kotlinx.coroutines.Job

data class ChatGlobal(
    val ip: String,
    val port: Int,
    var isSubscribed: Boolean = false,
    var subscriptionJob: Job? = null,// Tracks the subscription coroutine
)
