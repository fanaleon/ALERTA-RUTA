package com.serchu.alertaruta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Servicio en foreground que cada 2 minutos dispara una "ráfaga" de notificaciones.
 * El Redmi Watch 5 no tiene SDK abierto para mandarle patrones de vibración custom,
 * pero SÍ espeja las notificaciones del teléfono (vibra cuando llega una). Por eso
 * la app manda 1 notificación (1 vibrada) o 3 notificaciones seguidas (3 vibradas),
 * alternando cada ciclo. El celular vibra también en simultáneo.
 */
class AlertService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var pulseCount = 1
    private var notifIdCounter = 1000

    private val tickRunnable = object : Runnable {
        override fun run() {
            fireAlert(pulseCount)
            pulseCount = if (pulseCount == 1) 3 else 1
            handler.postDelayed(this, INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val foregroundNotif = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setContentTitle("Alerta Ruta activa")
            .setContentText("Vibrando cada 2 minutos para mantenerte alerta")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, foregroundNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, foregroundNotif)
        }

        isRunning = true
        handler.removeCallbacks(tickRunnable)
        pulseCount = 1
        handler.postDelayed(tickRunnable, INTERVAL_MILLIS)
        return START_STICKY
    }

    private fun fireAlert(times: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (i in 0 until times) {
            handler.postDelayed({
                val id = notifIdCounter++
                val notif = NotificationCompat.Builder(this, CHANNEL_ALERT)
                    .setContentTitle("¡Atento!")
                    .setContentText(if (times == 1) "Pulso simple" else "Pulso triple")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setVibrate(longArrayOf(0, 400))
                    .build()
                nm.notify(id, notif)
                // La borramos a los pocos segundos para no ensuciar la barra de notificaciones
                handler.postDelayed({ nm.cancel(id) }, 3000)
            }, i * 700L)
        }
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS, "Estado del servicio", NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(statusChannel)

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT, "Alertas de vibración", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400)
        }
        nm.createNotificationChannel(alertChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val CHANNEL_STATUS = "status_channel"
        const val CHANNEL_ALERT = "alert_channel"
        const val INTERVAL_MILLIS = 2 * 60 * 1000L
        var isRunning = false
    }
}
