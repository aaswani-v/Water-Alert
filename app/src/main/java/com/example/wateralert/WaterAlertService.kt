package com.example.wateralert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class WaterAlertService : Service() {

    private val CHANNEL_ID = "WaterAlertServiceChannel"

    /* This function runs when we start the service. It sets up the persistent notification. */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        // Start the service in the foreground so Android doesn't kill it
        startForeground(1, notification)
    }

    /* This function is called when the service is told to start. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY tells Android to restart the service if it gets killed by the system
        return START_STICKY
    }

    /* This function creates a notification channel, which is required for Android 8.0 and above. */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Water Alert Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /* This function builds the actual notification that the user sees in their tray. */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Water Alert Active")
            .setContentText("Listening for water notifications...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
