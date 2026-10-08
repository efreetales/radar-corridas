package com.taleco.radarcorridas

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Serviço que liga o GPS durante uma corrida aceita e/ou para o alerta de radares.
 * Mostra uma notificação fixa enquanto grava o percurso (exigência do Android).
 */
class TrackingService : Service(), LocationListener {

    companion object {
        private const val CHANNEL = "percurso"
        private const val NOTIF_ID = 42

        /** Motivos para o GPS estar ligado. */
        const val CORRIDA = "corrida"
        const val RADARES = "radares"

        private val reasons: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        /** Quem recebe cada ponto do GPS durante a corrida (o TripTracker). */
        @Volatile
        var onPoint: ((Location) -> Unit)? = null

        @Volatile
        var running = false
            private set

        fun start(ctx: Context, reason: String): Boolean {
            reasons.add(reason)
            return try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
                true
            } catch (e: Exception) {
                reasons.remove(reason)
                false
            }
        }

        fun stop(ctx: Context, reason: String) {
            reasons.remove(reason)
            try {
                if (reasons.isEmpty()) {
                    ctx.stopService(Intent(ctx, TrackingService::class.java))
                } else if (running) {
                    // Ainda há outro motivo: só atualiza o aviso e a frequência do GPS.
                    ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
                }
            } catch (_: Exception) {
            }
        }
    }

    private var lm: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle("Radar Corridas")
            .setContentText(statusText())
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            OfferLog.appendDiag(this, "PERCURSO: o Android não deixou ligar o GPS (${e.javaClass.simpleName}: ${e.message})")
            stopSelf()
            return START_NOT_STICKY
        }
        if (reasons.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        startUpdates()
        return START_NOT_STICKY
    }

    private fun statusText(): String {
        val r = reasons.toSet()
        return when {
            CORRIDA in r && RADARES in r -> "Registrando a corrida · alerta de radares ligado"
            CORRIDA in r -> "Registrando o percurso da corrida"
            else -> "Alerta de radares ligado"
        }
    }

    /** Intervalo atual do GPS: 1 s com alerta de radar (precisa da velocidade exata), 3 s só com corrida. */
    private var currentFast: Boolean? = null

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        val fast = RADARES in reasons
        if (lm != null && currentFast == fast) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        try { lm?.removeUpdates(this) } catch (_: Exception) {}
        lm = manager
        currentFast = fast
        try {
            if (fast) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            } else {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 10f, this, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            OfferLog.appendDiag(this, "GPS indisponível (${e.message})")
        }
        try {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 20f, this, Looper.getMainLooper())
        } catch (_: Exception) {
        }
    }

    override fun onLocationChanged(location: Location) {
        onPoint?.invoke(location)
        SpeedWatch.onLocation(this, location)
    }

    // Necessários em versões antigas do Android.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        try { lm?.removeUpdates(this) } catch (_: Exception) {}
        lm = null
        currentFast = null
        running = false
        reasons.clear()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Percurso das corridas", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
