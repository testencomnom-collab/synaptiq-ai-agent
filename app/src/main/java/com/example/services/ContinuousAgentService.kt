package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.*

class ContinuousAgentService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        const val EXTRA_TASK = "extra_task"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val CHANNEL_ID = "AgentBackgroundChannel"
        const val NOTIFICATION_ID = 1001
        
        val isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val currentThought = kotlinx.coroutines.flow.MutableStateFlow("")
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(getString(R.string.agent_starts)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            serviceJob.cancelChildren()
            releaseWakeLock()
            isRunning.value = false
            updateNotification(getString(R.string.agent_stopped))
            stopSelf()
            return START_NOT_STICKY
        }

        val task = intent?.getStringExtra(EXTRA_TASK) ?: return START_NOT_STICKY
        
        isRunning.value = true
        serviceScope.launch {
            try {
                runAutonomousLoop(task)
            } finally {
                releaseWakeLock()
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

        // WakeLock aktivieren (hält die CPU wach)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SynaptiQ::AgentWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L) 

        var isTaskFinished = false
        val actionHistory = mutableListOf<String>() 
        var stepCount = 0
        val maxSteps = 15 

        while (!isTaskFinished && stepCount < maxSteps && isActive) {
            stepCount++
            updateNotification(getString(R.string.agent_working, stepCount))

            try {
                val recentHistory = actionHistory.takeLast(5).joinToString("\n")
                val promptContext = "User Task: $initialTask\nRecent History:\n$recentHistory"

                val proposal = llmService.executeAgentQuery(
                    agentId = "system", 
                    userQuery = promptContext + "\nWhat is the next step to achieve the user's task? Return your next action.", 
                    notificationsContext = emptyList() 
                )
                
                // Zeige den Gedanken des Agenten in der Benachrichtigung an
                updateNotification(getString(R.string.agent_thinking, stepCount, proposal.thought))
                currentThought.value = proposal.thought ?: ""
                delay(1000) // Kurze Pause, damit der User den Gedanken lesen kann
                
                var currentStepRecord = "Step $stepCount - Thought: ${proposal.thought} | Action: ${proposal.actionType}"

                when (proposal.actionType) {
                    "FINISH" -> {
                        isTaskFinished = true
                        updateNotification(getString(R.string.task_completed))
                    }
                    "SYSTEM_ACTION" -> {
                        AgentAccessibilityService.AutomationState.targetApp = proposal.systemActionApp ?: ""
                        AgentAccessibilityService.AutomationState.recipient = proposal.systemActionRecipient ?: ""
                        AgentAccessibilityService.AutomationState.isRunning = true
                        AgentAccessibilityService.AutomationState.step = 1
                        
                        var waitTime = 0
                        // Warte maximal 15 Sekunden (30 * 500ms) auf den AccessibilityService
                        while (AgentAccessibilityService.AutomationState.isRunning && isActive && waitTime < 30) {
                            delay(500)
                            waitTime++
                        }
                        if (AgentAccessibilityService.AutomationState.isRunning) {
                            // Timeout erreicht, Brechstange ziehen, damit der Agent nicht hängt
                            AgentAccessibilityService.AutomationState.isRunning = false 
                            currentStepRecord += " -> Result: Timeout! UI Action took too long."
                        } else {
                            currentStepRecord += " -> Result: UI Action on ${proposal.systemActionApp} completed."
                        }
                    }
                    "OBSERVE" -> {
                        val screenText = AgentAccessibilityService.instance?.captureScreenText() ?: "[Error: Accessibility Service inactive]"
                        currentStepRecord += "\n[CURRENT SCREEN DUMP]: $screenText" 
                        delay(1000)
                    }
                    "SCROLL_DOWN", "SCROLL_UP" -> {
                        val success = AgentAccessibilityService.instance?.performScroll(proposal.actionType) == true
                        if (success) {
                            currentStepRecord += " -> Result: Scrolled ${proposal.actionType} successfully."
                            delay(1500) // Warten, bis Scroll-Animation fertig ist
                            val screenText = AgentAccessibilityService.instance?.captureScreenText() ?: "[Error]"
                            currentStepRecord += "\n[CURRENT SCREEN DUMP AFTER SCROLL]: $screenText" 
                        } else {
                            currentStepRecord += " -> Result: Scroll failed (no scrollable view found)."
                        }
                    }
                    else -> {
                        delay(1000)
                    }
                }
                
                actionHistory.add(currentStepRecord)
                
            } catch (e: Exception) {
                Log.e("ContinuousAgent", "Error in the autonomous loop", e)
                // Do not hard-abort on error, but wait briefly and then try again
                delay(5000)
                actionHistory.add("Step $stepCount - ERROR: ${e.message}")
            }
        }
        
        updateNotification(getString(R.string.agent_inactive))
        stopSelf()
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("ContinuousAgent", "Error releasing WakeLock", e)
        }
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
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
