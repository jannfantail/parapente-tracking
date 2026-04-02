package ch.gypaete.tracker

import android.content.*
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import ch.gypaete.tracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMISSIONS_REQUEST = 100

    // Receiver pour mises à jour en temps réel depuis le service
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
            checkPermissionsAndStart(name)
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, TrackerService::class.java))
            updateUI(false)
        }

        updateUI(TrackerService.isRunning)
    }

    override fun onResume() {
        super.onResume()
        // Écouter les mises à jour du service
        val filter = IntentFilter(TrackerService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        updateUI(TrackerService.isRunning)
        if (TrackerService.isRunning) updateLiveStats()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (e: Exception) {}
    }

    private fun updateLiveStats() {
        val alt  = TrackerService.lastAlt
        val spd  = TrackerService.lastSpd
        val g    = TrackerService.lastG
        val n    = TrackerService.sendCount

        binding.tvAlt.text   = if (alt != null) "${alt} m" else "— m"
        binding.tvSpd.text   = if (spd != null) "${spd} km/h" else "— km/h"
        binding.tvCount.text = "$n envois"

        if (g != null) {
            val gStr = String.format("%.2f G", g)
            binding.tvG.text = gStr
            // Couleur selon intensité
            val col = when {
                g > 2.5  -> getColor(R.color.red)
                g > 1.5  -> getColor(R.color.orange)
                g < 0.3  -> getColor(R.color.purple)
                else     -> getColor(R.color.green)
            }
            binding.tvG.setTextColor(col)
        } else {
            binding.tvG.text = "— G"
        }
    }

    private fun checkPermissionsAndStart(name: String) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST)
        } else {
            startTracker(name)
        }
    }

    private fun startTracker(name: String) {
        val intent = Intent(this, TrackerService::class.java).apply {
            putExtra("pilot_name", name)
        }
        ContextCompat.startForegroundService(this, intent)
        updateUI(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSIONS_REQUEST + 1
                )
            }
        }
    }

    private fun updateUI(running: Boolean) {
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled  = running
        binding.tvStatus.text = if (running) "● Actif — GPS en cours" else "○ Inactif"
        binding.tvStatus.setTextColor(
            if (running) getColor(R.color.green) else getColor(R.color.red)
        )
        // Réinitialiser les stats si arrêté
        if (!running) {
            binding.tvAlt.text   = "— m"
            binding.tvSpd.text   = "— km/h"
            binding.tvG.text     = "— G"
            binding.tvCount.text = "0 envois"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            val name = binding.etPilotName.text.toString().trim()
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startTracker(name)
            } else {
                Toast.makeText(this, "Permission GPS requise", Toast.LENGTH_LONG).show()
            }
        }
    }
}
