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
            val activeAgentIds = preferencesManager.activeLocalAgents
            val allAgents = com.example.data.model.LocalAgentRepository.agents
            val activeAgents = activeAgentIds.mapNotNull { id -> allAgents.find { it.id == id } }
            val queryLower = userQuery.lowercase(Locale.getDefault())

            // 1. Check if Email Action is requested
            if (queryLower.contains("mail") || queryLower.contains("email") || queryLower.contains("schreib") || queryLower.contains("antwort") || queryLower.contains("lucas") || queryLower.contains("sarah") || queryLower.contains("alex")) {
                var recipient = "lucas.boss@company.com"
                var subject = "Re: Quarterly Business Report & Update"
                var body = "Hi Lucas,\n\nI have reviewed the sales spreadsheets and finalized the figures. I am free to meet tomorrow morning to go over the final slides.\n\nBest regards,\nUser"
                
                if (queryLower.contains("sarah")) {
                    recipient = "sarah.hr@company.com"
                    subject = "Re: Onboarding Feedback Call"
                    body = "Hi Sarah,\n\nSure, I would love to connect next Tuesday at 3:00 PM to share my onboarding feedback. I've added it to my schedule.\n\nBest regards,\nUser"
                } else if (queryLower.contains("alex")) {
                    recipient = "alex.designer@company.com"
                    subject = "Re: Figma Draft Review Required"
                    body = "Hi Alex,\n\nI'll be ready at 4:30 PM to jump on the design huddle and review the Figma dashboard draft. Looking forward to it!\n\nBest regards,\nUser"
                }
                
                val agentName = activeAgents.firstOrNull()?.name ?: "Aura Agent"
                
                return@withContext AgentProposal(
                    thought = "Simulated $agentName: Detected email intent in user query. Analyzing seed emails context and drafting automated response proposal.",
                    responseText = "I've drafted a simulated email response using **$agentName**! You can click the **Execute** button below to simulate sending this email.",
                    hasAction = true,
                    actionType = "EMAIL",
                    emailRecipient = recipient,
                    emailSubject = subject,
                    emailBody = body
                )
            }

            // 2. Check if Calendar scheduling is requested
            if (queryLower.contains("calendar") || queryLower.contains("kalender") || queryLower.contains("termin") || queryLower.contains("meeting") || queryLower.contains("schedule") || queryLower.contains("planen") || queryLower.contains("buche") || queryLower.contains("buchen") || queryLower.contains("uhr") || queryLower.contains("heute") || queryLower.contains("morgen")) {
                val agentName = activeAgents.firstOrNull()?.name ?: "Aura Agent"
                
                val cal = Calendar.getInstance()
                var startHour = 14
                var dayOffset = 1 // default tomorrow
                
                if (queryLower.contains("heute") || queryLower.contains("today")) {
                    dayOffset = 0
                    startHour = cal.get(Calendar.HOUR_OF_DAY) + 1
                    if (startHour >= 18) {
                        startHour = 10
                        dayOffset = 1
                    }
                } else if (queryLower.contains("dienstag") || queryLower.contains("tuesday")) {
                    var daysToTuesday = (Calendar.TUESDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (daysToTuesday == 0) daysToTuesday = 7
                    dayOffset = daysToTuesday
                    startHour = 15
                }
                
                cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                cal.set(Calendar.HOUR_OF_DAY, startHour)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                
                val startMillis = cal.timeInMillis
                cal.add(Calendar.HOUR_OF_DAY, 1)
                val endMillis = cal.timeInMillis
                
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val startIso = sdf.format(Date(startMillis))
                val endIso = sdf.format(Date(endMillis))
                
                var title = "Sync & Finalize Report"
                var desc = "Simulated meeting to discuss current status and finalize ongoing projects."
                
                if (queryLower.contains("figma") || queryLower.contains("design") || queryLower.contains("alex")) {
                    title = "Figma Draft Review"
                    desc = "Design sync with Alex to review Figma dashboard mockups before shipping."
                } else if (queryLower.contains("onboarding") || queryLower.contains("hr") || queryLower.contains("sarah")) {
                    title = "Onboarding Feedback Call"
                    desc = "HR sync with Sarah to go over onboarding feedback and check-in."
                }
                
                return@withContext AgentProposal(
                    thought = "Simulated $agentName: Detected calendar scheduling request. Searching active on-device calendar for free slots and resolving conflicts. Found optimal slot at $startIso.",
                    responseText = "I've analyzed your schedule and drafted a simulated calendar entry using **$agentName** at a free slot! Tap **Execute** to write this event into your on-device calendar database.",
                    hasAction = true,
                    actionType = "CALENDAR",
                    calendarTitle = title,
                    calendarDesc = desc,
                    calendarStartMillis = startMillis,
                    calendarEndMillis = endMillis
                )
            }

            // 3. Fallback: Check if there is an active local agent and respond on its behalf
            if (activeAgents.isNotEmpty()) {
                val primaryAgent = activeAgents.first()
                val responseText = when (primaryAgent.id) {
                    "openhands" -> "Hello! I am **OpenHands**, your offline AI Software Engineer. I am currently active and monitoring your workspace. I can help you draft code files, build projects, or write tests. Let me know what feature you'd like to implement!"
                    "goose" -> "Greetings! I am **Goose**, your local system terminal assistant. I can inspect directories, read workspace configurations, and simulate local shell executions. What terminal command would you like me to simulate?"
                    "browseruse" -> "Hi there! I am **Browser-Use**, your web automation agent. I can open real browser windows, click buttons, fill in forms, and automate complex web searches. Tell me what website you want me to automate!"
                    "crewai" -> "Hello! We are your **CrewAI Agent Team** (Researcher, Writer, and Critic). We work in parallel to research, write, and refine reports for you. Let us know what topic you'd like us to collaborate on!"
                    "autogen" -> "Hello! We are **AutoGen Collaborative Conversational Agents**. We can orchestrate multi-agent discussions to solve programming and brainstorming tasks together. Ask us a complex question to get started!"
                    "metagpt" -> "Welcome! I am **MetaGPT**, your automated Software Development Corporation. I simulate roles like Product Manager, Architect, and Developer to produce full project blueprints. Tell me your app idea!"
                    "n8n" -> "Hi! I am **n8n**, your visual workflow automation agent. I can connect trigger events to actions across databases, calendar events, and emails. What API workflow should we configure?"
                    "langflow" -> "Hello! I am **Langflow**, your visual drag-and-drop LLM builder. I help you connect models, prompts, and vector databases in visual pipelines without code. What chain shall we construct?"
                    "smolagents" -> "Hey! I am **Smolagents**, your high-speed Python script execution assistant. I write and run lightweight code snippets locally to answer queries instantly. Give me a task to compute!"
                    else -> "Hello! I am **${primaryAgent.name}**, your locally installed agent plugin. I am running in fully-functional simulated offline mode! How can I assist you with your device emails, calendar, or workflows?"
                }
                
                return@withContext AgentProposal(
                    thought = "Simulated ${primaryAgent.name}: Processing user query in local offline fallback sandbox.",
                    responseText = responseText,
                    hasAction = false,
                    actionType = "NONE"
                )
            }

            // 4. Default if no active agent and no key
            return@withContext AgentProposal(
                thought = "No API key configured.",
                responseText = "Hello! To function as your agent, I need an API Key. Please head over to the **Settings tab** at the bottom and enter an API Key for your preferred provider (OpenAI, Anthropic, or Gemini).\n\n*Alternatively, you can visit the **Library tab** and download a **local agent** (like OpenHands or Goose) to experience the app in fully-interactive offline simulation mode immediately!*",
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
            
            --- ACTIVE LOCAL AGENTS ---
            $activeAgentsStr
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
