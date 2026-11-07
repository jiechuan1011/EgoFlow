package com.egoflow.app.data.dao

import androidx.room.*
import com.egoflow.app.data.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE session_date = :date ORDER BY timestamp ASC")
    fun getByDate(date: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT DISTINCT session_date FROM chat_messages ORDER BY session_date DESC")
    fun getSessionDates(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ChatMessageEntity)

    @Delete
    suspend fun delete(msg: ChatMessageEntity)
}
