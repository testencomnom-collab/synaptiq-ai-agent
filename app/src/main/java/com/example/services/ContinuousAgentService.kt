package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ContinuousAgentService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    companion object {
        const val EXTRA_TASK = "extra_task"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val CHANNEL_ID = "AgentBackgroundChannel"
        const val NOTIFICATION_ID = 1001
        
        val isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Agent startet..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            serviceJob.cancelChildren()
            isRunning.value = false
            updateNotification("Agent manuell gestoppt.")
            stopSelf()
            return START_NOT_STICKY
        }

        val task = intent?.getStringExtra(EXTRA_TASK) ?: return START_NOT_STICKY
        
        isRunning.value = true
        serviceScope.launch {
            try {
                runAutonomousLoop(task)
            } finally {
                isRunning.value = false
            }
        }
        return START_STICKY
    }

    private suspend fun CoroutineScope.runAutonomousLoop(initialTask: String) {
        val llmService = AgentEngineManager.agentService
        if (llmService == null) {
            stopSelf()
            return
        }

        var isTaskFinished = false
        var currentContext = "Initial User Task: $initialTask\n"
        var stepCount = 0
        val maxSteps = 15 // Sicherheitslimit gegen Endlosschleifen

        while (!isTaskFinished && stepCount < maxSteps && isActive) {
            stepCount++
            updateNotification("Agent arbeitet: Schritt $stepCount...")

            try {
                // 1. THINK (Denken)
                val proposal = llmService.executeAgentQuery(
                    agentId = "system", 
                    userQuery = currentContext + "\nWhat is the next step to achieve the user's task? Return your next action.", 
                    notificationsContext = emptyList() 
                )
                
                currentContext += "\nAgent Thought: ${proposal.thought}"
                currentContext += "\nAgent Action Executed: ${proposal.actionType}"

                // 2. ACT & OBSERVE (Handeln & Beobachten)
                when (proposal.actionType) {
                    "FINISH" -> {
                        isTaskFinished = true
                        updateNotification("Aufgabe abgeschlossen!")
                    }
                    "SYSTEM_ACTION" -> {
                        // UI-Automatisierung starten
                        AgentAccessibilityService.AutomationState.targetApp = proposal.systemActionApp ?: ""
                        AgentAccessibilityService.AutomationState.recipient = proposal.systemActionRecipient ?: ""
                        AgentAccessibilityService.AutomationState.isRunning = true
                        AgentAccessibilityService.AutomationState.step = 1
                        
                        // Warten, bis der AccessibilityService die UI-Aktion beendet hat
                        while (AgentAccessibilityService.AutomationState.isRunning && isActive) {
                            delay(500)
                        }
                        currentContext += "\nSystem Observation: UI Action on ${proposal.systemActionApp} completed."
                    }
                    "OBSERVE" -> {
                        val screenText = AgentAccessibilityService.instance?.captureScreenText() ?: "[Fehler: Accessibility Service nicht aktiv]"
                        currentContext += "\nSystem Observation (Current Screen Text): $screenText"
                        delay(1000)
                    }
                    else -> {
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e("ContinuousAgent", "Fehler in der autonomen Schleife", e)
                isTaskFinished = true
            }
        }
        
        updateNotification("Agent inaktiv.")
        stopSelf()
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SynaptiQ Autonomous Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agent Background Tasks",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
