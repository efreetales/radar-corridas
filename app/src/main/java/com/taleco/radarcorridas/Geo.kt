package com.taleco.radarcorridas

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

object Geo {

    private val main = Handler(Looper.getMainLooper())

    /** Distância em metros entre dois pontos. */
    fun meters(a: LatLng, b: LatLng): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    fun of(l: Location) = LatLng(l.latitude, l.longitude)

    /**
     * Transforma um endereço da oferta em coordenadas, sem travar a tela.
     * O resultado volta na thread principal (null se não encontrar).
     */
    fun geocode(ctx: Context, address: String?, onDone: (LatLng?) -> Unit) {
        // Endereço lido pela metade ("ER", "D", "Prt"...) não dá para localizar: melhor não arriscar
        val letters = address?.count { it.isLetter() } ?: 0
        if (address.isNullOrBlank() || letters < 6 || !Geocoder.isPresent()) {
            onDone(null)
            return
        }
        val app = ctx.applicationContext
        Thread {
            val result: LatLng? = try {
                val query = if (address.contains("Brasil", ignoreCase = true)) address else "$address, Brasil"
                @Suppress("DEPRECATION")
                val list = Geocoder(app, PT_BR).getFromLocationName(query, 1)
                list?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                    // Só vale dentro da Grande SP (evita cair no meio do Brasil quando o endereço é incompleto)
                    ?.takeIf { it.lat in -24.3..-23.1 && it.lng in -47.4..-46.0 }
            } catch (e: Exception) {
                null
            }
            main.post { onDone(result) }
        }.start()
    }

    /** Última posição conhecida do celular, se houver permissão. */
    fun lastKnown(ctx: Context): LatLng? {
        return try {
            val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
            val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p ->
                    try {
                        @Suppress("MissingPermission")
                        lm.getLastKnownLocation(p)
                    } catch (e: Exception) {
                        null
                    }
                }
            val best = candidates.maxByOrNull { it.time } ?: return null
            // Só vale se for recente (até 3 minutos).
            if (System.currentTimeMillis() - best.time > 180_000) null else of(best)
        } catch (e: Exception) {
            null
        }
    }
}
