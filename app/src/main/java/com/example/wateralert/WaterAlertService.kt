package com.example.wateralert

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

/* 
   ERROR: "Unresolved reference" for Service, Intent, IBinder, etc.
   CAUSE: Missing import statements.
   RESOLUTION: Added the necessary Android framework imports.
*/

class WaterAlertService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // background code goes here
        return START_STICKY // tells android to restart if service killed
    }
    override fun onBind(intent: Intent): IBinder? = null
}

/* 
   ERROR: "Unresolved reference" for NotificationListenerService and StatusBarNotification.
   CAUSE: Missing imports for the notification service API.
   RESOLUTION: Added android.service.notification.* imports.
*/



    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName
        val text = sbn?.notification?.extras?.getCharSequence("android.text")?.toString() ?: ""
        
        // Example check for water keywords
        if (text.contains("water", ignoreCase = true)) {
            speakOut("Please drink water!")
        }
    }

    /* 
       ERROR: "Unresolved reference speak" and "messageText"
       CAUSE: Code was outside of a class/function and variables weren't defined.
       RESOLUTION: Created a helper function 'speakOut' inside the class.
    */
    private fun speakOut(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
