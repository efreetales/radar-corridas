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
 * Serviço de localização, com dois modos:
 *  - TURNO (online, sem corrida): só "pega carona" nas posições que o celular já recebe
 *    (a Uber usa o GPS o tempo todo). Não liga GPS nenhum, então não gasta bateria a mais.
 *  - CORRIDA: liga o GPS também, para o percurso da corrida ficar completo.
 * Mostra uma notificação fixa enquanto roda (exigência do Android).
 */
class TrackingService : Service() {

    companion object {
        private const val CHANNEL = "percurso"
        private const val NOTIF_ID = 42

        /** Quem recebe os pontos do GPS durante a corrida (o TripTracker). */
        @Volatile
        var onPoint: ((Location) -> Unit)? = null

        @Volatile
        var running = false
            private set

        @Volatile private var wantTrip = false
        @Volatile private var instance: TrackingService? = null

        /** Liga, desliga ou troca o modo conforme o turno e a corrida. */
        fun sync(ctx: Context, prefs: Prefs) {
            val trip = TripTracker.isOnTrip
            val session = Trajeto.online && prefs.trackRoute
            wantTrip = trip
            if (!trip && !session) {
                if (running || instance != null) stop(ctx)
                return
            }
            val svc = instance
            if (svc != null) {
                svc.applyMode()
            } else {
                try {
                    ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
                } catch (e: Exception) {
                    OfferLog.appendDiag(ctx, "PERCURSO: não foi possível ligar (${e.javaClass.simpleName}: ${e.message})")
                }
            }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, TrackingService::class.java)) } catch (_: Exception) {}
        }
    }

    private var lm: LocationManager? = null
    private var passiveOn = false
    private var gpsOn = false
    private var notifiedTrip: Boolean? = null

    /** Todas as posições do celular (de qualquer app) → trajeto do turno. */
    private val passiveListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Trajeto.onLocation(this@TrackingService, location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /** GPS ligado por nós, só durante a corrida → percurso da corrida. */
    private val tripListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onPoint?.invoke(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification(wantTrip), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, notification(wantTrip))
            }
            notifiedTrip = wantTrip
        } catch (e: Exception) {
            OfferLog.appendDiag(this, "PERCURSO: o Android não deixou ligar o GPS (${e.javaClass.simpleName}: ${e.message})")
            stopSelf()
            return START_NOT_STICKY
        }
        instance = this
        running = true
        applyMode()
        return START_NOT_STICKY
    }

    private fun notification(trip: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle("Radar Corridas")
            .setContentText(if (trip) "Registrando o percurso da corrida" else "Online: registrando o trajeto do turno")
            .setOngoing(true)
            .setSilent(true)
            .build()

    @SuppressLint("MissingPermission")
    fun applyMode() {
        val manager = lm ?: getSystemService(LocationManager::class.java)?.also { lm = it } ?: return
        if (!passiveOn) {
            try {
                manager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 2000L, 0f, passiveListener, Looper.getMainLooper())
                passiveOn = true
            } catch (e: Exception) {
                OfferLog.appendDiag(this, "PERCURSO: posição de carona indisponível (${e.message})")
            }
        }
        if (wantTrip && !gpsOn) {
            try {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 10f, tripListener, Looper.getMainLooper())
                gpsOn = true
            } catch (e: Exception) {
                OfferLog.appendDiag(this, "PERCURSO: GPS indisponível (${e.message})")
            }
            try {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 20f, tripListener, Looper.getMainLooper())
            } catch (_: Exception) {
            }
        } else if (!wantTrip && gpsOn) {
            try { manager.removeUpdates(tripListener) } catch (_: Exception) {}
            gpsOn = false
        }
        if (notifiedTrip != wantTrip) {
            notifiedTrip = wantTrip
            try { getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notification(wantTrip)) } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        try { lm?.removeUpdates(passiveListener) } catch (_: Exception) {}
        try { lm?.removeUpdates(tripListener) } catch (_: Exception) {}
        passiveOn = false
        gpsOn = false
        lm = null
        running = false
        if (instance === this) instance = null
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
