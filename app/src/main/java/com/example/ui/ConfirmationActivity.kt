package com.example.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.app.AlertDialog
import com.example.services.AgentAccessibilityService

class ConfirmationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetIntent = intent.getParcelableExtra<Intent>("TARGET_INTENT")
        val message = intent.getStringExtra("CONFIRM_MESSAGE") ?: "Erlauben?"
        val appName = intent.getStringExtra("TARGET_APP")
        val recipient = intent.getStringExtra("TARGET_RECIPIENT")
        
        AlertDialog.Builder(this)
            .setTitle("Sicherheitsprüfung")
            .setMessage(message)
            .setPositiveButton("Erlauben") { _, _ ->
                if (targetIntent != null) {
                    if (!appName.isNullOrEmpty() && !recipient.isNullOrEmpty()) {
                        AgentAccessibilityService.AutomationState.isRunning = true
                        AgentAccessibilityService.AutomationState.targetApp = appName
                        AgentAccessibilityService.AutomationState.recipient = recipient
                        AgentAccessibilityService.AutomationState.step = 1
                    }
                    try {
                        startActivity(targetIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Aktion konnte nicht ausgeführt werden", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }
            .setNegativeButton("Ablehnen") { _, _ ->
                Toast.makeText(this, "Aktion blockiert", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
