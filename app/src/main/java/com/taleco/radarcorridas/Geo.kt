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
    /** Endereço lido pela metade às vezes cai em outro estado: resultado mais longe que isso é descartado. */
    private const val MAX_GEOCODE_KM = 40.0
    /** Área de busca ao redor de onde o motorista está (em graus, ~0,4° ≈ 45 km). */
    private const val SEARCH_BOX_DEG = 0.4

    fun geocode(ctx: Context, address: String?, near: LatLng?, onDone: (LatLng?) -> Unit) {
        val clean = address?.trim()?.trimEnd(',')
        // Textos curtos demais ("D", "ER", "Prt") são erros de leitura, não endereços.
        if (clean == null || clean.count { it.isLetter() } < 6 || !Geocoder.isPresent()) {
            onDone(null)
            return
        }
        val app = ctx.applicationContext
        Thread {
            val result: LatLng? = try {
                val query = if (clean.contains("Brasil", ignoreCase = true)) clean else "$clean, Brasil"
                val geocoder = Geocoder(app, PT_BR)
                @Suppress("DEPRECATION")
                val list = if (near != null) {
                    geocoder.getFromLocationName(
                        query, 1,
                        near.lat - SEARCH_BOX_DEG, near.lng - SEARCH_BOX_DEG,
                        near.lat + SEARCH_BOX_DEG, near.lng + SEARCH_BOX_DEG
                    )
                } else {
                    geocoder.getFromLocationName(query, 1)
                }
                val found = list?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                when {
                    found == null -> null
                    near != null && meters(near, found) > MAX_GEOCODE_KM * 1000 -> null
                    // Sem saber onde o motorista está, ao menos descarta o "centro do Brasil".
                    near == null && meters(LatLng(-14.235, -51.925), found) < 1000 -> null
                    else -> found
                }
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
