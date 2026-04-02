package ch.gypaete.tracker

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ch.gypaete.tracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val REQ_LOCATION   = 100
        const val REQ_BACKGROUND = 101
        const val REQ_NOTIF      = 102
    }

    private var pendingName: String? = null

    // Receiver pour stats live
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (TrackerService.isRunning) updateLiveStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("tracker", MODE_PRIVATE)
        binding.etPilotName.setText(prefs.getString("pilot_name", ""))

        binding.btnStart.setOnClickListener {
            val name = binding.etPilotName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Entrez un nom de pilote", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("pilot_name", name).apply()
            pendingName = name
            requestLocationPermission(name)
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, TrackerService::class.java))
            updateUI(false)
        }

        updateUI(TrackerService.isRunning)
    }

    // ── Permissions — séquence propre sans bloquer l'UI ───
    private fun requestLocationPermission(name: String) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onLocationGranted(name)
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_LOCATION)
        }
    }

    private fun onLocationGranted(name: String) {
        // Permission notif Android 13+ — non bloquante, on démarre quand même
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF
                )
                // On démarre sans attendre la réponse notif
            }
        }
        // Démarrer le service immédiatement
        startTracker(name)
        // Demander la permission background séparément (ne bloque pas)
        requestBackgroundLocation()
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Délai court pour que l'UI ne gèle pas
                binding.root.postDelayed({
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQ_BACKGROUND
                    )
                }, 800)
            }
        }
    }

    private fun startTracker(name: String) {
        val intent = Intent(this, TrackerService::class.java).apply {
            putExtra("pilot_name", name)
        }
        ContextCompat.startForegroundService(this, intent)
        updateUI(true)
        Toast.makeText(this, "✅ Tracking démarré — GPS actif", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pendingName?.let { onLocationGranted(it) }
                } else {
                    Toast.makeText(this, "❌ Permission GPS requise", Toast.LENGTH_LONG).show()
                    updateUI(false)
                }
            }
            REQ_BACKGROUND -> {
                // Pas critique — le service tourne déjà
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "⚠️ GPS arrière-plan limité", Toast.LENGTH_SHORT).show()
                }
            }
            // REQ_NOTIF : pas critique
        }
    }

    // ── Stats live ─────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(TrackerService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateReceiver, filter)
        }
        updateUI(TrackerService.isRunning)
        if (TrackerService.isRunning) updateLiveStats()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
    }

    private fun updateLiveStats() {
        binding.tvAlt.text   = if (TrackerService.lastAlt != null) "${TrackerService.lastAlt} m" else "— m"
        binding.tvSpd.text   = if (TrackerService.lastSpd != null) "${TrackerService.lastSpd} km/h" else "— km/h"
        binding.tvCount.text = "${TrackerService.sendCount} envois"
        val g = TrackerService.lastG
        if (g != null) {
            binding.tvG.text = String.format("%.2f G", g)
            binding.tvG.setTextColor(when {
                g > 2.5 -> getColor(R.color.red)
                g > 1.5 -> getColor(R.color.orange)
                g < 0.3 -> getColor(R.color.purple)
                else    -> getColor(R.color.green)
            })
        } else {
            binding.tvG.text = "— G"
        }
    }

    private fun updateUI(running: Boolean) {
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled  = running
        binding.tvStatus.text = if (running) "● Actif — GPS en cours" else "○ Inactif"
        binding.tvStatus.setTextColor(
            if (running) getColor(R.color.green) else getColor(R.color.red)
        )
        if (!running) {
            binding.tvAlt.text = "— m"
            binding.tvSpd.text = "— km/h"
            binding.tvG.text   = "— G"
            binding.tvCount.text = "0 envois"
        }
    }
}
