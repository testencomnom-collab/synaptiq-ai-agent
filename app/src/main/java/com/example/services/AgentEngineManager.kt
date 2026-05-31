package com.example.services

import android.app.Application
import com.example.data.PreferencesManager
import com.example.data.repository.AgentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AgentEngineManager {
    var agentService: LLMAgentService? = null
    val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getService(application: Application, prefs: PreferencesManager, repo: AgentRepository): LLMAgentService {
        if (agentService == null) {
            agentService = LLMAgentService(application, prefs, repo)
        }
        return agentService!!
    }

    fun shutdown() {
        agentService?.close()
        agentService = null
    }
}
