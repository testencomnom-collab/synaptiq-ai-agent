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
        var searchClicked = false
        var nameTyped = false
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
        
        // WhatsApp specific automation
        if (AutomationState.targetApp == "com.whatsapp" && event.packageName?.toString() == "com.whatsapp") {
            if (AutomationState.step == 1) {
                // Try finding contact directly first
                val contactNodes = rootNode.findAccessibilityNodeInfosByText(AutomationState.recipient)
                if (contactNodes.isNotEmpty() && AutomationState.nameTyped) {
                    for (node in contactNodes) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            break
                        } else if (node.parent?.isClickable == true) {
                            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            break
                        } else if (node.parent?.parent?.isClickable == true) {
                            node.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            break
                        }
                    }
                } else if (!AutomationState.nameTyped) {
                    // Start Search flow
                    val searchIcons = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/menuitem_search")
                    if (searchIcons.isNotEmpty() && !AutomationState.searchClicked) {
                        searchIcons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.searchClicked = true
                    } else if (AutomationState.searchClicked) {
                        val searchInputs = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_src_text")
                        if (searchInputs.isNotEmpty()) {
                            val args = android.os.Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, AutomationState.recipient)
                            }
                            searchInputs.first().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                            AutomationState.nameTyped = true
                        }
                    } else {
                        // Sometimes the picker shows the contact directly without typing
                        if (contactNodes.isNotEmpty()) {
                            for (node in contactNodes) {
                                if (node.isClickable) {
                                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    AutomationState.step = 2
                                    break
                                } else if (node.parent?.isClickable == true) {
                                    node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    AutomationState.step = 2
                                    break
                                } else if (node.parent?.parent?.isClickable == true) {
                                    node.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    AutomationState.step = 2
                                    break
                                }
                            }
                        }
                    }
                }
            }

            if (AutomationState.step == 2) {
                // In WhatsApp's share sheet, clicking a contact reveals a green "Next/Send" fab at the bottom right.
                val sendNodesId = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                val nextNodesId = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/next")
                val allFabs = sendNodesId + nextNodesId
                
                if (allFabs.isNotEmpty()) {
                    val btn = allFabs.first()
                    if (btn.isClickable) {
                        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.step = 3 // Move to chat view
                        return
                    } else if (btn.parent?.isClickable == true) {
                        btn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.step = 3
                        return
                    }
                }
            }
            
            if (AutomationState.step == 3) {
                // In the actual chat window, find the final send button
                val finalSendNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                val descNodes = rootNode.findAccessibilityNodeInfosByText("Senden") + rootNode.findAccessibilityNodeInfosByText("Send")
                val allFinal = finalSendNodes + descNodes
                
                if (allFinal.isNotEmpty()) {
                    val btn = allFinal.first()
                    if (btn.isClickable) {
                        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.isRunning = false
                        AutomationState.step = 0
                    } else if (btn.parent?.isClickable == true) {
                        btn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AutomationState.isRunning = false
                        AutomationState.step = 0
                    }
                }
            }
        }
        
        // Generic automation for Instagram, Telegram, Discord
        val genericApps = setOf("com.instagram.android", "org.telegram.messenger", "com.discord")
        if (genericApps.contains(AutomationState.targetApp) && event.packageName?.toString() == AutomationState.targetApp) {
            if (AutomationState.step == 1) {
                val contactNodes = rootNode.findAccessibilityNodeInfosByText(AutomationState.recipient)
                if (contactNodes.isNotEmpty()) {
                    for (node in contactNodes) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            break
                        } else if (node.parent?.isClickable == true) {
                            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            break
                        }
                    }
                }
            }
            if (AutomationState.step == 2) {
                val sendNodes = rootNode.findAccessibilityNodeInfosByText("Senden") + 
                                rootNode.findAccessibilityNodeInfosByText("Send") +
                                rootNode.findAccessibilityNodeInfosByText("Fertig") +
                                rootNode.findAccessibilityNodeInfosByText("Done")
                
                if (sendNodes.isNotEmpty()) {
                    for (node in sendNodes) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.isRunning = false
                            AutomationState.searchClicked = false
                            AutomationState.nameTyped = false
                            AutomationState.step = 0
                            return
                        } else if (node.parent?.isClickable == true) {
                            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.isRunning = false
                            AutomationState.searchClicked = false
                            AutomationState.nameTyped = false
                            AutomationState.step = 0
                            return
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("AgentAccessibility", "Accessibility Service Interrupted")
        AutomationState.isRunning = false
        AutomationState.searchClicked = false
        AutomationState.nameTyped = false
    }
}
