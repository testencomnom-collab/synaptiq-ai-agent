package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.data.database.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.NotificationItem
import com.example.data.repository.AgentRepository
import com.example.services.ActionHandler
import com.example.services.AgentAccessibilityService
import com.example.services.CalendarManager
import com.example.services.LLMAgentService
import com.example.services.AgentEngineManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AgentRepository(db.chatDao(), db.notificationDao(), db.agentConfigDao(), db.memoryDao())
    val preferencesManager = PreferencesManager(application)
    
    private val agentService: LLMAgentService
        get() = AgentEngineManager.getService(getApplication(), preferencesManager, repository)

    private var tts: TextToSpeech? = null

    init {
        if (preferencesManager.isTtsEnabled) {
            initTts()
        }
    }

    fun initTts() {
        if (tts == null) {
            tts = TextToSpeech(getApplication(), this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language not supported!")
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

    val activeChatAgentId = MutableStateFlow("system")

    // Flow states
    val chatHistory: StateFlow<List<ChatMessage>> = activeChatAgentId
        .flatMapLatest { agentId -> repository.getMessages(agentId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val notifications: StateFlow<List<NotificationItem>> = repository.allNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val memories: StateFlow<List<com.example.data.model.MemoryEntity>> = repository.allMemoriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val isLoading = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)
    val isAutoReplyEnabled = MutableStateFlow(false)

    val activeProviderFlow = MutableStateFlow(preferencesManager.activeProvider)
    val activeModelFlow = MutableStateFlow(preferencesManager.getActiveModel())
    val downloadedAgentsFlow = MutableStateFlow(preferencesManager.downloadedLocalAgents)
    val activeAgentsFlow = MutableStateFlow(preferencesManager.activeLocalAgents)
    val agentLanguageFlow = MutableStateFlow(preferencesManager.agentLanguage)

    val downloadingAgents = MutableStateFlow<Map<String, Float>>(emptyMap())

    init {
        // No auto-seeding anymore per user request
    }

    fun setAgentLanguage(lang: String) {
        preferencesManager.agentLanguage = lang
        agentLanguageFlow.value = lang
    }

    fun clearStatus() {
        statusMessage.value = null
    }

    fun selectProvider(provider: String) {
        preferencesManager.activeProvider = provider
        // Reset to provider default models
        preferencesManager.selectedModel = ""
        activeProviderFlow.value = provider
        activeModelFlow.value = preferencesManager.getActiveModel()
    }

    fun updateApiKey(provider: String, key: String) {
        when (provider) {
            "OPENAI" -> preferencesManager.openAiApiKey = key
            "ANTHROPIC" -> preferencesManager.anthropicApiKey = key
            "GEMINI" -> preferencesManager.geminiApiKey = key
        }
        statusMessage.value = "$provider API Key saved successfully."
    }

    fun updateModel(modelName: String) {
        preferencesManager.selectedModel = modelName
        activeModelFlow.value = modelName
    }

    fun downloadAgent(agentId: String) {
        if (downloadingAgents.value.containsKey(agentId)) return

        viewModelScope.launch(Dispatchers.IO) {
            statusMessage.value = "Initialisiere Download eines echten On-Device Modells..."
            downloadingAgents.value = downloadingAgents.value.toMutableMap().apply { put(agentId, 0f) }
            
            // Standard MediaPipe Gemma 2B INT4 URL
            val modelUrl = "https://huggingface.co/metsman/gemma-2b-it-cpu-int4-org/resolve/main/gemma-2b-it-cpu-int4.bin?download=true"
            val modelFileName = "gemma_2b_it_cpu_int4.bin"
            val modelFile = java.io.File(getApplication<Application>().filesDir, modelFileName)
            
            var requestSuccess = false
            
            // Delete file if it's a small corrupted file (e.g. Git LFS pointer < 1 GB)
            if (modelFile.exists() && modelFile.length() < 1000L * 1024L * 1024L) {
                modelFile.delete()
            }
            
            if (!modelFile.exists()) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(3000, java.util.concurrent.TimeUnit.SECONDS) // very high timeout for multi-GB model
                        .build()
                        
                    val request = okhttp3.Request.Builder().url(modelUrl).build()
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            val expectedBytes = body.contentLength()
                            val inputStream = body.byteStream()
                            val outputStream = java.io.FileOutputStream(modelFile)
                            
                            val buffer = ByteArray(8192 * 4)
                            var totalBytesRead = 0L
                            var bytesRead: Int
                            var lastUpdate = System.currentTimeMillis()
                            
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (expectedBytes > 0) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate > 300) { // Limit UI updates 
                                        val progress = totalBytesRead.toFloat() / expectedBytes.toFloat()
                                        downloadingAgents.value = downloadingAgents.value.toMutableMap().apply { put(agentId, progress) }
                                        lastUpdate = now
                                    }
                                }
                            }
                            outputStream.flush()
                            outputStream.close()
                            inputStream.close()
                            requestSuccess = true
                        }
                    } else {
                        Log.e("AgentViewModel", "Error HTTP Code: ${response.code}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("AgentViewModel", "Error downloading model", e)
                }
            } else {
                requestSuccess = true
            }

            // Cleanup downloading state
            downloadingAgents.value = downloadingAgents.value.toMutableMap().apply { remove(agentId) }

            if (requestSuccess) {
                val agentModel = com.example.data.model.LocalAgentRepository.agents.find { it.id == agentId }
                if (agentModel != null) {
                    val config = com.example.data.model.AgentConfigEntity(
                        id = agentId,
                        name = agentModel.name,
                        category = agentModel.category,
                        systemPrompt = "You are ${agentModel.name}, a local on-device AI specialist in the field of ${agentModel.category}. Maintain confidence and precision.",
                        toolsAllowed = "NONE"
                    )
                    repository.saveAgentConfig(config)
                }
                
                withContext(Dispatchers.Main) {
                    val currentSet = preferencesManager.downloadedLocalAgents.toMutableSet()
                    currentSet.add(agentId)
                    preferencesManager.downloadedLocalAgents = currentSet
                    downloadedAgentsFlow.value = currentSet

                    val activeSet = preferencesManager.activeLocalAgents.toMutableSet()
                    activeSet.add(agentId)
                    preferencesManager.activeLocalAgents = activeSet
                    activeAgentsFlow.value = activeSet

                    statusMessage.value = "Success! ${agentModel?.name ?: agentId} is now accessing Gemma 2B 100% offline."
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusMessage.value = "Error: Local model could not be downloaded."
                    if (modelFile.exists()) {
                        modelFile.delete() // Clean partial file
                    }
                }
            }
        }
    }

    fun setAgentActive(agentId: String, isActive: Boolean) {
        viewModelScope.launch {
            val currentSet = preferencesManager.activeLocalAgents.toMutableSet()
            if (isActive) {
                currentSet.add(agentId)
            } else {
                currentSet.remove(agentId)
            }
            preferencesManager.activeLocalAgents = currentSet
            activeAgentsFlow.value = currentSet
        }
    }

    fun sendMessage(query: String) {
        if (query.trim().isEmpty() || isLoading.value) return

        AgentEngineManager.engineScope.launch {
            isLoading.value = true
            // Save user message to database
            val userMsg = ChatMessage(agentId = activeChatAgentId.value, role = "user", message = query)
            repository.insertMessage(userMsg)

            // Compile existing simulated notifications for context
            val currentNotifications = notifications.value

            // Call Agent Service
            val svc = agentService
            val proposal = svc.executeAgentQuery(activeChatAgentId.value, query, currentNotifications)

            // Build action data payload if applicable
            var actionDataJson: String? = null
            if (proposal.hasAction) {
                val actionObj = JSONObject().apply {
                    put("type", proposal.actionType)
                    if (proposal.emailRecipient != null) {
                        put("emailRecipient", proposal.emailRecipient)
                        put("emailSubject", proposal.emailSubject)
                        put("emailBody", proposal.emailBody)
                    }
                    if (proposal.calendarTitle != null) {
                        put("calendarTitle", proposal.calendarTitle)
                        put("calendarDesc", proposal.calendarDesc)
                        put("calendarStart", proposal.calendarStartMillis)
                        put("calendarEnd", proposal.calendarEndMillis)
                    }
                    if (proposal.systemActionApp != null) {
                        put("systemActionApp", proposal.systemActionApp)
                    }
                    if (proposal.systemActionRecipient != null) {
                        put("recipient", proposal.systemActionRecipient)
                    }
                    if (proposal.systemActionInstruction != null) {
                        put("instruction", proposal.systemActionInstruction)
                    }
                }
                actionDataJson = actionObj.toString()
            }

            // Save assistant response
            val assistantMsg = ChatMessage(
                agentId = activeChatAgentId.value,
                role = "assistant",
                message = proposal.responseText,
                thought = proposal.thought,
                hasAction = proposal.hasAction,
                actionType = proposal.actionType,
                actionData = actionDataJson,
                actionExecuted = false
            )
            repository.insertMessage(assistantMsg)
            
            if (preferencesManager.isTtsEnabled) {
                tts?.speak(proposal.responseText, TextToSpeech.QUEUE_FLUSH, null, "AgentTTSResponse")
            }
            
            isLoading.value = false
        }
    }

    fun executeProposedAction(message: ChatMessage) {
        if (!message.hasAction || message.actionData == null) return

        viewModelScope.launch {
            try {
                val json = JSONObject(message.actionData)
                val type = json.optString("type", "NONE")

                var calendarSuccess = true
                
                val rec = json.optString("emailRecipient")
                val subj = json.optString("emailSubject")
                val body = json.optString("emailBody")

                // Executing Calendar Block
                if (type == "CALENDAR" || type == "BOTH") {
                    calendarSuccess = ActionHandler.handleCalendarAction(getApplication(), json)
                }

                // Executing Email Block
                if (type == "EMAIL" || type == "BOTH") {
                    ActionHandler.handleEmailAction(getApplication(), json)
                }

                // Executing System Action Block
                if (type == "SYSTEM_ACTION" || type == "BOTH") {
                    ActionHandler.handleSystemAction(getApplication(), json)
                }

                // Mark action executed in DB if calendar succeeds or email composer launched
                if (calendarSuccess) {
                    repository.markActionExecuted(message.id)
                    
                    // Add secondary notification message to history
                    val updateMsg = ChatMessage(
                        agentId = message.agentId,
                        role = "assistant",
                        message = "🚀 Action Completed! " + when (type) {
                            "CALENDAR" -> "I scheduled the appointment in your on-device calendar."
                            "EMAIL" -> "I drafted your response to email recipient \"$rec\"."
                            "SYSTEM_ACTION" -> "I initiated the Deep System Automation workflow."
                            "BOTH" -> "I scheduled the calendar event AND prepared your email draft."
                            else -> "Action logged."
                        }
                    )
                    repository.insertMessage(updateMsg)
                }

            } catch (e: Exception) {
                Log.e("AgentViewModel", "Failed to execute proposed action", e)
                Toast.makeText(getApplication(), "Failed to execute schedule action: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addNotification(appName: String, sender: String, message: String) {
        AgentEngineManager.engineScope.launch {
            val notification = NotificationItem(appName = appName, sender = sender, message = message)
            val id = repository.insertNotification(notification).toInt()
            statusMessage.value = "New notification added from $sender."
            
            if (isAutoReplyEnabled.value) {
                // Auto-reply logic
                try {
            val svc = agentService
            val prompt = "The user has received the following notification from $sender (App: $appName):\n\"$message\"\nPlease write a short, direct, and appropriate reply to this notification on behalf of the user."
                    val replyText = svc.generateDirectReply(activeChatAgentId.value, prompt)
                    repository.updateNotificationReply(id, replyText)
                    statusMessage.value = "AI replied to $sender automatically."
                } catch (e: Exception) {
                    Log.e("AgentViewModel", "Failed to auto-reply", e)
                }
            }
        }
    }

    fun removeNotification(id: Int) {
        // Not implemented in DB directly? Let's just ignore or if we added a delete, use that.
        // Waiting, we can also add a delete notification to the repo
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearChatHistory(activeChatAgentId.value)
            statusMessage.value = "Chat history cleared."
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            repository.clearNotifications()
            statusMessage.value = "Notifications cleared."
        }
    }

    fun deleteMemory(id: Int) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearMemories()
            statusMessage.value = "Alle Erinnerungen gelöscht."
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
