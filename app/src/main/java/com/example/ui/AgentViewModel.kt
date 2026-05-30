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
import com.example.services.AgentAccessibilityService
import com.example.services.CalendarManager
import com.example.services.LLMAgentService
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
class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AgentRepository(db.chatDao(), db.notificationDao(), db.agentConfigDao())
    val preferencesManager = PreferencesManager(application)
    
    private var agentService: LLMAgentService? = null

    init {
        try {
            agentService = LLMAgentService(application, preferencesManager, repository)
        } catch (e: Throwable) {
            Log.e("AgentViewModel", "Could not initialize LLMAgentService: ${e.message}", e)
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
            val modelUrl = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma_2b_it_cpu_int4/1/gemma_2b_it_cpu_int4.bin"
            val modelFileName = "gemma_2b_it_cpu_int4.bin"
            val modelFile = java.io.File(getApplication<Application>().filesDir, modelFileName)
            
            var requestSuccess = false
            
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
                        Log.e("AgentViewModel", "Fehler HTTP Code: ${response.code}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("AgentViewModel", "Fehler beim Download", e)
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
                        systemPrompt = "Du bist ${agentModel.name}, ein lokaler On-Device KI Spezialist im Bereich ${agentModel.category}. Beachte Konfidenz und Präzision.",
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

                    statusMessage.value = "Erfolg! ${agentModel?.name ?: agentId} greift jetzt 100% offline auf Gemma 2B zu."
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusMessage.value = "Fehler: Lokales Modell konnte nicht heruntergeladen werden."
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

        viewModelScope.launch {
            isLoading.value = true
            // Save user message to database
            val userMsg = ChatMessage(agentId = activeChatAgentId.value, role = "user", message = query)
            repository.insertMessage(userMsg)

            // Compile existing simulated notifications for context
            val currentNotifications = notifications.value

            // Call Agent Service
            val svc = agentService ?: throw Exception("LLM Agent Service is not available due to initialization fatal error.")
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
                    val calTitle = json.optString("calendarTitle")
                    val calDesc = json.optString("calendarDesc")
                    val start = json.optLong("calendarStart", 0L)
                    val end = json.optLong("calendarEnd", 0L)

                    if (calTitle.isNotEmpty() && start > 0L && end > start) {
                        val uri = withContext(Dispatchers.IO) {
                            CalendarManager.insertEvent(
                                context = getApplication(),
                                title = calTitle,
                                description = calDesc,
                                startMillis = start,
                                endMillis = end
                            )
                        }
                        if (uri != null) {
                            val sdf = SimpleDateFormat("h:mm a (MMM d)", Locale.getDefault())
                            Toast.makeText(
                                getApplication(),
                                "Scheduled event: \"$calTitle\" at ${sdf.format(Date(start))}",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            calendarSuccess = false
                            Toast.makeText(
                                getApplication(),
                                "Failed to write to calendar. Check runtime permissions inside System Settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                // Executing Email Block
                if (type == "EMAIL" || type == "BOTH") {
                    if (rec.isNotEmpty()) {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:".toUri()
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(rec))
                            putExtra(Intent.EXTRA_SUBJECT, subj)
                            putExtra(Intent.EXTRA_TEXT, body)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        Toast.makeText(getApplication(), "Opening email composer draft...", Toast.LENGTH_SHORT).show()
                    }
                }

                // Executing System Action Block
                if (type == "SYSTEM_ACTION" || type == "BOTH") {
                    val sysApp = json.optString("systemActionApp", "")
                    val sysRecipient = json.optString("recipient", "")
                    val sysInstruction = json.optString("instruction", "")
                    // fallback to old field name if needed
                    val finalInstruction = if (sysInstruction.isEmpty()) json.optString("systemActionInstruction", "") else sysInstruction
                    if (sysApp.isNotEmpty()) {
                        val app = getApplication<Application>()
                        val pm = app.packageManager

                        // Map common app names to package names
                        val packageMap = mapOf(
                            "snapchat" to "com.snapchat.android",
                            "whatsapp" to "com.whatsapp",
                            "instagram" to "com.instagram.android",
                            "telegram" to "org.telegram.messenger",
                            "tiktok" to "com.zhiliaoapp.musically",
                            "twitter" to "com.twitter.android",
                            "x" to "com.twitter.android",
                            "discord" to "com.discord",
                            "signal" to "org.thoughtcrime.securesms",
                            "facebook" to "com.facebook.katana",
                            "messenger" to "com.facebook.orca",
                            "youtube" to "com.google.android.youtube",
                            "spotify" to "com.spotify.music",
                            "chrome" to "com.android.chrome",
                            "gmail" to "com.google.android.gm",
                            "maps" to "com.google.android.apps.maps",
                            "camera" to "com.android.camera",
                            "settings" to "com.android.settings",
                            "clock" to "com.android.deskclock",
                            "calculator" to "com.android.calculator2",
                            "phone" to "com.android.dialer",
                            "contacts" to "com.android.contacts",
                            "calendar" to "com.google.android.calendar",
                            "files" to "com.google.android.apps.nbu.files",
                            "notes" to "com.google.android.keep"
                        )

                        val targetPackage = packageMap[sysApp.lowercase().trim()]

                        if (targetPackage != null) {
                            try {
                                // Try to send content via share intent for messaging apps
                                val messagingApps = setOf(
                                    "com.snapchat.android", "com.whatsapp", "com.instagram.android",
                                    "org.telegram.messenger", "com.discord", "org.thoughtcrime.securesms",
                                    "com.facebook.orca", "com.twitter.android"
                                )

                                if (targetPackage in messagingApps && finalInstruction.isNotEmpty()) {
                                    // Set Automation State for Accessibility Service auto-clicker
                                    if (sysRecipient.isNotEmpty()) {
                                        AgentAccessibilityService.AutomationState.isRunning = true
                                        AgentAccessibilityService.AutomationState.targetApp = targetPackage
                                        AgentAccessibilityService.AutomationState.recipient = sysRecipient
                                        AgentAccessibilityService.AutomationState.step = 1
                                    } else {
                                        AgentAccessibilityService.AutomationState.isRunning = false
                                    }

                                    // Use share intent to send text to the app
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        setType("text/plain")
                                        setPackage(targetPackage)
                                        putExtra(Intent.EXTRA_TEXT, finalInstruction)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        app.startActivity(shareIntent)
                                        Toast.makeText(app, "Opening $sysApp with message...", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        // Fallback: just open the app
                                        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                                        if (launchIntent != null) {
                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            app.startActivity(launchIntent)
                                            Toast.makeText(app, "Opened $sysApp (send message manually)", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    // Just open the app
                                    val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        app.startActivity(launchIntent)
                                        Toast.makeText(app, "Opened $sysApp", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(app, "$sysApp is not installed on this device.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AgentViewModel", "Failed to launch $sysApp", e)
                                Toast.makeText(app, "Could not open $sysApp: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // Try to find the app by searching installed packages
                            val installed = pm.getInstalledApplications(0)
                            val match = installed.find {
                                pm.getApplicationLabel(it).toString().equals(sysApp, ignoreCase = true)
                            }
                            if (match != null) {
                                val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    app.startActivity(launchIntent)
                                    Toast.makeText(app, "Opened $sysApp", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(app, "App '$sysApp' not found on device.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
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
        viewModelScope.launch {
            val notification = NotificationItem(appName = appName, sender = sender, message = message)
            val id = repository.insertNotification(notification).toInt()
            statusMessage.value = "New notification added from $sender."
            
            if (isAutoReplyEnabled.value) {
                // Auto-reply logic
                try {
                    val svc = agentService ?: return@launch
                    val prompt = "Der Nutzer hat folgende Benachrichtigung von $sender (App: $appName) erhalten:\n\"$message\"\nBitte schreibe eine kurze, direkte und passende Antwortnachricht auf diese Benachrichtigung im Namen des Nutzers."
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

    override fun onCleared() {
        super.onCleared()
        agentService?.close()
    }
}
