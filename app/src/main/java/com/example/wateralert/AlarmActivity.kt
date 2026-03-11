package com.example.wateralert

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class AlarmActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private val chantRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && tts != null) {
                tts?.speak("पानी 10 मिनट में आने वाला है! पानी 10 मिनट में आने वाला है!", TextToSpeech.QUEUE_FLUSH, null, "Chant")
                // Repeat every 8 seconds
                handler.postDelayed(this, 8000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Break through lock screen and turn on the screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)

        val btnStopAlarm: Button = findViewById(R.id.btnStopAlarm)
        btnStopAlarm.setOnClickListener {
            stopChanting()
            finish() // Close the activity
        }

        // Initialize TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi")
                isPlaying = true
                handler.post(chantRunnable)
            }
        }
    }

    private fun stopChanting() {
        isPlaying = false
        handler.removeCallbacks(chantRunnable)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onDestroy() {
        stopChanting()
        super.onDestroy()
    }
}
