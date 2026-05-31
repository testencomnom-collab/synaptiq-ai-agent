package com.example.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AgentNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("AgentNotification", "Notification Listener Connected")
    }

    private val blockList = setOf(
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.authy.authy",
        "com.starfinanz.smob.android.sbanking",
        "de.dkb.portalapp",
        "com.ing.diba.mbank",
        "com.n26.android"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName ?: return
            
            if (blockList.contains(packageName) || packageName.contains("bank", ignoreCase = true)) {
                Log.d("AgentNotification", "Blocked sensitive notification from $packageName")
                return
            }

            val extras = it.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            
            Log.d("AgentNotification", "New Notification from $packageName: $title - $text")
            // Here the agent could process incoming messages from WhatsApp, Telegram, etc.
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Log.d("AgentNotification", "Notification Removed: ${sbn?.packageName}")
    }
}
