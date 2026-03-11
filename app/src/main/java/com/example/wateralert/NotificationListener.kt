package com.example.wateralert

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class NotificationListener : NotificationListenerService() {

    private var tts: TextToSpeech? = null

    /* This function runs when the service starts. It sets up the Text-to-Speech engine. */
    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set language to Hindi for better pronunciation of Hindi/Marathi words
                tts?.language = Locale("hi")
            }
        }
    }

    /* This function runs every time a new notification appears on the phone. */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return

        // We only want to listen to WhatsApp notifications
        if (packageName != "com.whatsapp") return

        // Get the actual message text from the notification
        val text = sbn.notification?.extras?.getCharSequence("android.text")?.toString() ?: return

        // List of keywords to look for in English, Hindi, and Marathi
        val keywords = listOf("water", "पाणी", "पानी", "टाइमिंग", "वेळ", "timing", "पाणि")

        // If the message contains any of our keywords, read it out loud
        if (keywords.any { text.contains(it, ignoreCase = true) }) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    /* This function runs when the service is stopped to clean up memory. */
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
