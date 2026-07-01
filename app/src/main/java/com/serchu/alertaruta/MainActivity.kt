package com.serchu.alertaruta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchAlerta: Switch
    private lateinit var textEstado: TextView

    // Launcher para pedir permiso de notificaciones (Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAlertService()
        } else {
            switchAlerta.isChecked = false
            textEstado.text = "Necesitás dar permiso de notificaciones para que funcione"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchAlerta = findViewById(R.id.switchAlerta)
        textEstado = findViewById(R.id.textEstado)

        switchAlerta.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestIgnoreBatteryOptimizations()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startAlertService()
                }
            } else {
                stopAlertService()
            }
        }
    }

    /**
     * En HyperOS/MIUI, si Android mata la app en background, el servicio se corta.
     * Esto le pide al sistema que no la optimice. En algunos Xiaomi también conviene
     * ir a Ajustes > Apps > Alerta Ruta > Batería > "Sin restricciones" a mano.
     */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                // Algunos dispositivos bloquean este intent directo, no pasa nada
            }
        }
    }

    private fun startAlertService() {
        val intent = Intent(this, AlertService::class.java)
        ContextCompat.startForegroundService(this, intent)
        textEstado.text = "Alerta activa: vibrando cada 2 minutos"
    }

    private fun stopAlertService() {
        val intent = Intent(this, AlertService::class.java)
        stopService(intent)
        textEstado.text = "Alerta detenida"
    }

    override fun onResume() {
        super.onResume()
        switchAlerta.isChecked = AlertService.isRunning
        textEstado.text = if (AlertService.isRunning)
            "Alerta activa: vibrando cada 2 minutos"
        else
            "Alerta detenida"
    }
}
