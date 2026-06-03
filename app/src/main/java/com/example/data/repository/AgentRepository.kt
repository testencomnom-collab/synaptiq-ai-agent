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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val configCache = mutableMapOf<String, AgentConfigEntity>()
    private val configMutex = Mutex()

    suspend fun saveAgentConfig(config: AgentConfigEntity) {
        agentConfigDao.insertConfig(config)
        configMutex.withLock { configCache[config.id] = config }
    }

    suspend fun getAgentConfig(id: String): AgentConfigEntity? {
        configMutex.withLock {
            if (configCache.containsKey(id)) return configCache[id]
        }
        val config = agentConfigDao.getConfigById(id)
        if (config != null) {
            configMutex.withLock { configCache[id] = config }
        }
        return config
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

    val allMemoriesFlow: Flow<List<MemoryEntity>> = memoryDao.getMemoriesFlow()

    private var memoryCache: List<MemoryEntity>? = null
    private val memoryMutex = Mutex()

    suspend fun getAllMemories(): List<MemoryEntity> {
        memoryMutex.withLock {
            if (memoryCache != null) return memoryCache!!
        }
        val memories = memoryDao.getAllMemories()
        memoryMutex.withLock { memoryCache = memories }
        return memories
    }

    suspend fun insertMemory(memory: MemoryEntity) {
        memoryDao.insertMemory(memory)
        memoryMutex.withLock { memoryCache = null } // Invalidate cache
    }

    suspend fun deleteMemory(id: Int) {
        memoryDao.deleteMemoryById(id)
        memoryMutex.withLock { memoryCache = null } // Invalidate cache
    }

    suspend fun clearMemories() {
        memoryDao.clearMemories()
        memoryMutex.withLock { memoryCache = null } // Invalidate cache
    }
}
