package com.accident.detector

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Core components
    private lateinit var tts: TextToSpeech
    private lateinit var alertManager: AlertManager
    private lateinit var locationClient: FusedLocationProviderClient

    // UI
    private lateinit var tvStatus    : TextView
    private lateinit var tvAx        : TextView
    private lateinit var tvAy        : TextView
    private lateinit var tvAz        : TextView
    private lateinit var tvGx        : TextView
    private lateinit var tvGy        : TextView
    private lateinit var tvGz        : TextView
    private lateinit var tvMagnitude : TextView
    private lateinit var tvScore     : TextView
    private lateinit var tvCountdown : TextView
    private lateinit var btnImOk     : Button
    private lateinit var etFamily    : EditText
    private lateinit var etPolice    : EditText
    private lateinit var etAmbulance : EditText
    private lateinit var btnSaveContacts : Button

    // State
    private var isAlertActive = false
    private var countdownTimer: CountDownTimer? = null
    private var currentLat = 0.0
    private var currentLng = 0.0
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val PERMISSION_CODE = 101
    private val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    // ── Broadcast receiver — gets data from MonitoringService ──
    private val sensorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ax    = intent?.getFloatExtra("ax",    0f) ?: 0f
            val ay    = intent?.getFloatExtra("ay",    0f) ?: 0f
            val az    = intent?.getFloatExtra("az",    0f) ?: 0f
            val gx    = intent?.getFloatExtra("gx",    0f) ?: 0f
            val gy    = intent?.getFloatExtra("gy",    0f) ?: 0f
            val gz    = intent?.getFloatExtra("gz",    0f) ?: 0f
            val score = intent?.getFloatExtra("score", 0f) ?: 0f
            updateUI(ax, ay, az, gx, gy, gz, score)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        requestAllPermissions()
        setupComponents()
        loadSavedContacts()
        startMonitoringService()
    }

    override fun onResume() {
        super.onResume()
        // Start listening to sensor broadcast
        registerReceiver(
            sensorReceiver,
            IntentFilter("SENSOR_DATA"),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(sensorReceiver)
    }

    private fun initViews() {
        tvStatus         = findViewById(R.id.tvStatus)
        tvAx             = findViewById(R.id.tvAx)
        tvAy             = findViewById(R.id.tvAy)
        tvAz             = findViewById(R.id.tvAz)
        tvGx             = findViewById(R.id.tvGx)
        tvGy             = findViewById(R.id.tvGy)
        tvGz             = findViewById(R.id.tvGz)
        tvMagnitude      = findViewById(R.id.tvMagnitude)
        tvScore          = findViewById(R.id.tvScore)
        tvCountdown      = findViewById(R.id.tvCountdown)
        btnImOk          = findViewById(R.id.btnImOk)
        etFamily         = findViewById(R.id.etFamily)
        etPolice         = findViewById(R.id.etPolice)
        etAmbulance      = findViewById(R.id.etAmbulance)
        btnSaveContacts  = findViewById(R.id.btnSaveContacts)

        btnImOk.isEnabled = false
        btnImOk.setOnClickListener { userIsOk() }
        btnSaveContacts.setOnClickListener { saveContacts() }
    }

    private fun setupComponents() {
        tts            = TextToSpeech(this, this)
        alertManager   = AlertManager(this)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    // ── Start background service ────────────────────────
    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Log.d("MainActivity", "MonitoringService started")
    }

    // ── UI update from broadcast ────────────────────────
    private fun updateUI(
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float,
        score: Float
    ) {
        val magnitude = Math.sqrt((ax*ax + ay*ay + az*az).toDouble())

        tvAx.text        = "ax: ${"%.3f".format(ax)} g"
        tvAy.text        = "ay: ${"%.3f".format(ay)} g"
        tvAz.text        = "az: ${"%.3f".format(az)} g"
        tvGx.text        = "gx: ${"%.2f".format(gx)} °/s"
        tvGy.text        = "gy: ${"%.2f".format(gy)} °/s"
        tvGz.text        = "gz: ${"%.2f".format(gz)} °/s"
        tvMagnitude.text = "Magnitude: ${"%.3f".format(magnitude)} g"
        tvScore.text     = "AI Score: ${"%.4f".format(score)}"

        // Show connection status
        if (MonitoringService.isConnected) {
            tvStatus.text = "🟢 ESP32 connected — monitoring active"
        }

        // Trigger alert if accident and not already alerting
        if (score > 0.5f && !isAlertActive) {
            triggerAccidentAlert()
        }
    }

    // ── Accident alert flow ─────────────────────────────
    private fun triggerAccidentAlert() {
        isAlertActive     = true
        btnImOk.isEnabled = true
        tvStatus.text     = " ACCIDENT DETECTED!"
        fetchLocation()
        speak("Accident detected! Are you OK? Press I am OK button. You have 30 seconds.")
        startCountdown()
    }

    private fun startCountdown() {
        tvCountdown.text = "30"
        countdownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisLeft: Long) {
                val sec = (millisLeft / 1000).toInt()
                tvCountdown.text = sec.toString()
                if (sec == 15) speak("15 seconds remaining. Are you OK?")
                if (sec == 5)  speak("5 seconds. Sending alert now if no response.")
            }
            override fun onFinish() {
                tvCountdown.text = "0"
                speak("No response. Sending emergency alert.")
                triggerEmergency()
            }
        }.start()
    }

    private fun userIsOk() {
        countdownTimer?.cancel()
        isAlertActive     = false
        btnImOk.isEnabled = false
        tvCountdown.text  = ""
        tvStatus.text     = " Monitoring active — you confirmed OK"
        speak("Glad you are OK. Stay safe. Monitoring continues.")
    }

    private fun triggerEmergency() {
        isAlertActive     = false
        btnImOk.isEnabled = false
        tvCountdown.text  = ""
        tvStatus.text     = "📡 Sending emergency alerts..."
        scope.launch(Dispatchers.IO) {
            alertManager.triggerEmergency(currentLat, currentLng)
            withContext(Dispatchers.Main) {
                tvStatus.text = "Emergency alerts sent!"
            }
        }
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    currentLat = it.latitude
                    currentLng = it.longitude
                }
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun saveContacts() {
        alertManager.saveContacts(
            family    = etFamily.text.toString().trim(),
            police    = etPolice.text.toString().trim(),
            ambulance = etAmbulance.text.toString().trim()
        )
        Toast.makeText(this, " Contacts saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadSavedContacts() {
        val prefs = getSharedPreferences("contacts", MODE_PRIVATE)
        etFamily.setText(prefs.getString("family", ""))
        etPolice.setText(prefs.getString("police", "100"))
        etAmbulance.setText(prefs.getString("ambulance", "108"))
    }

    private fun requestAllPermissions() {
        val notGranted = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, notGranted.toTypedArray(), PERMISSION_CODE
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language    = Locale.US
            tts.setSpeechRate(0.9f)
            speak("Accident detector ready. Stay safe.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        scope.cancel()
        countdownTimer?.cancel()
    }
}