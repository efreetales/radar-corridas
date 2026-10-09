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

    /**
     * Um resultado de busca de endereço.
     * exact = tem rua (não é só o bairro ou o centro da cidade).
     */
    data class Place(val at: LatLng, val label: String, val exact: Boolean)

    private fun inSp(lat: Double, lng: Double) = lat in -24.3..-23.1 && lng in -47.4..-46.0

    /**
     * Busca um endereço OU o nome de um lugar (bar, teatro...) e devolve até 5 opções,
     * as com rua primeiro. Usa o Geocoder do celular e, para nomes de lugares,
     * o OpenStreetMap (Nominatim). Resultado na thread principal.
     */
    fun search(ctx: Context, text: String, onDone: (List<Place>) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val out = mutableListOf<Place>()
            // 1) OpenStreetMap: acha estabelecimentos pelo nome, só dentro da Grande SP
            try {
                val q = java.net.URLEncoder.encode(text, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=5" +
                    "&countrycodes=br&viewbox=-47.2,-23.2,-46.3,-24.1&bounded=1&q=$q"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "RadarCorridas/1.0 (github.com/efreetales/radar-corridas)")
                conn.setRequestProperty("Accept-Language", "pt-BR")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val arr = org.json.JSONArray(body)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val lat = o.optString("lat").toDoubleOrNull() ?: continue
                    val lng = o.optString("lon").toDoubleOrNull() ?: continue
                    if (!inSp(lat, lng)) continue
                    val a = o.optJSONObject("address")
                    val road = a?.optString("road").orEmpty()
                    val num = a?.optString("house_number").orEmpty()
                    val bairro = a?.optString("suburb").orEmpty().ifEmpty { a?.optString("neighbourhood").orEmpty() }
                    val name = o.optString("name")
                    val parts = listOf(
                        name.takeIf { it.isNotBlank() && it != road },
                        listOf(road, num).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                        bairro.ifBlank { null }
                    ).filterNotNull()
                    val label = if (parts.isEmpty()) o.optString("display_name").take(90) else parts.joinToString(" – ")
                    out += Place(LatLng(lat, lng), label, road.isNotBlank())
                }
            } catch (e: Exception) { /* sem internet ou serviço fora: segue com o Geocoder */ }

            // 2) Geocoder do celular (bom para "rua + número")
            if (Geocoder.isPresent()) try {
                val query = when {
                    text.contains("São Paulo", true) -> "$text, Brasil"
                    else -> "$text, São Paulo, Brasil"
                }
                @Suppress("DEPRECATION")
                val list = Geocoder(app, PT_BR).getFromLocationName(query, 5).orEmpty()
                for (a in list) {
                    if (!a.hasLatitude() || !inSp(a.latitude, a.longitude)) continue
                    val label = a.getAddressLine(0)?.removeSuffix(", Brasil")
                        ?: listOfNotNull(a.thoroughfare, a.subThoroughfare, a.subLocality).joinToString(", ")
                    out += Place(LatLng(a.latitude, a.longitude), label, !a.thoroughfare.isNullOrBlank())
                }
            } catch (e: Exception) { }

            // Tira repetidos (mesmo ponto a menos de 40 m) e põe os com rua primeiro
            val uniq = mutableListOf<Place>()
            for (p in out.sortedByDescending { it.exact }) {
                if (uniq.none { meters(it.at, p.at) < 40 }) uniq += p
            }
            main.post { onDone(uniq.take(5)) }
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
