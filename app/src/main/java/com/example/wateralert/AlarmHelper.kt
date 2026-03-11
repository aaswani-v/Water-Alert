package com.example.wateralert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import java.util.Calendar

object AlarmHelper {

    fun setWaterAlarm(context: Context, targetHour24: Int, targetMinute: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, targetHour24)
        calendar.set(Calendar.MINUTE, targetMinute)
        calendar.set(Calendar.SECOND, 0)
        
        // Subtract 10 minutes
        calendar.add(Calendar.MINUTE, -10)
        
        // If the calculated time has already passed today, set it for tomorrow
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val alarmHour = calendar.get(Calendar.HOUR_OF_DAY)
        val alarmMinute = calendar.get(Calendar.MINUTE)
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Android 12+ requires explicit permission to set exact alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Cannot show toast easily from background context, just return. Main UI handles asking permission.
            return
        }

        val intent = Intent(context, WaterAlarmReceiver::class.java).apply {
            putExtra("ALARM_MESSAGE", "पानी आ रहा है! (Water)")
        }

        // FLAG_UPDATE_CURRENT combined with a static ID (1001) guarantees old alarms are overwritten 
        val pendingIntentId = 1001
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, pendingIntentId, intent, flags
        )

        try {
            // Background exact alarm that wakes device
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            // Save state for UI Toggle
            val prefs = context.getSharedPreferences("WaterAlertPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_alarm_active", true)
                .putInt("alarm_hour", targetHour24)
                .putInt("alarm_minute", targetMinute)
                .apply()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelWaterAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WaterAlarmReceiver::class.java)
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags)
        
        alarmManager.cancel(pendingIntent)
        
        val prefs = context.getSharedPreferences("WaterAlertPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_alarm_active", false).apply()
    }
}
