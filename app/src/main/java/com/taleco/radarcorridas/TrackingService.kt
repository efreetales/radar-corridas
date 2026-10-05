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
 * Serviço que liga o GPS só durante uma corrida aceita.
 * Mostra uma notificação fixa enquanto grava o percurso (exigência do Android).
 */
class TrackingService : Service(), LocationListener {

    companion object {
        private const val CHANNEL = "percurso"
        private const val NOTIF_ID = 42

        /** Quem recebe cada ponto do GPS (o TripTracker). */
        @Volatile
        var onPoint: ((Location) -> Unit)? = null

        @Volatile
        var running = false
            private set

        fun start(ctx: Context): Boolean = try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
            true
        } catch (e: Exception) {
            false
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, TrackingService::class.java)) } catch (_: Exception) {}
        }
    }

    private var lm: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle("Radar Corridas")
            .setContentText("Registrando o percurso da corrida")
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
        running = true
        startUpdates()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (lm != null) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        lm = manager
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 10f, this, Looper.getMainLooper())
        } catch (e: Exception) {
            OfferLog.appendDiag(this, "PERCURSO: GPS indisponível (${e.message})")
        }
        try {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 20f, this, Looper.getMainLooper())
        } catch (_: Exception) {
        }
    }

    override fun onLocationChanged(location: Location) {
        onPoint?.invoke(location)
    }

    // Necessários em versões antigas do Android.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        try { lm?.removeUpdates(this) } catch (_: Exception) {}
        lm = null
        running = false
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
