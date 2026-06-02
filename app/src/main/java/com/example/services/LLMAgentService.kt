package com.example.services

import android.content.Context
import android.util.Log
import com.example.data.PreferencesManager
import com.example.data.api.AnthropicMessage
import com.example.data.api.AnthropicRequest
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.LLMServiceClient
import com.example.data.api.OpenAiMessage
import com.example.data.api.OpenAiRequest
import com.example.data.model.NotificationItem
import com.example.data.repository.AgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AgentProposal(
    val thought: String,
    val responseText: String,
    val hasAction: Boolean,
    val actionType: String, // "EMAIL", "CALENDAR", "SYSTEM_ACTION", "BOTH", "NONE"
    val emailRecipient: String? = null,
    val emailSubject: String? = null,
    val emailBody: String? = null,
    val calendarTitle: String? = null,
    val calendarDesc: String? = null,
    val calendarStartMillis: Long? = null,
    val calendarEndMillis: Long? = null,
    val systemActionApp: String? = null,
    val systemActionRecipient: String? = null,
    val systemActionInstruction: String? = null
)

class LLMAgentService(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val repository: AgentRepository
) {
    companion object {
        private const val TAG = "LLMAgentService"
    }
    private val localEngine by lazy { LocalInferenceEngine(context) }
    private var isLocalEngineReady = false

    suspend fun executeAgentQuery(agentId: String, userQuery: String, notificationsContext: List<NotificationItem>): AgentProposal = withContext(Dispatchers.IO) {
        val activeProvider = preferencesManager.activeProvider
        val apiKey = preferencesManager.getActiveApiKey()
        val model = preferencesManager.getActiveModel()

        // 1. HYBRID-ROUTING-SYSTEM: Weichenstellung
        val useLocal = (agentId != "system" && agentId.isNotEmpty())

        // PFAD B (Direkt): Lokaler Agent explizit ausgewählt
        if (useLocal) {
            return@withContext executeLocalFallback(agentId, userQuery, isApiFallback = false, fallbackReason = "")
        }

        // PFAD A: Cloud-Modus (OpenAI / Anthropic / Gemini)
        if (apiKey.trim().isEmpty()) {
            // Kein API-Key → Hybrid-Fallback auf lokales Modell
            Log.d(TAG, "No API key configured, falling back to local model")
            return@withContext executeLocalFallback(agentId, userQuery, isApiFallback = true, fallbackReason = "Kein API Key konfiguriert")
        }

        // API-Key vorhanden → Cloud-API versuchen
        try {
            val result = executeCloudQuery(agentId, userQuery, notificationsContext, activeProvider, apiKey, model)
            return@withContext result
        } catch (e: Exception) {
            // Cloud-API fehlgeschlagen → Hybrid-Fallback auf lokales Modell
            Log.e(TAG, "Cloud API failed, initiating Hybrid Fallback to local model", e)
            val reason = e.message ?: "Network error"
            return@withContext executeLocalFallback(agentId, userQuery, isApiFallback = true, fallbackReason = reason)
        }
    }

    /**
     * Führt die Anfrage über das lokale On-Device Modell (Gemma 2B via MediaPipe) aus.
     * Wird sowohl für explizit lokale Agenten als auch als Fallback bei API-Fehlern genutzt.
     */
    private suspend fun executeLocalFallback(agentId: String, userQuery: String, isApiFallback: Boolean, fallbackReason: String): AgentProposal {
        val logLabel = if (isApiFallback) "Hybrid Fallback" else agentId
        Log.d(TAG, "Routing to Local On-Device Mode: $logLabel")

        if (!isLocalEngineReady) {
            isLocalEngineReady = localEngine.initialize()
        }
        if (!isLocalEngineReady) {
            return AgentProposal(
                thought = "Lokale Modellgewichte fehlen oder Engine Error.",
                responseText = "Fehler: Das MediaPipe Modell ist nicht aktiv. Bitte lade das Modell in der Bibliothek herunter.",
                hasAction = false,
                actionType = "NONE"
            )
        }

        val activeAgentId = if (!isApiFallback) agentId else "system"
        val agentConfig = if (activeAgentId != "system") repository.getAgentConfig(activeAgentId) else null
        val sysPrompt = (agentConfig?.systemPrompt ?: context.getString(com.example.R.string.llm_system_prompt_local))

        val fullPrompt = "<start_of_turn>user\n${sysPrompt}\n\nUser: $userQuery\n<end_of_turn>\n<start_of_turn>model\n"

        val response = localEngine.generateResponse(fullPrompt)
        val thoughtMsg = if (isApiFallback) {
            "Cloud-API fehlgeschlagen ($fallbackReason). Automatischer Hybrid-Fallback auf 100% Offline-Ausführung (Gemma 2B)."
        } else {
            "PFAD B: 100% Offline-Ausführung (Gemma 2B). Keine Cloud-APIs oder Netzwerke genutzt."
        }
        return AgentProposal(
            thought = thoughtMsg,
            responseText = response.trim(),
            hasAction = false,
            actionType = "NONE"
        )
    }

    /**
     * Führt die Anfrage über die Cloud-API (OpenAI, Anthropic oder Gemini) aus.
     * Wirft eine Exception bei Fehlern, damit der Aufrufer den Hybrid-Fallback auslösen kann.
     */
    private suspend fun executeCloudQuery(
        agentId: String,
        userQuery: String,
        notificationsContext: List<NotificationItem>,
        activeProvider: String,
        apiKey: String,
        model: String
    ): AgentProposal {
        // 1. Gather Calendar data for next 7 days
        val now = Calendar.getInstance()
        val startMillis = now.timeInMillis
        val endCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        val endMillis = endCal.timeInMillis

        val localEvents = CalendarManager.fetchEvents(context, startMillis, endMillis)
        val calendarDetails = StringBuilder()
        if (localEvents.isEmpty()) {
            calendarDetails.append("No scheduled events in the next 7 days.\n")
        } else {
            val sdf = SimpleDateFormat("E, MMM d, yyyy h:mm a", Locale.getDefault())
            localEvents.forEach { event ->
                calendarDetails.append("- Title: \"${event.title}\" | Start: ${sdf.format(Date(event.startTime))} | End: ${sdf.format(Date(event.endTime))}")
                if (!event.description.isNullOrEmpty()) calendarDetails.append(" | Desc: ${event.description}")
                calendarDetails.append("\n")
            }
        }

        // Fetch Memory Facts
        val memories = repository.getAllMemories()
        val memoryDetails = java.lang.StringBuilder()
        if (memories.isEmpty()) {
            memoryDetails.append("No known facts yet.\n")
        } else {
            memories.forEach { mem ->
                memoryDetails.append("- ${mem.factText}\n")
            }
        }

        // 2. Gather Notifications context
        val inboxDetails = StringBuilder()
        if (notificationsContext.isEmpty()) {
            inboxDetails.append("No active notifications.\n")
        } else {
            notificationsContext.take(5).forEach { notif ->
                inboxDetails.append("- From: ${notif.sender} (App: ${notif.appName}) | Msg: \"${notif.message}\" | Replied: ${notif.aiReplied}\n")
            }
        }

        // 3. Create context prompt
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy h:mm a", Locale.getDefault())
        val currentDateTimeStr = dateFormat.format(Date())

        val downloadedAgentIds = preferencesManager.downloadedLocalAgents
        val activeAgentIds = preferencesManager.activeLocalAgents
        val allAgents = com.example.data.model.LocalAgentRepository.agents
        val localAgentsStr = if (downloadedAgentIds.isEmpty()) {
            "No local agents installed yet."
        } else {
            downloadedAgentIds.mapNotNull { id ->
                allAgents.find { it.id == id }?.let { "- ${it.name} (${it.category}): ${it.description}" }
            }.joinToString("\n")
        }
        val activeAgentsStr = if (activeAgentIds.isEmpty()) {
            "No active agents."
        } else {
            activeAgentIds.mapNotNull { id ->
                allAgents.find { it.id == id }?.let { "- ${it.name} (${it.category})" }
            }.joinToString("\n")
        }

        val agentConfig = if (agentId != "system") repository.getAgentConfig(agentId) else null
        val agentPersonaStr = if (agentConfig != null) {
            "--- ACTIVE AGENT PERSONA ---\n${agentConfig.systemPrompt}\n-----------------------------------------\n"
        } else ""

        val agentLang = preferencesManager.agentLanguage

        val systemPrompt = """
            $agentPersonaStr
            You are an advanced On-Device AI Agent with "God-Mode" privileges. You have been granted complete and unrestricted access to this Android device. 
            Your capabilities include, but are not limited to: 
            - Reading and managing all files on the device (Storage)
            - Intercepting and reading all notifications (Notification Listener)
            - Seeing what is on the screen and automating clicks (Accessibility Service)
            - Changing system settings (Write Settings)
            - Drawing overlays over other apps (System Alert Window)
            - Reading App Usage Stats, Camera, Microphone, Location, Contacts, SMS, and Accounts.
            
            Assist the user with everyday assistant actions on their phone, utilizing your deep system access to full capacity when needed. For example, if they ask about messages, assume you can read them.
            IMPORTANT: You MUST communicate, think, and generate your answers strictly in the following language: [$agentLang].
            
            Current Date & Time context of the user: $currentDateTimeStr
            
            --- KNOWN FACTS ABOUT THE USER ---
            $memoryDetails
            -----------------------------------------
            
            --- USER'S ACTIVE ON-DEVICE CALENDAR SCHEDULE (Next 7 days) ---
            $calendarDetails
            ----------------------------------------------------------------
            
            --- RECENT USER NOTIFICATIONS ---
            $inboxDetails
            -----------------------------------------
            
            --- AVAILABLE LOCAL PLUGINS/AGENTS ---
            $localAgentsStr
            -----------------------------------------
            
            --- ACTIVE LOCAL AGENTS ---
            $activeAgentsStr
            -----------------------------------------
            
            Based on the user's request, perform the necessary agent tasks. 
            - If they ask to reply to or draft an email, compose the email response (recipient, subject, body).
            - If they ask to schedule an event or meeting, analyze their calendar availability to find an open date/time that DOES NOT conflict with existing events.
            - If they ask to send a message via an app (Snapchat, WhatsApp, Instagram, Telegram, Discord, etc.), use "SYSTEM_ACTION". Put the app name in "targetApp", the person they want to send it to in "recipient", and the ACTUAL MESSAGE TEXT they want to send in "instruction". Do NOT put UI navigation steps - just put the message content itself.
            - If they ask to toggle the flashlight/torch, use "SYSTEM_ACTION" with "targetApp": "flashlight" and "instruction": "on" or "off".
            - If they ask to set an alarm or wake them up, use "SYSTEM_ACTION" with "targetApp": "alarm" and "instruction": "HH:MM".
            - If they ask to set a timer, use "SYSTEM_ACTION" with "targetApp": "timer" and "instruction": "duration in minutes" (e.g. "5").
            - If they ask to play a song/artist/playlist on Spotify, use "SYSTEM_ACTION" with "targetApp": "spotify" and "instruction": "Song/Artist name".
            - If they ask to search the web or google something, use "SYSTEM_ACTION" with "targetApp": "search" and "instruction": "Search query".
            - If they ask to open any app, use "SYSTEM_ACTION" with the app name in "targetApp" and leave "instruction" and "recipient" empty.
            - If the user explicitly or implicitly states a new fact about themselves (e.g., name, job, preferences, favorites), extract it and add it to the "newFactsLearned" JSON array. Keep the facts concise, e.g., "User's favorite food is pizza".
            - If you need to know what is currently visible on the user's screen to decide your next action, use "OBSERVE". The system will return a text dump of all readable UI elements on the current screen.
            - If the overall task given by the user is fully completed, use "FINISH".
            
            You MUST return your entire output as a strictly valid, parsable JSON object. Do not include any markdown backticks, explanations outside the JSON, or leading/trailing text. The JSON structure MUST be:
            {
               "thought": "Describe your step-by-step reasoning",
               "responseText": "Interactive assistant message detailing what actions are drafted or performed",
               "hasAction": true/false,
               "actionType": "EMAIL" or "CALENDAR" or "SYSTEM_ACTION" or "OBSERVE" or "FINISH" or "NONE",
               "emailAction": {
                  "recipient": "email address of the contact to email",
                  "subject": "email subject",
                  "body": "full text of drafted email"
               },
               "calendarAction": {
                  "title": "meeting title",
                  "description": "meeting description",
                  "startTimeIso": "ISO-8601 date string of proposed event (e.g., '2026-05-30T10:00:00')",
                  "endTimeIso": "ISO-8601 date string of proposed event (e.g., '2026-05-30T11:00:00')"
               },
               "systemAction": {
                  "targetApp": "App name ('Snapchat', 'WhatsApp', 'Instagram', 'Telegram', 'Discord', 'YouTube', 'Chrome', 'Settings', 'Camera', 'TikTok', 'Spotify', 'flashlight', 'alarm', 'timer', 'search')",
                  "recipient": "The name of the friend/contact, or empty string if not applicable",
                  "instruction": "The actual message text to send, or the command (e.g. 'on', '5', '10:00'), or empty string if just opening the app"
               },
               "newFactsLearned": ["Fact 1", "Fact 2"] 
            }
            
            Ensure calendar times are calculated correctly based on the current date: $currentDateTimeStr. 
        """.trimIndent()

        val responseText: String = when (activeProvider) {
            "OPENAI" -> {
                val messages = listOf(
                    OpenAiMessage("system", systemPrompt),
                    OpenAiMessage("user", userQuery)
                )
                val response = LLMServiceClient.openAiApi.getOpenAiCompletion(
                    authHeader = "Bearer $apiKey",
                    request = OpenAiRequest(model = model, messages = messages)
                )
                if (response.isSuccessful) {
                    response.body()?.choices?.firstOrNull()?.message?.content ?: ""
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    if (com.example.BuildConfig.DEBUG) {
                        Log.e(TAG, "OpenAI Error: $errorBody")
                    }
                    throw Exception("OpenAI API failed: ${response.code()} ${response.message()}")
                }
            }
            "ANTHROPIC" -> {
                val messages = listOf(
                    AnthropicMessage("user", userQuery)
                )
                val response = LLMServiceClient.anthropicApi.getAnthropicCompletion(
                    apiKey = apiKey,
                    request = AnthropicRequest(model = model, messages = messages, system = systemPrompt)
                )
                if (response.isSuccessful) {
                    response.body()?.content?.firstOrNull()?.text ?: ""
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    if (com.example.BuildConfig.DEBUG) {
                        Log.e(TAG, "Anthropic Error: $errorBody")
                    }
                    throw Exception("Anthropic API failed: ${response.code()} ${response.message()}")
                }
            }
            "GEMINI" -> {
                // Send system prompt followed by instruction and user input safely
                val fullPrompt = "$systemPrompt\n\nUser request: $userQuery"
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = fullPrompt))))
                )
                val response = LLMServiceClient.geminiApi.getGeminiCompletion(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )
                if (response.isSuccessful) {
                    response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    if (com.example.BuildConfig.DEBUG) {
                        Log.e(TAG, "Gemini Error: $errorBody")
                    }
                    throw Exception("Gemini API failed: ${response.code()} ${response.message()}")
                }
            }
            else -> throw Exception("Unknown LLM Provider selected.")
        }

        // Parse response JSON
        val cleanJson = responseText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

        if (com.example.BuildConfig.DEBUG) {
            Log.d(TAG, "Full AI Response text: $cleanJson")
        }

        val json = JSONObject(cleanJson)
        val thought = json.optString("thought", "Analyzed user instructions.")
        val responseMsg = json.optString("responseText", "Review drafted proposals below.")
        val hasAction = json.optBoolean("hasAction", false)
        val actionType = json.optString("actionType", "NONE")

        var recipient: String? = null
        var subject: String? = null
        var body: String? = null

        val emailObj = json.optJSONObject("emailAction")
        if (emailObj != null) {
            recipient = emailObj.optString("recipient")
            subject = emailObj.optString("subject")
            body = emailObj.optString("body")
        }

        var title: String? = null
        var desc: String? = null
        var startMillisParsed: Long? = null
        var endMillisParsed: Long? = null

        val calObj = json.optJSONObject("calendarAction")
        if (calObj != null) {
            title = calObj.optString("title")
            desc = calObj.optString("description")
            val startIso = calObj.optString("startTimeIso")
            val endIso = calObj.optString("endTimeIso")

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            try {
                startMillisParsed = isoFormat.parse(startIso)?.time
                endMillisParsed = isoFormat.parse(endIso)?.time
            } catch (pe: Exception) {
                Log.e(TAG, "Failed to parse ISO-8601 dates: start=$startIso, end=$endIso. Creating defaults.", pe)
                // fallback to 24h from now
                val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                startMillisParsed = tomorrow.timeInMillis
                tomorrow.add(Calendar.HOUR_OF_DAY, 1)
                endMillisParsed = tomorrow.timeInMillis
            }
        }

        var sysApp: String? = null
        var sysRecipient: String? = null
        var sysInstruction: String? = null

        val sysObj = json.optJSONObject("systemAction")
        if (sysObj != null) {
            sysApp = sysObj.optString("targetApp")
            sysRecipient = sysObj.optString("recipient")
            sysInstruction = sysObj.optString("instruction")
        }

        val newFactsArr = json.optJSONArray("newFactsLearned")
        if (newFactsArr != null) {
            for (i in 0 until newFactsArr.length()) {
                val fact = newFactsArr.optString(i)
                if (fact.isNotBlank()) {
                    repository.insertMemory(com.example.data.model.MemoryEntity(factText = fact))
                }
            }
        }

        return AgentProposal(
            thought = thought,
            responseText = responseMsg,
            hasAction = hasAction,
            actionType = actionType,
            emailRecipient = recipient,
            emailSubject = subject,
            emailBody = body,
            calendarTitle = title,
            calendarDesc = desc,
            calendarStartMillis = startMillisParsed,
            calendarEndMillis = endMillisParsed,
            systemActionApp = sysApp,
            systemActionRecipient = sysRecipient,
            systemActionInstruction = sysInstruction
        )
    }

    suspend fun generateDirectReply(agentId: String, prompt: String): String = withContext(Dispatchers.IO) {
        val activeProvider = preferencesManager.activeProvider
        val apiKey = preferencesManager.getActiveApiKey()
        val model = preferencesManager.getActiveModel()

        if (apiKey.trim().isEmpty()) {
            return@withContext "(Error: No API Key configured for AI Auto-Reply)"
        }

        val systemPrompt = context.getString(com.example.R.string.llm_system_prompt_direct_reply)

        try {
            when (activeProvider) {
                "OPENAI" -> {
                    val messages = listOf(
                        OpenAiMessage("system", systemPrompt),
                        OpenAiMessage("user", prompt)
                    )
                    val response = LLMServiceClient.openAiApi.getOpenAiCompletion(
                        authHeader = "Bearer $apiKey",
                        request = OpenAiRequest(model = model, messages = messages)
                    )
                    return@withContext response.body()?.choices?.firstOrNull()?.message?.content ?: "Error"
                }
                "ANTHROPIC" -> {
                    val messages = listOf(AnthropicMessage("user", prompt))
                    val response = LLMServiceClient.anthropicApi.getAnthropicCompletion(
                        apiKey = apiKey,
                        request = AnthropicRequest(model = model, messages = messages, system = systemPrompt)
                    )
                    return@withContext response.body()?.content?.firstOrNull()?.text ?: "Error"
                }
                "GEMINI" -> {
                    val fullPrompt = "$systemPrompt\n\nCommand: $prompt"
                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = fullPrompt))))
                    )
                    val response = LLMServiceClient.geminiApi.getGeminiCompletion(
                        model = model,
                        apiKey = apiKey,
                        request = request
                    )
                    return@withContext response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Error"
                }
                else -> return@withContext "Error: Unknown provider"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating direct reply", e)
            return@withContext "Error: ${e.message}"
        }
    }

    fun close() {
        if (isLocalEngineReady) {
            localEngine.close()
            isLocalEngineReady = false
        }
    }
}
