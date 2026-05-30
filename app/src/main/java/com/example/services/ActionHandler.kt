package com.example.services

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActionHandler {

    suspend fun handleCalendarAction(context: Application, json: JSONObject): Boolean {
        val calTitle = json.optString("calendarTitle")
        val calDesc = json.optString("calendarDesc")
        val start = json.optLong("calendarStart", 0L)
        val end = json.optLong("calendarEnd", 0L)

        if (calTitle.isNotEmpty() && start > 0L && end > start) {
            val uri = withContext(Dispatchers.IO) {
                CalendarManager.insertEvent(
                    context = context,
                    title = calTitle,
                    description = calDesc,
                    startMillis = start,
                    endMillis = end
                )
            }
            if (uri != null) {
                val sdf = SimpleDateFormat("h:mm a (MMM d)", Locale.getDefault())
                Toast.makeText(
                    context,
                    "Scheduled event: \"$calTitle\" at ${sdf.format(Date(start))}",
                    Toast.LENGTH_LONG
                ).show()
                return true
            } else {
                Toast.makeText(
                    context,
                    "Failed to write to calendar. Check runtime permissions inside System Settings.",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
        }
        return false
    }

    fun handleEmailAction(context: Application, json: JSONObject): Boolean {
        val rec = json.optString("emailRecipient")
        val subj = json.optString("emailSubject")
        val body = json.optString("emailBody")

        if (rec.isNotEmpty()) {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(rec))
                putExtra(Intent.EXTRA_SUBJECT, subj)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Toast.makeText(context, "Opening email composer draft...", Toast.LENGTH_SHORT).show()
            context.startActivity(emailIntent)
            return true
        }
        return false
    }

    fun handleSystemAction(context: Application, json: JSONObject): Boolean {
        val sysApp = json.optString("systemActionApp", "")
        val sysRecipient = json.optString("recipient", "")
        val sysInstruction = json.optString("instruction", "")
        val finalInstruction = if (sysInstruction.isEmpty()) json.optString("systemActionInstruction", "") else sysInstruction

        if (sysApp.isNotEmpty()) {
            val pm = context.packageManager
            val packageMap = mapOf(
                "snapchat" to "com.snapchat.android",
                "whatsapp" to "com.whatsapp",
                "instagram" to "com.instagram.android",
                "telegram" to "org.telegram.messenger",
                "tiktok" to "com.zhiliaoapp.musically",
                "twitter" to "com.twitter.android",
                "x" to "com.twitter.android",
                "discord" to "com.discord",
                "signal" to "org.thoughtcrime.securesms",
                "facebook" to "com.facebook.katana",
                "messenger" to "com.facebook.orca",
                "youtube" to "com.google.android.youtube",
                "spotify" to "com.spotify.music",
                "chrome" to "com.android.chrome",
                "gmail" to "com.google.android.gm",
                "maps" to "com.google.android.apps.maps",
                "camera" to "com.android.camera",
                "settings" to "com.android.settings",
                "clock" to "com.android.deskclock",
                "calculator" to "com.android.calculator2",
                "phone" to "com.android.dialer",
                "contacts" to "com.android.contacts",
                "calendar" to "com.google.android.calendar",
                "files" to "com.google.android.apps.nbu.files",
                "notes" to "com.google.android.keep"
            )

            val targetPackage = packageMap[sysApp.lowercase().trim()]

            if (targetPackage != null) {
                try {
                    val messagingApps = setOf(
                        "com.snapchat.android", "com.whatsapp", "com.instagram.android",
                        "org.telegram.messenger", "com.discord", "org.thoughtcrime.securesms",
                        "com.facebook.orca", "com.twitter.android"
                    )

                    if (targetPackage in messagingApps && finalInstruction.isNotEmpty()) {
                        if (sysRecipient.isNotEmpty()) {
                            AgentAccessibilityService.AutomationState.isRunning = true
                            AgentAccessibilityService.AutomationState.targetApp = targetPackage
                            AgentAccessibilityService.AutomationState.recipient = sysRecipient
                            AgentAccessibilityService.AutomationState.step = 1
                        } else {
                            AgentAccessibilityService.AutomationState.isRunning = false
                        }

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            setType("text/plain")
                            setPackage(targetPackage)
                            putExtra(Intent.EXTRA_TEXT, finalInstruction)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(shareIntent)
                            Toast.makeText(context, "Opening $sysApp with message...", Toast.LENGTH_LONG).show()
                            return true
                        } catch (e: Exception) {
                            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                                Toast.makeText(context, "Opened $sysApp (send message manually)", Toast.LENGTH_LONG).show()
                                return true
                            }
                        }
                    } else {
                        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            Toast.makeText(context, "Opened $sysApp", Toast.LENGTH_SHORT).show()
                            return true
                        } else {
                            Toast.makeText(context, "$sysApp is not installed on this device.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ActionHandler", "Failed to launch $sysApp", e)
                    Toast.makeText(context, "Could not open $sysApp: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                val installed = pm.getInstalledApplications(0)
                val match = installed.find {
                    pm.getApplicationLabel(it).toString().equals(sysApp, ignoreCase = true)
                }
                if (match != null) {
                    val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        Toast.makeText(context, "Opened $sysApp", Toast.LENGTH_SHORT).show()
                        return true
                    }
                } else {
                    Toast.makeText(context, "App '$sysApp' not found on device.", Toast.LENGTH_LONG).show()
                }
            }
        }
        return false
    }
}
