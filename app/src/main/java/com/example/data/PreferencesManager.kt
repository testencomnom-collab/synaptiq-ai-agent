package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "agent_prefs_secured",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        
        private const val KEY_ACTIVE_PROVIDER = "active_provider" // "OPENAI", "ANTHROPIC", "GEMINI"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_DOWNLOADED_AGENTS = "downloaded_local_agents"
    }

    var openAiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    var anthropicApiKey: String
        get() = prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ANTHROPIC_API_KEY, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var activeProvider: String
        get() = prefs.getString(KEY_ACTIVE_PROVIDER, "OPENAI") ?: "OPENAI"
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROVIDER, value).apply()

    var selectedModel: String
        get() = prefs.getString(KEY_SELECTED_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value).apply()

    var downloadedLocalAgents: Set<String>
        get() = prefs.getStringSet(KEY_DOWNLOADED_AGENTS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DOWNLOADED_AGENTS, value).apply()

    fun getActiveApiKey(): String {
        return when (activeProvider) {
            "OPENAI" -> openAiApiKey
            "ANTHROPIC" -> anthropicApiKey
            "GEMINI" -> geminiApiKey
            else -> ""
        }
    }

    fun getActiveModel(): String {
        val model = selectedModel
        if (model.isNotEmpty()) return model
        return when (activeProvider) {
            "OPENAI" -> "gpt-4o-mini"
            "ANTHROPIC" -> "claude-3-5-sonnet-20241022"
            "GEMINI" -> "gemini-1.5-flash"
            else -> ""
        }
    }
}
