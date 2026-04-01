package ch.gypaete.tracker

import android.Manifest
import android.content.Intent
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
    private val PERMISSIONS_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Charger le nom sauvegardé
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

        // Vérifier si le service tourne déjà
        updateUI(TrackerService.isRunning)
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

        // Demander la permission GPS arrière-plan séparément (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
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
        binding.btnStop.isEnabled = running
        binding.tvStatus.text = if (running) "● Actif — GPS en cours" else "○ Inactif"
        binding.tvStatus.setTextColor(
            if (running) getColor(R.color.green) else getColor(R.color.red)
        )
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

    override fun onResume() {
        super.onResume()
        updateUI(TrackerService.isRunning)
    }
}
