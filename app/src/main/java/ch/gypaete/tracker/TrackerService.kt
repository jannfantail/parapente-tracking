package ch.gypaete.tracker

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
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

class TrackerService : Service() {

    companion object {
        var isRunning = false
        const val SERVER_URL = "https://localisation.gypaete-parapente.ch:8443/position"
        const val CHANNEL_ID = "parapente_tracker"
        const val NOTIF_ID = 1
        const val INTERVAL_MS = 2000L
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var pilotName = "Pilote"
    private var sendCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        pilotName = intent?.getStringExtra("pilot_name") ?: "Pilote"
        isRunning = true

        val notification = buildNotification("Démarrage GPS…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

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
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun sendPosition(location: Location) {
        val alt = if (location.hasAltitude()) location.altitude.toInt() else null
        val spd = if (location.hasSpeed()) (location.speed * 3.6).toInt() else null
        val acc = location.accuracy.toInt()

        val json = JSONObject().apply {
            put("id", pilotName)
            put("lat", location.latitude)
            put("lng", location.longitude)
            if (alt != null) put("alt", alt) else put("alt", JSONObject.NULL)
            put("acc", acc)
            if (spd != null) put("spd", spd) else put("spd", JSONObject.NULL)
            if (location.hasBearing()) put("hdg", location.bearing.toInt()) else put("hdg", JSONObject.NULL)
            put("ts", System.currentTimeMillis())
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(SERVER_URL).post(body).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    sendCount++
                    updateNotification("🪂 $pilotName | Alt: ${alt ?: "—"}m | Envois: $sendCount")
                }
                response.close()
            }
            override fun onFailure(call: Call, e: IOException) {
                updateNotification("⚠️ Réseau indisponible — GPS actif")
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
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Parapente Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Arrêter", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Tracker GPS",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracking GPS parapente en arrière-plan"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
