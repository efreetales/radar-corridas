package com.taleco.radarcorridas

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Um evento do dia (teatro, show, jogo...). O que importa para o motorista é a SAÍDA do público.
 * endMin = horário estimado de término (minutos do dia).
 */
class CityEvent(
    val name: String,
    val place: String,
    val address: String,
    val type: String,
    val date: String,          // yyyy-MM-dd
    val startMin: Int?,
    val endMin: Int,
    val audience: Int?,
    var lat: Double?,
    var lng: Double?
) {
    val icon: String get() = when (type.lowercase(Locale.ROOT)) {
        "teatro", "musical", "danca", "dança", "ópera", "opera" -> "🎭"
        "show", "musica", "música", "festa", "balada" -> "🎵"
        "futebol", "jogo", "esporte" -> "⚽"
        "igreja", "culto", "missa" -> "⛪"
        "formatura", "faculdade", "universidade", "palestra", "feira", "congresso" -> "🎓"
        else -> "🎟"
    }

    /** Vira um "ponto bom" temporário: vale de 20 min antes até 40 min depois do término. */
    fun asSpot(radiusM: Int): Spot? {
        val la = lat ?: return null
        val lo = lng ?: return null
        val day = try {
            Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!! }.get(Calendar.DAY_OF_WEEK)
        } catch (_: Exception) { return null }
        // Término depois da meia-noite (ex.: começa 23h, termina 0h10) conta no dia seguinte
        val endAbs = endMin + if ((startMin != null && endMin < startMin) || endMin < 6 * 60) 1440 else 0
        val startAbs = endAbs - 20
        val s = startAbs % 1440
        val e = (endAbs + 40) % 1440
        val windowDay = ((day - 1 + startAbs / 1440) % 7) + 1
        return Spot(-(name.hashCode().toLong() and 0xffffffL) - 1, "$icon $name", la, lo, setOf(windowDay), s, e, radiusM, true, event = this)
    }
}

/** Agenda de eventos do dia, publicada todo dia pela tarefa agendada no GitHub. */
object Events {

    private const val URL_JSON = "https://raw.githubusercontent.com/efreetales/radar-corridas/eventos/eventos.json"
    private const val FILE = "eventos_hoje.json"
    private const val REFRESH_MS = 2 * 60 * 60 * 1000L

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var list: List<CityEvent> = emptyList()
    @Volatile private var loadedAt = 0L
    @Volatile var downloading = false
        private set

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    /** Eventos de hoje e de ontem (para os que terminam depois da meia-noite). */
    fun today(ctx: Context): List<CityEvent> {
        if (list.isEmpty()) load(ctx)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(Date())
        val yesterday = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))
        return list.filter { it.date == today || (it.date == yesterday && it.endMin < 6 * 60) }
    }

    fun spots(ctx: Context): List<Spot> {
        val p = Prefs(ctx)
        if (!p.eventAlerts) return emptyList()
        return today(ctx).mapNotNull { it.asSpot(p.eventRadiusM) }
    }

    @Synchronized
    fun load(ctx: Context) {
        val f = file(ctx)
        if (!f.exists()) return
        list = try { parse(f.readText()) } catch (_: Exception) { emptyList() }
    }

    private fun parse(text: String): List<CityEvent> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("eventos") ?: JSONArray()
        val defaultDate = root.optString("data")
        val out = ArrayList<CityEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val end = hm(o.optString("fim_estimado")) ?: continue
            out.add(
                CityEvent(
                    o.optString("nome"), o.optString("local"), o.optString("endereco"), o.optString("tipo"),
                    o.optString("data").ifBlank { defaultDate }, hm(o.optString("inicio")), end,
                    if (o.has("publico_estimado") && !o.isNull("publico_estimado")) o.optInt("publico_estimado") else null,
                    if (o.has("lat") && !o.isNull("lat")) o.optDouble("lat") else null,
                    if (o.has("lng") && !o.isNull("lng")) o.optDouble("lng") else null
                )
            )
        }
        return out
    }

    private fun hm(s: String?): Int? {
        val m = Regex("(\\d{1,2})[:h](\\d{2})").find(s ?: return null) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    /** Baixa a agenda (no máximo a cada 2 h, ou quando forçado) e localiza os endereços. */
    fun refresh(ctx: Context, force: Boolean = false, onDone: ((Int?) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (downloading) return
        if (!force && System.currentTimeMillis() - loadedAt < REFRESH_MS && list.isNotEmpty()) return
        downloading = true
        Thread {
            var n: Int? = null
            try {
                val conn = URL(URL_JSON + "?t=" + System.currentTimeMillis() / 600_000).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "RadarCorridas/1.0 (Android)")
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val events = parse(text)
                    // Localiza os que vieram sem coordenadas
                    for (e in events) {
                        if (e.lat != null && e.lng != null) continue
                        try {
                            val q = listOf(e.address, e.place).firstOrNull { it.isNotBlank() } ?: continue
                            @Suppress("DEPRECATION")
                            val r = Geocoder(app, PT_BR).getFromLocationName(if (q.contains("São Paulo")) q else "$q, São Paulo", 1)?.firstOrNull()
                            if (r != null && r.latitude in -24.3..-23.1 && r.longitude in -47.4..-46.0) {
                                e.lat = r.latitude
                                e.lng = r.longitude
                            }
                        } catch (_: Exception) {
                        }
                    }
                    file(app).writeText(toJson(text, events))
                    list = events
                    loadedAt = System.currentTimeMillis()
                    n = events.size
                }
                conn.disconnect()
            } catch (e: Exception) {
                OfferLog.appendDiag(app, "EVENTOS: falha ao baixar (${e.message})")
            }
            downloading = false
            main.post { onDone?.invoke(n) }
        }.start()
    }

    /** Guarda a agenda já com as coordenadas encontradas (o mapa lê este arquivo). */
    private fun toJson(original: String, events: List<CityEvent>): String {
        val root = try { JSONObject(original) } catch (_: Exception) { JSONObject() }
        val arr = JSONArray()
        for (e in events) arr.put(JSONObject().apply {
            put("nome", e.name); put("local", e.place); put("endereco", e.address); put("tipo", e.type); put("data", e.date)
            e.startMin?.let { put("inicio", Spot.hhmm(it)) }
            put("fim_estimado", Spot.hhmm(e.endMin))
            e.audience?.let { put("publico_estimado", it) }
            e.lat?.let { put("lat", it) }; e.lng?.let { put("lng", it) }
            put("icone", e.icon)
        })
        root.put("eventos", arr)
        return root.toString()
    }
}
