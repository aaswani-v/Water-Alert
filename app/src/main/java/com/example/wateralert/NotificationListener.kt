package com.example.wateralert

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent

class NotificationListener : NotificationListenerService() {

    private val groqService = GroqService()
    private val scope = CoroutineScope(Dispatchers.IO)

    /* This function runs every time a new notification appears on the phone. */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return

        // We only want to listen to WhatsApp notifications
        if (packageName != "com.whatsapp") return

        // Get the actual message text and sender from the notification
        val text = sbn.notification?.extras?.getCharSequence("android.text")?.toString() ?: return
        val title = sbn.notification?.extras?.getCharSequence("android.title")?.toString() ?: "Unknown"

        // Discard "Checking for new messages..." or empty ones
        if (text.isBlank() || text.contains("new messages", true) || text.contains("WhatsApp Web", true)) {
            return
        }

        // Get exact time
        val dateFormat = SimpleDateFormat("h:mm a, EEEE, dd MMM yyyy", Locale.ENGLISH)
        val timestamp = dateFormat.format(Date(sbn.postTime))
        
        val formattedMessage = "[$timestamp] $title: $text"

        // Keywords indicating water
        val waterKeywords = listOf("water", "पाणी", "पानी", "टाइमिंग", "वेळ", "timing", "पाणि")
        val isWaterRelated = waterKeywords.any { text.contains(it, ignoreCase = true) }

        if (isWaterRelated) {
            Log.d("WaterAlert", "Stored WATER message: $formattedMessage")
            saveMessageToList("water_messages", formattedMessage)
            
            // Automatically launch background alarm analysis!
            val singleMessageJsonArray = JSONArray().apply { put(formattedMessage) }.toString()
            scope.launch {
                val result = groqService.summarizeWaterMessage(singleMessageJsonArray)
                if (result != null && !result.startsWith("ERROR:")) {
                    try {
                        val jsonResponse = JSONObject(result)
                        val hour24 = jsonResponse.optInt("water_time_hour_24h", -1)
                        val minute = jsonResponse.optInt("water_time_minute", -1)

                        if (hour24 != -1 && hour24 != 0) {
                            // Automatically Set the Alarm
                            AlarmHelper.setWaterAlarm(applicationContext, hour24, minute)
                            
                            // Send broadcast to update UI if MainActivity is open
                            val updateIntent = Intent("com.example.wateralert.ALARM_UPDATED")
                            sendBroadcast(updateIntent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            Log.d("WaterAlert", "Stored GENERAL message: $formattedMessage")
            saveMessageToList("general_messages", formattedMessage)
        }
    }

    private fun saveMessageToList(key: String, newMessage: String) {
        val prefs = getSharedPreferences("WaterAlertPrefs", Context.MODE_PRIVATE)
        val existingMessagesStr = prefs.getString(key, "[]")
        
        try {
            val messagesArray = JSONArray(existingMessagesStr)
            messagesArray.put(newMessage)
            prefs.edit().putString(key, messagesArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            // If json is corrupted, start fresh
            val newArray = JSONArray()
            newArray.put(newMessage)
            prefs.edit().putString(key, newArray.toString()).apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
