package com.example.wateralert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.widget.Switch
import android.widget.TextView
import com.example.wateralert.AlarmHelper

class MainActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private val groqService = GroqService()
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var progressBar: ProgressBar
    private lateinit var tvAlarmStatus: TextView
    private lateinit var switchAlarm: Switch

    // Receiver to auto-update the UI when NotificationListener blindly schedules an alarm
    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncAlarmUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart: Button = findViewById(R.id.btnStart)
        val btnStop: Button = findViewById(R.id.btnStop)
        val btnPlaySummary: Button = findViewById(R.id.btnPlaySummary)
        val btnPlayGossip: Button = findViewById(R.id.btnPlayGossip)
        progressBar = findViewById(R.id.progressBar)
        tvAlarmStatus = findViewById(R.id.tvAlarmStatus)
        switchAlarm = findViewById(R.id.switchAlarm)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi")
            }
        }

        switchAlarm.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("WaterAlertPrefs", Context.MODE_PRIVATE)
            val currentActive = prefs.getBoolean("is_alarm_active", false)
            
            // Only fire logic if the change was physical by user, not programmatic
            if (isChecked != currentActive) {
                if (isChecked) {
                    val h = prefs.getInt("alarm_hour", -1)
                    val m = prefs.getInt("alarm_minute", -1)
                    if (h != -1) {
                        AlarmHelper.setWaterAlarm(this, h, m)
                        Toast.makeText(this, "Auto-Alarm Enabled", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    AlarmHelper.cancelWaterAlarm(this)
                    Toast.makeText(this, "Auto-Alarm Cancelled", Toast.LENGTH_SHORT).show()
                }
                syncAlarmUI()
            }
        }

        btnPlaySummary.setOnClickListener {
            handlePlayClick("water_messages", isWater = true)
        }

        btnPlayGossip.setOnClickListener {
            handlePlayClick("general_messages", isWater = false)
        }

        btnStart.setOnClickListener {
            if (checkPermissions()) {
                startWaterService()
                Toast.makeText(this, "Listening for WhatsApp messages...", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            stopWaterService()
            Toast.makeText(this, "Stopped listening.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        syncAlarmUI()
        // Register receiver bridging the background service and UI
        val filter = IntentFilter("com.example.wateralert.ALARM_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(uiUpdateReceiver)
    }

    private fun syncAlarmUI() {
        val prefs = getSharedPreferences("WaterAlertPrefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_alarm_active", false)
        val hour = prefs.getInt("alarm_hour", -1)
        val min = prefs.getInt("alarm_minute", -1)

        switchAlarm.isChecked = isActive
        
        if (hour != -1 && isActive) {
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, min)
            tvAlarmStatus.text = "Scheduled at $timeStr"
        } else if (hour != -1 && !isActive) {
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, min)
            tvAlarmStatus.text = "Paused ($timeStr)"
        } else {
            tvAlarmStatus.text = "No Alarm Scheduled"
        }
    }

    private fun handlePlayClick(storageKey: String, isWater: Boolean) {
        val prefs = getSharedPreferences("WaterAlertPrefs", Context.MODE_PRIVATE)
        val messagesJson = prefs.getString(storageKey, "[]") ?: "[]"
        val messagesArray = JSONArray(messagesJson)

        if (messagesArray.length() == 0) {
            val msg = if (isWater) "अभी कोई नया पानी का मैसेज नहीं आया है।" else "अभी ग्रुप में कोई खास बात नहीं हुई है।"
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }

        // Show loading state
        progressBar.visibility = View.VISIBLE

        scope.launch {
            try {
                // Call Groq on background thread
                val result = withContext(Dispatchers.IO) {
                    if (isWater) groqService.summarizeWaterMessage(messagesJson)
                    else groqService.summarizeGossip(messagesJson)
                }

                progressBar.visibility = View.GONE

                if (result == null) {
                    tts?.speak("इंटरनेट काम नहीं कर रहा है।", TextToSpeech.QUEUE_FLUSH, null, null)
                    return@launch
                }

                if (result.startsWith("ERROR:")) {
                    tts?.speak("तकनीकी खराबी: API ऎरर।", TextToSpeech.QUEUE_FLUSH, null, null)
                    Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (isWater) {
                    // Parse the Strict JSON block from Groq
                    val jsonResponse = JSONObject(result)
                    val spokenSummary = jsonResponse.optString("spoken_summary", "पानी का समय समझ नहीं आया।")
                    val hour24 = jsonResponse.optInt("water_time_hour_24h", -1)
                    val minute = jsonResponse.optInt("water_time_minute", -1)

                    // Speak the summary
                    tts?.speak(spokenSummary, TextToSpeech.QUEUE_FLUSH, null, null)

                    // Set alarm if valid time parsed
                    if (hour24 != -1 && hour24 != 0) {
                        AlarmHelper.setWaterAlarm(this@MainActivity, hour24, minute)
                    } else {
                        Toast.makeText(this@MainActivity, "No valid time found by AI. Hour extracted: $hour24", Toast.LENGTH_LONG).show()
                    }

                } else {
                    // Gossip is returned as plain text
                    tts?.speak(result, TextToSpeech.QUEUE_FLUSH, null, null)
                }

                // Clear the messages after successful reading
                prefs.edit().putString(storageKey, "[]").apply()

            } catch (e: Exception) {
                e.printStackTrace()
                progressBar.visibility = View.GONE
                tts?.speak("कुछ खराबी आ गई है।", TextToSpeech.QUEUE_FLUSH, null, null)
                Toast.makeText(this@MainActivity, "App Crash: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                return false
            }
        }
        return true
    }

    /* This function checks if the user has allowed the app to read notifications in system settings. */
    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    /* This function starts our background service. */
    private fun startWaterService() {
        val intent = Intent(this, WaterAlertService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /* This function stops our background service. */
    private fun stopWaterService() {
        val intent = Intent(this, WaterAlertService::class.java)
        stopService(intent)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
