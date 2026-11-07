package com.egoflow.app.data.repository

import com.egoflow.app.data.dao.ChatMessageDao
import com.egoflow.app.data.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class ChatRepository(private val chatDao: ChatMessageDao) {

    fun getAll(): Flow<List<ChatMessageEntity>> = chatDao.getAll()

    fun getByDate(date: String): Flow<List<ChatMessageEntity>> = chatDao.getByDate(date)

    fun getSessionDates(): Flow<List<String>> = chatDao.getSessionDates()

    suspend fun saveMessage(id: String, role: String, content: String, timestamp: Long) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
        chatDao.insert(ChatMessageEntity(id = id, role = role, content = content, timestamp = timestamp, sessionDate = date))
    }
}
