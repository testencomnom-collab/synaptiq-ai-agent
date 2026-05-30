package com.example.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- OpenAI API Models ---
data class OpenAiMessage(val role: String, val content: String)

data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Double = 0.2
)

data class OpenAiChoice(val message: OpenAiMessage)
data class OpenAiResponse(val choices: List<OpenAiChoice>)

// --- Anthropic API Models ---
data class AnthropicMessage(val role: String, val content: String)

data class AnthropicRequest(
    val model: String,
    val max_tokens: Int = 2000,
    val messages: List<AnthropicMessage>,
    val system: String? = null
)

data class AnthropicContent(val text: String, val type: String = "text")
data class AnthropicResponse(val content: List<AnthropicContent>)

// --- Gemini API Models ---
data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>, val role: String = "user")
data class GeminiRequest(val contents: List<GeminiContent>)

data class GeminiCandidateContent(val parts: List<GeminiPart>)
data class GeminiCandidate(val content: GeminiCandidateContent)
data class GeminiResponse(val candidates: List<GeminiCandidate>)

// --- Retrofit Servicing ---
interface LLMApi {
    // OpenAI client
    @POST("v1/chat/completions")
    suspend fun getOpenAiCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiRequest
    ): Response<OpenAiResponse>

    // Anthropic client
    @POST("v1/messages")
    suspend fun getAnthropicCompletion(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: AnthropicRequest
    ): Response<AnthropicResponse>

    // Gemini Client
    @POST("v1beta/models/{model}:generateContent")
    suspend fun getGeminiCompletion(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
