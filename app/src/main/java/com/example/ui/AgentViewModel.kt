package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.data.database.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.EmailItem
import com.example.data.repository.AgentRepository
import com.example.services.CalendarManager
import com.example.services.LLMAgentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AgentRepository(db.chatDao(), db.emailDao(), db.agentConfigDao())
    val preferencesManager = PreferencesManager(application)
    private val agentService = LLMAgentService(application, preferencesManager, repository)

    val activeChatAgentId = MutableStateFlow("system")

    // Flow states
    val chatHistory: StateFlow<List<ChatMessage>> = activeChatAgentId
        .flatMapLatest { agentId -> repository.getMessages(agentId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val emails: StateFlow<List<EmailItem>> = repository.allEmails.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val isLoading = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)

    val activeProviderFlow = MutableStateFlow(preferencesManager.activeProvider)
    val activeModelFlow = MutableStateFlow(preferencesManager.getActiveModel())
    val downloadedAgentsFlow = MutableStateFlow(preferencesManager.downloadedLocalAgents)
    val activeAgentsFlow = MutableStateFlow(preferencesManager.activeLocalAgents)

    init {
        // Seed some starter emails for the simulated Inbox if empty
        viewModelScope.launch {
            repository.allEmails.collect { list ->
                if (list.isEmpty()) {
                    seedInbox()
                }
            }
        }
    }

    private suspend fun seedInbox() {
        val seedMails = listOf(
            EmailItem(
                sender = "lucas.boss@company.com",
                recipient = "me@device.com",
                subject = "Quarterly Business Report & Update",
                body = "Hey there, I need you to look over the sales spreadsheets and let me know when you're available tomorrow for a quick meeting to finalize the Q3 figures."
            ),
            EmailItem(
                sender = "sarah.hr@company.com",
                recipient = "me@device.com",
                subject = "Onboarding Feedback Call",
                body = "Hi! Can we schedule a short 15-minute sync next Tuesday at around 3:00 PM to talk about your recent onboarding experience?"
            ),
            EmailItem(
                sender = "alex.designer@company.com",
                recipient = "me@device.com",
                subject = "Figma Draft Review Required",
                body = "Are you free today at 4:30 PM to jump on a quick huddle? I want to show you the new designs for the dashboard before shipping them."
            )
        )
        seedMails.forEach { repository.insertEmail(it) }
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
        viewModelScope.launch {
            statusMessage.value = "Downloading $agentId LLM weights (1.5 GB)..."
            // Simulate large binary download delay
            kotlinx.coroutines.delay(3000)
            statusMessage.value = "Installing MediaPipe Inference Engine..."
            kotlinx.coroutines.delay(1000)
            
            try {
                val dummyFile = java.io.File(getApplication<Application>().filesDir, "local_model_gemma_2b.bin")
                if (!dummyFile.exists()) {
                    dummyFile.writeText("DUMMY_WEIGHTS")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val currentSet = preferencesManager.downloadedLocalAgents.toMutableSet()
            currentSet.add(agentId)
            preferencesManager.downloadedLocalAgents = currentSet
            downloadedAgentsFlow.value = currentSet

            // Auto-activate the agent so it's immediately usable in chat
            val activeSet = preferencesManager.activeLocalAgents.toMutableSet()
            activeSet.add(agentId)
            preferencesManager.activeLocalAgents = activeSet
            activeAgentsFlow.value = activeSet

            // Persist the actual config profile in database (Simulated internet download)
            val agentModel = com.example.data.model.LocalAgentRepository.agents.find { it.id == agentId }
            if (agentModel != null) {
                val config = com.example.data.model.AgentConfigEntity(
                    id = agentId,
                    name = agentModel.name,
                    category = agentModel.category,
                    systemPrompt = "You are ${agentModel.name}, an expert in ${agentModel.category}. Be concise and helpful.",
                    toolsAllowed = "EMAIL,CALENDAR"
                )
                repository.saveAgentConfig(config)
            }
            
            statusMessage.value = "$agentId successfully installed locally."
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
        if (query.trim().isEmpty()) return

        viewModelScope.launch {
            isLoading.value = true
            // Save user message to database
            val userMsg = ChatMessage(agentId = activeChatAgentId.value, role = "user", message = query)
            repository.insertMessage(userMsg)

            // Compile existing simulated emails for context
            val currentEmails = emails.value

            // Call Agent Service
            val proposal = agentService.executeAgentQuery(activeChatAgentId.value, query, currentEmails)

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

        try {
            val json = JSONObject(message.actionData)
            val type = json.optString("type", "NONE")

            var calendarSuccess = true
            var emailIntended = false
            
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
                    val uri = CalendarManager.insertEvent(
                        context = getApplication(),
                        title = calTitle,
                        description = calDesc,
                        startMillis = start,
                        endMillis = end
                    )
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
                    emailIntended = true
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(rec))
                        putExtra(Intent.EXTRA_SUBJECT, subj)
                        putExtra(Intent.EXTRA_TEXT, body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    getApplication<Application>().startActivity(emailIntent)
                    Toast.makeText(getApplication(), "Opening email composer draft...", Toast.LENGTH_SHORT).show()
                }
            }

            // Mark action executed in DB if calendar succeeds or email composer launched
            if (calendarSuccess) {
                viewModelScope.launch {
                    repository.markActionExecuted(message.id)
                    
                    // Add secondary notification message to history
                    val updateMsg = ChatMessage(
                        agentId = message.agentId,
                        role = "assistant",
                        message = "🚀 Action Completed! " + when (type) {
                            "CALENDAR" -> "I scheduled the appointment in your on-device calendar."
                            "EMAIL" -> "I drafted your response to email recipient \"$rec\"."
                            "BOTH" -> "I scheduled the calendar event AND prepared your email draft."
                            else -> "Action logged."
                        }
                    )
                    repository.insertMessage(updateMsg)
                }
            }

        } catch (e: Exception) {
            Log.e("AgentViewModel", "Failed to execute proposed action", e)
            Toast.makeText(getApplication(), "Failed to execute schedule action: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun addEmailToInbox(sender: String, subject: String, body: String) {
        viewModelScope.launch {
            val email = EmailItem(sender = sender, recipient = "me@device.com", subject = subject, body = body)
            repository.insertEmail(email)
            statusMessage.value = "New mail added to simulated inbox from $sender."
        }
    }

    fun removeEmail(id: Int) {
        viewModelScope.launch {
            repository.deleteEmail(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearChatHistory(activeChatAgentId.value)
            statusMessage.value = "Chat history cleared."
        }
    }

    fun clearInbox() {
        viewModelScope.launch {
            repository.clearInbox()
            statusMessage.value = "Inbox cleared."
        }
    }
}
