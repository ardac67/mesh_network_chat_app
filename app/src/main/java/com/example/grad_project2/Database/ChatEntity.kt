package com.example.grad_project2.Database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val chatId: Int = 0,
    val uuid: String? = null,
    val text: String,
    val ip: String,
    val sender: String? = null,
    val relayedFrom: String? = null,
    val nick:String?= null,
    val timestamp: Long = System.currentTimeMillis(),
    val to:String,
    val type:String?=null
)
