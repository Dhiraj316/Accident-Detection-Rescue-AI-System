package com.accident.detector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.cancel
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MonitoringService : Service() {

    companion object {
        const val CHANNEL_ID    = "accident_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val TAG           = "MonitoringService"

        // Default sensor values — all zero when not connected
        var currentAx  = 0.0f
        var currentAy  = 0.0f
        var currentAz  = 0.0f
        var currentGx  = 0.0f
        var currentGy  = 0.0f
        var currentGz  = 0.0f
        var isConnected = false   // false until ESP32 connects
        var lastScore   = 0.0f   // last ML prediction score
    }

    private var accidentModel: AccidentModel? = null
    private var sensorServer: SensorServer?   = null
    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MonitoringService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("⏳ Starting..."))
        initModel()
        startSensorServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MonitoringService started")
        updateNotification(" Monitoring active — waiting for ESP32")
        return START_STICKY   // restart automatically if killed
    }

    // ── Init ML model ──────────────────────────────────
    private fun initModel() {
        scope.launch {
            try {
                accidentModel = AccidentModel(applicationContext)
                accidentModel?.loadModel()
                Log.d(TAG, "ML model loaded in service")
            } catch (e: Exception) {
                Log.e(TAG, "Model load failed: ${e.message}")
            }
        }
    }

    // ── Start HTTP server to receive ESP32 data ─────────
    private fun startSensorServer() {
        try {
            sensorServer = SensorServer(8080) { ax, ay, az, gx, gy, gz ->
                onDataReceived(ax, ay, az, gx, gy, gz)
            }
            sensorServer?.start()
            Log.d(TAG, "Sensor server started on port 8080")
        } catch (e: Exception) {
            Log.e(TAG, "Server start failed: ${e.message}")
        }
    }

    // ── Called every time ESP32 sends a reading ─────────
    private fun onDataReceived(
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float
    ) {
        // Update shared state — default 0.0 until connected
        currentAx   = ax
        currentAy   = ay
        currentAz   = az
        currentGx   = gx
        currentGy   = gy
        currentGz   = gz
        isConnected = true   // ESP32 is now connected

        // Run ML prediction
        val score = accidentModel?.predict(ax, ay, az, gx, gy, gz) ?: 0.0f
        lastScore = score

        Log.d(TAG, "Data: ax=$ax ay=$ay az=$az | Score=$score")

        // Notify MainActivity via broadcast
        val intent = Intent("SENSOR_DATA").apply {
            putExtra("ax",    ax)
            putExtra("ay",    ay)
            putExtra("az",    az)
            putExtra("gx",    gx)
            putExtra("gy",    gy)
            putExtra("gz",    gz)
            putExtra("score", score)
        }
        sendBroadcast(intent)

        // Update notification
        val magnitude = Math.sqrt((ax*ax + ay*ay + az*az).toDouble())
        if (score > 0.5f) {
            updateNotification("ACCIDENT DETECTED! Score: ${"%.2f".format(score)}")
        } else {
            updateNotification(" Monitoring | mag: ${"%.2f".format(magnitude)}g | score: ${"%.3f".format(score)}")
        }
    }

    // ── Notification helpers ────────────────────────────
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Accident Monitor",
            NotificationManager.IMPORTANCE_LOW   // LOW = no sound for every update
        ).apply {
            description = "Shows monitoring status in background"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        // Tap notification → open app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Accident Detector")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)       // can't be dismissed by user
            .setSilent(true)        // no sound on update
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── Lifecycle ───────────────────────────────────────
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorServer?.stop()
        accidentModel?.close()
        serviceJob.cancel()
        isConnected = false
        Log.d(TAG, "MonitoringService destroyed")
    }
}