package com.example.wateralert

import android.service.notification.NotificationListenerService
import android.speech.tts.TextToSpeech
import java.util.Locale

class NotificationListener : NotificationListenerService() {

    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        // INITIALIZING TTS HERE
        // ERROR: "Unresolved reference context"
        // CAUSE: Trying to initialize TextToSpeech in the top-level of the file where 'this' (context) doesn't exist.
        // RESOLUTION: Moved TTS initialization inside the Service's onCreate() where 'this' refers to the service context.
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("hi")
            }
        }
    }