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
import com.example.data.model.EmailItem
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
    val actionType: String, // "EMAIL", "CALENDAR", "BOTH", "NONE"
    val emailRecipient: String? = null,
    val emailSubject: String? = null,
    val emailBody: String? = null,
    val calendarTitle: String? = null,
    val calendarDesc: String? = null,
    val calendarStartMillis: Long? = null,
    val calendarEndMillis: Long? = null
)

class LLMAgentService(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val repository: AgentRepository
) {
    companion object {
        private const val TAG = "LLMAgentService"
    }

    suspend fun executeAgentQuery(userQuery: String, emailsContext: List<EmailItem>): AgentProposal = withContext(Dispatchers.IO) {
        val activeProvider = preferencesManager.activeProvider
        val apiKey = preferencesManager.getActiveApiKey()
        val model = preferencesManager.getActiveModel()

        if (apiKey.trim().isEmpty()) {
            return@withContext AgentProposal(
                thought = "No API key configured.",
                responseText = "Hello! To function as your agent, I need an API Key. Please head over to the **Settings tab** at the bottom and enter an API Key for your preferred provider (OpenAI, Anthropic, or Gemini).",
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

        // 2. Gather Inbox context
        val inboxDetails = StringBuilder()
        if (emailsContext.isEmpty()) {
            inboxDetails.append("No active emails in local simulated Inbox.\n")
        } else {
            emailsContext.take(5).forEach { email ->
                inboxDetails.append("- From: ${email.sender} | Subj: ${email.subject} | EmailBody: \"${email.body}\"\n")
            }
        }

        // 3. Create context prompt
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy h:mm a", Locale.getDefault())
        val currentDateTimeStr = dateFormat.format(Date())

        val downloadedAgentIds = preferencesManager.downloadedLocalAgents
        val localAgentsStr = if (downloadedAgentIds.isEmpty()) {
            "No local agents installed yet."
        } else {
            val allAgents = com.example.data.model.LocalAgentRepository.agents
            downloadedAgentIds.mapNotNull { id ->
                allAgents.find { it.id == id }?.let { "- ${it.name} (${it.category}): ${it.description}" }
            }.joinToString("\n")
        }

        val systemPrompt = """
            You are an advanced On-Device AI Agent. You assist the user with everyday assistant actions on their phone, including reading/answering emails and booking calendar events based on their direct calendar availability.
            
            Current Date & Time context of the user: $currentDateTimeStr
            
            --- USER'S ACTIVE ON-DEVICE CALENDAR SCHEDULE (Next 7 days) ---
            $calendarDetails
            ----------------------------------------------------------------
            
            --- RECENT SIMULATED USER INBOX EMAILS ---
            $inboxDetails
            -----------------------------------------
            
            --- AVAILABLE LOCAL PLUGINS/AGENTS ---
            $localAgentsStr
            -----------------------------------------
            
            Based on the user's request, perform the necessary agent tasks. 
            - If they ask to reply to or draft an email, compose the email response (recipient, subject, body).
            - If they ask to schedule an event or meeting, analyze their calendar availability (under the schedule provided above) to find an open date/time that DOES NOT conflict with existing events. Make sure you select a valid date/time that complies with the request (e.g. 'tomorrow morning', 'next Tuesday at 2pm'). Convert that proposed time strictly to its absolute timestamp in milliseconds or ISO-8601 representation.
            
            You MUST return your entire output as a strictly valid, parsable JSON object. Do not include any markdown backticks, explanations outside the JSON, or leading/trailing text. The JSON structure MUST be:
            {
               "thought": "Describe your step-by-step reasoning about calendar availability and email creation",
               "responseText": "Interactive assistant message detailing what actions are drafted",
               "hasAction": true/false,
               "actionType": "EMAIL" or "CALENDAR" or "BOTH" or "NONE",
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
                        Log.e(TAG, "OpenAI Error: $errorBody")
                        throw Exception("OpenAI API failed: ${response.code()} ${response.message()}\n$errorBody")
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
                        Log.e(TAG, "Anthropic Error: $errorBody")
                        throw Exception("Anthropic API failed: ${response.code()} ${response.message()}\n$errorBody")
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
                        Log.e(TAG, "Gemini Error: $errorBody")
                        throw Exception("Gemini API failed: ${response.code()} ${response.message()}\n$errorBody")
                    }
                }
                else -> throw Exception("Unknown LLM Provider selected.")
            }

            // Parse response JSON
            val cleanJson = responseText.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            Log.d(TAG, "Full AI Response text: $cleanJson")

            val json = JSONObject(cleanJson)
            val thought = json.optString("thought", "Analyzed user instructions.")
            val responseMsg = json.optString("responseText", "Review drafted proposals below.")
            val hasAction = json.optBoolean("hasAction", false)
            val actionType = json.optString("actionType", "NONE")

            var recipient: String? = null
            var subject: String? = null
            var body: String? = null

            if (json.has("emailAction")) {
                val emailObj = json.getJSONObject("emailAction")
                recipient = emailObj.optString("recipient")
                subject = emailObj.optString("subject")
                body = emailObj.optString("body")
            }

            var title: String? = null
            var desc: String? = null
            var startMillisParsed: Long? = null
            var endMillisParsed: Long? = null

            if (json.has("calendarAction")) {
                val calObj = json.getJSONObject("calendarAction")
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
                calendarEndMillis = endMillisParsed
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
}
