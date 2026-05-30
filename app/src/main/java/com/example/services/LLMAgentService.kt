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

        // PFAD B: Echter Lokaler Modus (On-Device MediaPipe)
        if (agentId != "system" && agentId.isNotEmpty()) {
            Log.d(TAG, "Routing to Local On-Device Mode for Agent: $agentId")
            if (!isLocalEngineReady) {
                isLocalEngineReady = localEngine.initialize() 
            }
            if (!isLocalEngineReady) {
                return@withContext AgentProposal(
                    thought = "Lokale Modellgewichte fehlen oder Engine Error.",
                    responseText = "Fehler: Das MediaPipe Modell für $agentId (" +
                                 "gemma_2b_it_cpu_int4.bin) ist nicht aktiv. Bitte lade " +
                                 "das echte Modell in der Bibliothek herunter.",
                    hasAction = false,
                    actionType = "NONE"
                )
            }
            
            val agentConfig = repository.getAgentConfig(agentId)
            val agentLang = preferencesManager.agentLanguage
            val sysPrompt = (agentConfig?.systemPrompt ?: "Du bist ein lokaler KI Assistent.") + " WICHTIG: Antworte in dieser Sprache: $agentLang"
            // Clean specific templating ideal for Gemma instruction tuned models
            val fullPrompt = "<start_of_turn>user\n${sysPrompt}\n\nUser: $userQuery\n<end_of_turn>\n<start_of_turn>model\n"
            
            val response = localEngine.generateResponse(fullPrompt)
            return@withContext AgentProposal(
                thought = "PFAD B: 100% Offline-Ausführung (Gemma 2B). Keine Cloud-APIs oder Netzwerke genutzt.",
                responseText = response.trim(),
                hasAction = false,
                actionType = "NONE"
            )
        }

        // PFAD A: Cloud-Modus (Open AI / Anthropic / Gemini Rest APIs)
        if (apiKey.trim().isEmpty()) {
            return@withContext AgentProposal(
                thought = "Cloud-API-Routing (PFAD A) fehlgeschlagen: Kein Key konfiguriert.",
                responseText = "Hinweis: Für den Cloud-Chat ist kein API-Key hinterlegt. Bitte wechsle zu den Einstellungen (Settings) und trage einen gültigen API-Schlüssel ein, um die Live-KI API zu nutzen. Alternativ kannst du offline mit lokalen Agenten chatten.",
                hasAction = false,
                actionType = "NONE"
            )
        }

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
            You must reply in the following language: $agentLang.
            
            Current Date & Time context of the user: $currentDateTimeStr
            
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
            
            You MUST return your entire output as a strictly valid, parsable JSON object. Do not include any markdown backticks, explanations outside the JSON, or leading/trailing text. The JSON structure MUST be:
            {
               "thought": "Describe your step-by-step reasoning",
               "responseText": "Interactive assistant message detailing what actions are drafted or performed",
               "hasAction": true/false,
               "actionType": "EMAIL" or "CALENDAR" or "SYSTEM_ACTION" or "NONE",
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
               }
            }
            
            Ensure calendar times are calculated correctly based on the current date: $currentDateTimeStr. 
        """.trimIndent()

        try {
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

            return@withContext AgentProposal(
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

        } catch (e: Exception) {
            Log.e(TAG, "Error executing agent action query", e)
            val cleanMsg = e.message ?: "An unknown connection error occurred."
            return@withContext AgentProposal(
                thought = "Error: $cleanMsg",
                responseText = "I encountered an API error. Please verify your internet connection, active subscription state, or API Key inside Settings:\n\n**Error Details:**\n$cleanMsg",
                hasAction = false,
                actionType = "NONE"
            )
        }
    }

    suspend fun generateDirectReply(agentId: String, prompt: String): String = withContext(Dispatchers.IO) {
        val activeProvider = preferencesManager.activeProvider
        val apiKey = preferencesManager.getActiveApiKey()
        val model = preferencesManager.getActiveModel()

        if (apiKey.trim().isEmpty()) {
            return@withContext "(Error: No API Key configured for AI Auto-Reply)"
        }

        val agentLang = preferencesManager.agentLanguage

        val systemPrompt = "Du bist ein hilfreicher Assistent. Generiere eine sehr kurze und präzise Antwort (max 1-2 Sätze) als direkte Reaktion per Text. Gib NUR die Antwortnachricht zurück, keine Erklärungen und kein JSON. WICHTIG: Antworte in dieser Sprache: $agentLang"

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
