package com.example.grad_project2.Database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatDao {
    @Insert
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY timestamp ASC")
    suspend fun getAllChats(): List<ChatEntity>

    @Query("""
    SELECT * FROM chats 
    WHERE sender = :peerName
    OR relayedFrom = :peerName 
    OR `to` = :peerName
    ORDER BY timestamp ASC
    LIMIT 10;
""")
    suspend fun getChatsWithPeer(peerName: String): List<ChatEntity>



    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()
}
