package ch.gypaete.tracker

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class TrackerService : Service(), SensorEventListener {

    companion object {
        var isRunning = false
        var lastStatus = "—"
        var lastAlt: Int? = null
        var lastSpd: Int? = null
        var lastG: Double? = null
        var sendCount = 0
        const val SERVER_URL = "https://localisation.gypaete-parapente.ch:8443/position"
        const val CHANNEL_ID = "parapente_tracker"
        const val NOTIF_ID = 1
        const val INTERVAL_MS = 2000L
        const val ACTION_UPDATE = "ch.gypaete.tracker.UPDATE"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null

    // Données accéléromètre avec gravité (= référence 1G)
    private var gx = 0f; private var gy = 0f; private var gz = 0f

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var pilotName = "Pilote"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialiser accéléromètre
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }

        pilotName = intent?.getStringExtra("pilot_name") ?: "Pilote"
        isRunning = true
        sendCount = 0

        val notification = buildNotification("Démarrage GPS…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // Démarrer accéléromètre (50Hz = SENSOR_DELAY_GAME)
        accelSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        startLocationUpdates()
        return START_STICKY
    }

    // ── Accéléromètre ──────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // accelerationIncludingGravity : au repos = ~9.81 m/s² sur Z = 1G
            gx = event.values[0]
            gy = event.values[1]
            gz = event.values[2]
            // Calcul facteur de charge en G : norme du vecteur / g standard
            val g = sqrt((gx*gx + gy*gy + gz*gz).toDouble()) / 9.80665
            lastG = Math.round(g * 100.0) / 100.0
            // Notifier l'activité en temps réel
            sendBroadcast(Intent(ACTION_UPDATE))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── GPS ────────────────────────────────────────────────
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { sendPosition(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) { stopSelf() }
    }

    private fun sendPosition(location: Location) {
        val alt = if (location.hasAltitude()) location.altitude.toInt() else null
        val spd = if (location.hasSpeed()) (location.speed * 3.6).toInt() else null
        val acc = location.accuracy.toInt()
        val currentG = lastG

        lastAlt = alt; lastSpd = spd

        val json = JSONObject().apply {
            put("id",  pilotName)
            put("lat", location.latitude)
            put("lng", location.longitude)
            if (alt != null) put("alt", alt) else put("alt", JSONObject.NULL)
            put("acc", acc)
            if (spd != null) put("spd", spd) else put("spd", JSONObject.NULL)
            if (location.hasBearing()) put("hdg", location.bearing.toInt()) else put("hdg", JSONObject.NULL)
            put("ts",  System.currentTimeMillis())
            // Accélération en G
            if (currentG != null) {
                put("gForce", currentG)
                put("ax", Math.round(gx * 100.0) / 100.0)
                put("ay", Math.round(gy * 100.0) / 100.0)
                put("az", Math.round(gz * 100.0) / 100.0)
            }
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url(SERVER_URL).post(body).build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    sendCount++
                    val gStr = currentG?.let { " | ${String.format("%.2f", it)}G" } ?: ""
                    lastStatus = "🪂 $pilotName | Alt: ${alt ?: "—"}m | ${spd ?: "—"}km/h$gStr"
                    updateNotification(lastStatus)
                    sendBroadcast(Intent(ACTION_UPDATE))
                }
                response.close()
            }
            override fun onFailure(call: Call, e: IOException) {
                lastStatus = "⚠️ Réseau — GPS actif ($sendCount envois)"
                updateNotification(lastStatus)
            }
        })
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, TrackerService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🪂 Parapente Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Arrêter", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Tracker GPS", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tracking GPS parapente en arrière-plan" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        sensorManager?.unregisterListener(this)
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
