package com.example.grad_project2.Database

class ChatRepository(private val chatDao: ChatDao) {

    suspend fun insertChat(chat: ChatEntity) {
        chatDao.insertChat(chat)
    }

    suspend fun getAllChats(): List<ChatEntity> {
        return chatDao.getAllChats()
    }

    suspend fun clearChats() {
        chatDao.deleteAllChats()
    }
}
