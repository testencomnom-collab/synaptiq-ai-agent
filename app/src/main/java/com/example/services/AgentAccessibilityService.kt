package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        this.serviceInfo = info
        Log.d("AgentAccessibility", "Accessibility Service Connected")
    }

    object AutomationState {
        var isRunning = false
        var targetApp = ""
        var recipient = ""
        var step = 0
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AutomationState.isRunning) return

        val rootNode = rootInActiveWindow ?: return

        // Snapchat specific automation
        if (AutomationState.targetApp == "com.snapchat.android" && event.packageName?.toString() == "com.snapchat.android") {
            if (AutomationState.step == 1) {
                // Find friend by name
                val friendNodes = rootNode.findAccessibilityNodeInfosByText(AutomationState.recipient)
                if (friendNodes.isNotEmpty()) {
                    val friendNode = friendNodes.first()
                    // Click it
                    if (friendNode.isClickable) {
                        friendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.step = 2
                    } else if (friendNode.parent?.isClickable == true) {
                        friendNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.step = 2
                    }
                }
            }

            if (AutomationState.step == 2) {
                // After clicking friend, find the send button. 
                // In Snapchat share sheet, the send button is usually a button at the bottom right.
                // It might have content description "Senden" or "Send"
                val sendNodesDesc = rootNode.findAccessibilityNodeInfosByText("Senden")
                if (sendNodesDesc.isNotEmpty()) {
                    val sendBtn = sendNodesDesc.first()
                    if (sendBtn.isClickable) {
                        sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.isRunning = false // Automation complete
                        AutomationState.step = 0
                        return
                    } else if (sendBtn.parent?.isClickable == true) {
                        sendBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.isRunning = false
                        AutomationState.step = 0
                        return
                    }
                }
                
                val sendNodesDescEN = rootNode.findAccessibilityNodeInfosByText("Send")
                if (sendNodesDescEN.isNotEmpty()) {
                    val sendBtn = sendNodesDescEN.first()
                    if (sendBtn.isClickable) {
                        sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.isRunning = false // Automation complete
                        AutomationState.step = 0
                        return
                    } else if (sendBtn.parent?.isClickable == true) {
                        sendBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.isRunning = false
                        AutomationState.step = 0
                        return
                    }
                }
                
                // Fallback: look for view id containing "send_to_bottom_panel_send_button"
                val sendNodesId = rootNode.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/send_to_bottom_panel_send_button")
                if (sendNodesId.isNotEmpty()) {
                    val sendBtn = sendNodesId.first()
                    if (sendBtn.isClickable) {
                        sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.isRunning = false
                        AutomationState.step = 0
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("AgentAccessibility", "Accessibility Service Interrupted")
        AutomationState.isRunning = false
    }
}
