package ch.gypaete.tracker

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.*
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
        var isRunning  = false
        var lastAlt:   Int?    = null
        var lastSpd:   Int?    = null
        var lastG:     Double? = null
        var sendCount  = 0
        const val SERVER_URL  = "https://localisation.gypaete-parapente.ch:8443/position"
        const val CHANNEL_ID  = "parapente_tracker"
        const val NOTIF_ID    = 1
        const val INTERVAL_MS = 2000L
        const val ACTION_UPDATE = "ch.gypaete.tracker.UPDATE"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var gpsHandlerThread: HandlerThread
    private lateinit var gpsHandler: Handler

    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var gx = 0f; private var gy = 0f; private var gz = 0f

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var pilotName = "Pilote"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelSensor   = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Thread dédié pour les callbacks GPS — évite de bloquer le Main thread
        gpsHandlerThread = HandlerThread("GPSThread").also { it.start() }
        gpsHandler = Handler(gpsHandlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }

        pilotName  = intent?.getStringExtra("pilot_name") ?: "Pilote"
        isRunning  = true
        sendCount  = 0
        lastAlt    = null; lastSpd = null; lastG = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification("Démarrage GPS…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, buildNotification("Démarrage GPS…"))
        }

        // Accéléromètre sur le main thread (capteur léger)
        accelSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        startLocationUpdates()
        return START_STICKY
    }

    // ── Accéléromètre ──────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        gx = event.values[0]; gy = event.values[1]; gz = event.values[2]
        // Norme du vecteur gravité incluse / g standard = facteur de charge
        lastG = Math.round(sqrt((gx*gx + gy*gy + gz*gz).toDouble()) / 9.80665 * 100.0) / 100.0
        sendBroadcast(Intent(ACTION_UPDATE))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── GPS ────────────────────────────────────────────────
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS
        ).setMinUpdateIntervalMillis(INTERVAL_MS)
         .setWaitForAccurateLocation(false)
         .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { sendPosition(it) }
            }
        }
        try {
            // Utiliser gpsHandler (thread dédié) au lieu du main looper
            fusedLocationClient.requestLocationUpdates(request, locationCallback, gpsHandler.looper)
        } catch (e: SecurityException) {
            updateNotification("❌ Permission GPS manquante")
            stopSelf()
        }
    }

    private fun sendPosition(location: Location) {
        val alt = if (location.hasAltitude()) location.altitude.toInt() else null
        val spd = if (location.hasSpeed()) (location.speed * 3.6).toInt() else null
        val acc = location.accuracy.toInt()
        val g   = lastG

        lastAlt = alt; lastSpd = spd

        val json = JSONObject().apply {
            put("id",  pilotName)
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("alt", alt ?: JSONObject.NULL)
            put("acc", acc)
            put("spd", spd ?: JSONObject.NULL)
            put("hdg", if (location.hasBearing()) location.bearing.toInt() else JSONObject.NULL)
            put("ts",  System.currentTimeMillis())
            if (g != null) {
                put("gForce", g)
                put("ax", Math.round(gx * 100.0) / 100.0)
                put("ay", Math.round(gy * 100.0) / 100.0)
                put("az", Math.round(gz * 100.0) / 100.0)
            }
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder().url(SERVER_URL).post(body).build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.close()
                if (response.isSuccessful) {
                    sendCount++
                    val gStr = g?.let { " | ${String.format("%.2f", it)}G" } ?: ""
                    updateNotification("🪂 $pilotName | ${alt ?: "—"}m | ${spd ?: "—"}km/h$gStr | $sendCount envois")
                    sendBroadcast(Intent(ACTION_UPDATE))
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                updateNotification("⚠️ Réseau — GPS actif ($sendCount envois)")
            }
        })
    }

    // ── Notification ───────────────────────────────────────
    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopPI = PendingIntent.getService(
            this, 0,
            Intent(this, TrackerService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPI = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🪂 Parapente Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openPI)
            .addAction(android.R.drawable.ic_delete, "Arrêter", stopPI)
            .build()
    }

    private fun createNotificationChannel() {
        val chan = NotificationChannel(
            CHANNEL_ID, "Tracker GPS", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tracking GPS parapente" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(chan)
    }

    // ── Nettoyage ──────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        sensorManager?.unregisterListener(this)
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        if (::gpsHandlerThread.isInitialized)
            gpsHandlerThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
