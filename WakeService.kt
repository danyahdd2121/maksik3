package com.example.openassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Постоянно слушает микрофон и ждёт «Привет, Максик» / «Hey Максик».
 * Это не настоящий wake-word движок, а цикл распознавания речи, поэтому
 * он расходует батарею и на некоторых телефонах может «щёлкать».
 */
class WakeService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val wake = listOf("максик", "максык", "максек", "maksik", "maxik")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { stopSelf(); return START_NOT_STICKY }
        running = true
        listen()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("wake", "Максик слушает", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "wake")
                else Notification.Builder(this)
        val n = b.setContentTitle("Максик слушает")
            .setContentText("Скажи: «Привет, Максик»")
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setContentIntent(pi).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 30)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun restart(ms: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, ms)
    }

    private fun listen() {
        if (!running) return
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                when (e) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopSelf()
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(200)
                    else -> restart(2000)
                }
            }
            override fun onResults(res: Bundle?) { check(res) }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
        recognizer = r
    }

    private fun check(res: Bundle?) {
        val list = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList<String>()
        for (t in list) {
            val low = t.lowercase()
            val w = wake.firstOrNull { low.contains(it) } ?: continue
            trigger(low.substringAfter(w).trim().trim(',', '.', ' '))
            return
        }
        restart(200)
    }

    private fun trigger(cmd: String) {
        recognizer?.destroy(); recognizer = null
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("command", cmd)
            )
        } catch (_: Exception) { }
        // Пока Максик слушает команду сам, сервис не трогает микрофон
        restart(if (cmd.isBlank()) 25000 else 8000)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        super.onDestroy()
    }
}
