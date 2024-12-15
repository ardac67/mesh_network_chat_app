package com.example.grad_project2

import kotlinx.coroutines.Job

data class ChatGlobal(
    val ip: String,
    val port: Int,
    val isHostMe: Boolean,
    val sessionName: String? = null
)
