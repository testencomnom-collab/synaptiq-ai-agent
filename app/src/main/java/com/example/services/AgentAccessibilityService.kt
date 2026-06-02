package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AgentAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    object AutomationState {
        var isRunning = false
        var targetApp = ""
        var recipient = ""
        var step = 0
        var searchClicked = false
        var nameTyped = false

        fun start(app: String, contact: String, startStep: Int) {
            targetApp = app
            recipient = contact
            step = startStep
            searchClicked = false
            nameTyped = false
            isRunning = true
            Log.d("AgentAccessibility", "AutomationState started: app=$app, recipient=$contact, step=$startStep")
        }

        fun stop() {
            isRunning = false
            targetApp = ""
            recipient = ""
            step = 0
            searchClicked = false
            nameTyped = false
            Log.d("AgentAccessibility", "AutomationState stopped and reset")
        }
    }

    private fun findFirstEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString()?.contains("EditText") == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditText(child)
            if (found != null) {
                return found
            }
            child.recycle()
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AutomationState.isRunning) return

        val rootNode = rootInActiveWindow ?: return

        // Snapchat specific automation
        if (AutomationState.targetApp == "com.snapchat.android" && event.packageName?.toString() == "com.snapchat.android") {
            if (AutomationState.step == 1) {
                // Find friend by name
                val friendNodes = rootNode.findAccessibilityNodeInfosByText(AutomationState.recipient)
                var foundAndClicked = false
                if (friendNodes.isNotEmpty()) {
                    for (friendNode in friendNodes) {
                        if (friendNode.className?.toString()?.contains("EditText") == true) continue
                        if (friendNode.isClickable) {
                            friendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            foundAndClicked = true
                            break
                        } else if (friendNode.parent?.isClickable == true) {
                            friendNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            foundAndClicked = true
                            break
                        } else if (friendNode.parent?.parent?.isClickable == true) {
                            friendNode.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            foundAndClicked = true
                            break
                        } else if (friendNode.parent?.parent?.parent?.isClickable == true) {
                            friendNode.parent?.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            foundAndClicked = true
                            break
                        }
                    }
                }

                if (!foundAndClicked && !AutomationState.nameTyped) {
                    // Start Snapchat search flow if friend is not found
                    var searchInputNode: AccessibilityNodeInfo? = null
                    val searchInputs = rootNode.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/search_text_input")
                    if (searchInputs.isNotEmpty()) {
                        searchInputNode = searchInputs.first()
                    } else {
                        val searchInputs2 = rootNode.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/search_box")
                        if (searchInputs2.isNotEmpty()) {
                            searchInputNode = searchInputs2.first()
                        } else {
                            searchInputNode = findFirstEditText(rootNode)
                        }
                    }

                    if (searchInputNode != null) {
                        if (!AutomationState.searchClicked) {
                            if (searchInputNode.isClickable) {
                                searchInputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            } else if (searchInputNode.parent?.isClickable == true) {
                                searchInputNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            }
                            AutomationState.searchClicked = true
                        } else {
                            val args = android.os.Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, AutomationState.recipient)
                            }
                            searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                            AutomationState.nameTyped = true
                        }
                    }
                }
            }

            if (AutomationState.step == 2) {
                // After clicking friend, find the send button.
                val sendNodesDesc = rootNode.findAccessibilityNodeInfosByText("Senden")
                if (sendNodesDesc.isNotEmpty()) {
                    for (sendBtn in sendNodesDesc) {
                        if (sendBtn.isClickable) {
                            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (sendBtn.parent?.isClickable == true) {
                            sendBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (sendBtn.parent?.parent?.isClickable == true) {
                            sendBtn.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        }
                    }
                }

                val sendNodesDescEN = rootNode.findAccessibilityNodeInfosByText("Send")
                if (sendNodesDescEN.isNotEmpty()) {
                    for (sendBtn in sendNodesDescEN) {
                        if (sendBtn.isClickable) {
                            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (sendBtn.parent?.isClickable == true) {
                            sendBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (sendBtn.parent?.parent?.isClickable == true) {
                            sendBtn.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        }
                    }
                }

                // Fallback: look for view id containing "send_to_bottom_panel_send_button"
                val sendNodesId = rootNode.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/send_to_bottom_panel_send_button")
                if (sendNodesId.isNotEmpty()) {
                    for (sendBtn in sendNodesId) {
                        if (sendBtn.isClickable) {
                            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (sendBtn.parent?.isClickable == true) {
                            sendBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (sendBtn.parent?.parent?.isClickable == true) {
                            sendBtn.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        }
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
                        if (node.className?.toString()?.contains("EditText") == true) continue
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
                        } else if (node.parent?.parent?.parent?.isClickable == true) {
                            node.parent?.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 2
                            break
                        }
                    }
                } else if (!AutomationState.nameTyped) {
                    // Start Search flow
                    var searchIconNode: AccessibilityNodeInfo? = null
                    val searchIcons = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/menuitem_search")
                    if (searchIcons.isNotEmpty()) {
                        searchIconNode = searchIcons.first()
                    } else {
                        val searchButtons = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_button")
                        if (searchButtons.isNotEmpty()) {
                            searchIconNode = searchButtons.first()
                        } else {
                            val searchDescEN = rootNode.findAccessibilityNodeInfosByText("Search")
                            val searchDescDE = rootNode.findAccessibilityNodeInfosByText("Suchen")
                            val searchDescAll = searchDescEN + searchDescDE
                            for (node in searchDescAll) {
                                if (node.isClickable || node.parent?.isClickable == true) {
                                    searchIconNode = node
                                    break
                                }
                            }
                        }
                    }

                    if (searchIconNode != null && !AutomationState.searchClicked) {
                        if (searchIconNode.isClickable) {
                            searchIconNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.searchClicked = true
                        } else if (searchIconNode.parent?.isClickable == true) {
                            searchIconNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.searchClicked = true
                        }
                    } else if (AutomationState.searchClicked) {
                        var searchInputNode: AccessibilityNodeInfo? = null
                        val searchInputs = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_src_text")
                        if (searchInputs.isNotEmpty()) {
                            searchInputNode = searchInputs.first()
                        } else {
                            val searchInputs2 = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_input")
                            if (searchInputs2.isNotEmpty()) {
                                searchInputNode = searchInputs2.first()
                            } else {
                                searchInputNode = findFirstEditText(rootNode)
                            }
                        }

                        if (searchInputNode != null) {
                            val args = android.os.Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, AutomationState.recipient)
                            }
                            searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                            AutomationState.nameTyped = true
                        }
                    } else {
                        // Sometimes the picker shows the contact directly without typing
                        if (contactNodes.isNotEmpty()) {
                            for (node in contactNodes) {
                                if (node.className?.toString()?.contains("EditText") == true) continue
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
                                } else if (node.parent?.parent?.parent?.isClickable == true) {
                                    node.parent?.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
                val nextDescEN = rootNode.findAccessibilityNodeInfosByText("Next")
                val nextDescDE = rootNode.findAccessibilityNodeInfosByText("Weiter")
                val sendDescEN = rootNode.findAccessibilityNodeInfosByText("Send")
                val sendDescDE = rootNode.findAccessibilityNodeInfosByText("Senden")
                val allFabs = sendNodesId + nextNodesId + nextDescEN + nextDescDE + sendDescEN + sendDescDE

                if (allFabs.isNotEmpty()) {
                    for (btn in allFabs) {
                        if (btn.isClickable) {
                            btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 3 // Move to chat view
                            return
                        } else if (btn.parent?.isClickable == true) {
                            btn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 3
                            return
                        } else if (btn.parent?.parent?.isClickable == true) {
                            btn.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.step = 3
                            return
                        }
                    }
                }
            }

            if (AutomationState.step == 3) {
                // In the actual chat window, find the final send button
                val finalSendNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                val finalSendNodes2 = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send_button")
                val descNodes = rootNode.findAccessibilityNodeInfosByText("Senden") + rootNode.findAccessibilityNodeInfosByText("Send")
                val allFinal = finalSendNodes + finalSendNodes2 + descNodes

                if (allFinal.isNotEmpty()) {
                    for (btn in allFinal) {
                        if (btn.isClickable) {
                            btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (btn.parent?.isClickable == true) {
                            btn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (btn.parent?.parent?.isClickable == true) {
                            btn.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (btn.parent?.parent?.parent?.isClickable == true) {
                            btn.parent?.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        }
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
                        } else if (node.parent?.parent?.isClickable == true) {
                            node.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
                            AutomationState.stop()
                            return
                        } else if (node.parent?.isClickable == true) {
                            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        } else if (node.parent?.parent?.isClickable == true) {
                            node.parent?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AutomationState.stop()
                            return
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("AgentAccessibility", "Accessibility Service Interrupted")
        AutomationState.stop()
    }

    fun captureScreenText(): String {
        val rootNode = rootInActiveWindow ?: return "[Konnte Bildschirm nicht lesen - kein Root Window]"
        val textList = mutableListOf<String>()
        extractTextRecursively(rootNode, textList)
        rootNode.recycle()
        return textList.joinToString(" | ")
    }

    private fun extractTextRecursively(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        
        if (!text.isNullOrEmpty() && text !in textList) {
            textList.add(text)
        } else if (!contentDesc.isNullOrEmpty() && contentDesc !in textList) {
            textList.add(contentDesc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextRecursively(child, textList)
                child.recycle()
            }
        }
    }

    fun performScroll(direction: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = if (direction == "SCROLL_DOWN") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val success = scrollFirstScrollableNode(rootNode, action)
        rootNode.recycle()
        return success
    }

    private fun scrollFirstScrollableNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val success = scrollFirstScrollableNode(child, action)
                child.recycle()
                if (success) return true
            }
        }
        return false
    }
}
