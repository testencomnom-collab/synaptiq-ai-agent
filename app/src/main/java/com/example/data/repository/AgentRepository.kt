package com.example.data.repository

import com.example.data.database.ChatDao
import com.example.data.database.NotificationDao
import com.example.data.model.ChatMessage
import com.example.data.model.NotificationItem
import kotlinx.coroutines.flow.Flow

import com.example.data.database.AgentConfigDao
import com.example.data.model.AgentConfigEntity
import com.example.data.database.MemoryDao
import com.example.data.model.MemoryEntity

class AgentRepository(
    private val chatDao: ChatDao,
    private val notificationDao: NotificationDao,
    private val agentConfigDao: AgentConfigDao,
    private val memoryDao: MemoryDao
) {
    val allNotifications: Flow<List<NotificationItem>> = notificationDao.getAllNotifications()

    fun getMessages(agentId: String): Flow<List<ChatMessage>> {
        return chatDao.getAllMessages(agentId)
    }

    suspend fun saveAgentConfig(config: AgentConfigEntity) {
        agentConfigDao.insertConfig(config)
    }

    suspend fun getAgentConfig(id: String): AgentConfigEntity? {
        return agentConfigDao.getConfigById(id)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        return chatDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ChatMessage) {
        chatDao.updateMessage(message)
    }

    suspend fun clearChatHistory(agentId: String) {
        chatDao.clearHistory(agentId)
    }

    suspend fun markActionExecuted(id: Int) {
        chatDao.markActionExecuted(id)
    }

    suspend fun insertNotification(notification: NotificationItem): Long {
        return notificationDao.insertNotification(notification)
    }

    suspend fun updateNotificationReply(id: Int, replyText: String) {
        notificationDao.updateReply(id, replyText)
    }

    suspend fun clearNotifications() {
        notificationDao.deleteAll()
    }

    suspend fun getAllMemories(): List<MemoryEntity> {
        return memoryDao.getAllMemories()
    }

    suspend fun insertMemory(memory: MemoryEntity) {
        memoryDao.insertMemory(memory)
    }
}
