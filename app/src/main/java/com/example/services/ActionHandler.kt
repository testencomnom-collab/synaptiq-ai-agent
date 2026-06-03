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
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.content.Context
import android.app.SearchManager
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object ActionHandler {

    private fun showToastOnMainThread(context: Context, message: String, length: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, length).show()
        }
    }

    private fun executeDirectly(context: Context, targetIntent: Intent, message: String, targetApp: String? = null, targetRecipient: String? = null) {
        try {
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(targetIntent)
        } catch (e: Exception) {
            Log.e("ActionHandler", "Failed to execute autonomous action", e)
            showToastOnMainThread(context, "Execution failed: ${e.message}", Toast.LENGTH_SHORT)
        }
    }

    private suspend fun getPhoneNumber(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numIndex != -1) {
                    val rawNum = cursor.getString(numIndex)
                    var cleanNum = rawNum?.replace(Regex("[^0-9+]"), "")
                    if (cleanNum?.startsWith("00") == true) {
                        cleanNum = "+" + cleanNum.substring(2)
                    }
                    return@withContext cleanNum
                }
            }
        }
        return@withContext null
    }

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
                showToastOnMainThread(
                    context,
                    "Scheduled event: \"$calTitle\" at ${sdf.format(Date(start))}",
                    Toast.LENGTH_LONG
                )
                return true
            } else {
                showToastOnMainThread(
                    context,
                    "Failed to write to calendar. Check runtime permissions inside System Settings.",
                    Toast.LENGTH_LONG
                )
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
            showToastOnMainThread(context, "Opening email composer draft...", Toast.LENGTH_SHORT)
            executeDirectly(context, emailIntent, "E-Mail an $rec vorbereiten?")
            return true
        }
        return false
    }

    suspend fun handleSystemAction(context: Application, json: JSONObject): Boolean {
        val sysApp = json.optString("systemActionApp", "")
        var rawRecipient = json.optString("recipient", "")
        // Robustly clean up the recipient name in case the LLM incorrectly includes the app name or prepositions
        val cleanupRegex = Regex("(?i)\\b(auf|in|via|bei|on|at|to|für|for|snapchat|whatsapp|insta|instagram|telegram|discord|twitter|facebook)\\b")
        val sysRecipient = rawRecipient.replace(cleanupRegex, "").replace(Regex("\\s+"), " ").trim()
        val sysInstruction = json.optString("instruction", "")
        val finalInstruction = if (sysInstruction.isEmpty()) json.optString("systemActionInstruction", "") else sysInstruction

        // Hardware & System Controls
        when (sysApp.lowercase().trim()) {
            "flashlight", "taschenlampe" -> {
                return try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList[0]
                    val turnOn = finalInstruction.contains("on", ignoreCase = true) || finalInstruction.contains("an", ignoreCase = true)
                    cameraManager.setTorchMode(cameraId, turnOn)
                    showToastOnMainThread(context, if (turnOn) "Flashlight ON" else "Flashlight OFF", Toast.LENGTH_SHORT)
                    true
                } catch (e: Exception) {
                    showToastOnMainThread(context, "Could not control flashlight: ${e.message}", Toast.LENGTH_SHORT)
                    false
                }
            }
            "alarm", "wecker" -> {
                val timeStr = finalInstruction.filter { it.isDigit() || it == ':' }
                if (timeStr.isNotEmpty()) {
                    val parts = timeStr.split(":")
                    val hour = parts[0].toIntOrNull() ?: 0
                    val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, sysRecipient.ifEmpty { "AI Alarm" })
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    executeDirectly(context, intent, "Wecker für $hour:$minute stellen?")
                    return true
                }
            }
            "timer" -> {
                val minutes = finalInstruction.filter { it.isDigit() }.toIntOrNull() ?: 5
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, sysRecipient.ifEmpty { "AI Timer" })
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                executeDirectly(context, intent, "Timer für $minutes Minuten stellen?")
                return true
            }
            "spotify" -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    action = "android.media.action.MEDIA_PLAY_FROM_SEARCH"
                    setPackage("com.spotify.music")
                    putExtra(SearchManager.QUERY, finalInstruction)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    executeDirectly(context, intent, "Spotify Suche nach: $finalInstruction durchführen?")
                    true
                } catch (e: Exception) {
                    // Fallback to opening app
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        executeDirectly(context, launchIntent, "Spotify öffnen?")
                        true
                    } else false
                }
            }
            "search", "google" -> {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, finalInstruction)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                executeDirectly(context, intent, "Im Web nach '$finalInstruction' suchen?")
                return true
            }
        }

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
                "notes" to "com.google.android.keep",
                "sms" to "sms_internal",
                "nachrichten" to "sms_internal",
                "messages" to "sms_internal"
            )

            val targetPackage = packageMap[sysApp.lowercase().trim()]
            
            if (targetPackage == "sms_internal" && finalInstruction.isNotEmpty()) {
                val phone = if (sysRecipient.isNotEmpty()) getPhoneNumber(context, sysRecipient) else null
                if (phone != null && ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val smsManager = context.getSystemService(SmsManager::class.java)
                        smsManager.sendTextMessage(phone, null, finalInstruction, null, null)
                        showToastOnMainThread(context, "SMS sent to $sysRecipient", Toast.LENGTH_LONG)
                        return true
                    } catch (e: Exception) {
                        showToastOnMainThread(context, "SMS Error: ${e.message}", Toast.LENGTH_SHORT)
                    }
                } else if (phone != null) {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                        putExtra("sms_body", finalInstruction)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    executeDirectly(context, intent, "Send SMS to $sysRecipient?")
                    return true
                }
                return false
            }

            if (targetPackage != null) {
                try {
                    val messagingApps = setOf(
                        "com.snapchat.android", "com.whatsapp", "com.instagram.android",
                        "org.telegram.messenger", "com.discord", "org.thoughtcrime.securesms",
                        "com.facebook.orca", "com.twitter.android"
                    )

                    if (targetPackage in messagingApps && finalInstruction.isNotEmpty()) {
                        val phone = if (sysRecipient.isNotEmpty()) getPhoneNumber(context, sysRecipient) else null
                        val shareIntent = if (targetPackage == "com.whatsapp" && phone != null) {
                            Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(finalInstruction)}")
                                setPackage(targetPackage)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        } else {
                            Intent(Intent.ACTION_SEND).apply {
                                setType("text/plain")
                                setPackage(targetPackage)
                                putExtra(Intent.EXTRA_TEXT, finalInstruction)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        try {
                            AgentAccessibilityService.AutomationState.start(
                                app = targetPackage,
                                contact = sysRecipient,
                                startStep = if (targetPackage == "com.whatsapp" && phone != null) 3 else 1
                            )

                            val msg = "Nachricht via $sysApp senden?"
                            executeDirectly(context, shareIntent, msg, targetPackage, sysRecipient)
                            return true
                        } catch (e: Exception) {
                            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                executeDirectly(context, launchIntent, "$sysApp öffnen?")
                                return true
                            }
                        }
                    } else {
                        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            executeDirectly(context, launchIntent, "$sysApp öffnen?")
                            return true
                        } else {
                            showToastOnMainThread(context, "$sysApp is not installed on this device.", Toast.LENGTH_LONG)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ActionHandler", "Failed to launch $sysApp", e)
                    showToastOnMainThread(context, "Could not open $sysApp: ${e.message}", Toast.LENGTH_LONG)
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
                        executeDirectly(context, launchIntent, "$sysApp öffnen?")
                        return true
                    }
                } else {
                    showToastOnMainThread(context, "App '$sysApp' not found on device.", Toast.LENGTH_LONG)
                }
            }
        }
        return false
    }
}
