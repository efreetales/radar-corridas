package com.taleco.radarcorridas

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Um ponto bom marcado pelo motorista.
 * days: dias da semana em que vale (Calendar.SUNDAY = 1 ... SATURDAY = 7)
 * startMin / endMin: janela em minutos do dia (ex.: 21:30 = 1290). Pode atravessar a meia-noite.
 * radiusM: a partir de quantos metros avisa.
 */
class Spot(
    val id: Long,
    var name: String,
    var lat: Double,
    var lng: Double,
    var days: Set<Int>,
    var startMin: Int,
    var endMin: Int,
    var radiusM: Int,
    var enabled: Boolean,
    val event: CityEvent? = null   // preenchido quando o "ponto" é um evento do dia
) {
    /** Está no horário agora? */
    fun activeAt(cal: Calendar): Boolean {
        if (!enabled) return false
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = cal.get(Calendar.DAY_OF_WEEK)
        return if (startMin <= endMin) {
            today in days && now in startMin until endMin
        } else {
            // Ex.: 22:00 até 02:00. Depois da meia-noite vale o dia em que começou.
            val yesterday = if (today == Calendar.SUNDAY) Calendar.SATURDAY else today - 1
            (today in days && now >= startMin) || (yesterday in days && now < endMin)
        }
    }

    fun windowText(): String = "${hhmm(startMin)}–${hhmm(endMin)}"

    fun daysText(): String {
        if (days.size == 7) return "todos os dias"
        if (days == setOf(2, 3, 4, 5, 6)) return "seg a sex"
        if (days == setOf(1, 7)) return "fim de semana"
        return DAY_ORDER.filter { it in days }.joinToString(", ") { DAY_SHORT[it] ?: "" }
    }

    companion object {
        val DAY_ORDER = listOf(2, 3, 4, 5, 6, 7, 1)
        val DAY_SHORT = mapOf(1 to "dom", 2 to "seg", 3 to "ter", 4 to "qua", 5 to "qui", 6 to "sex", 7 to "sáb")
        val DAY_LETTER = mapOf(1 to "D", 2 to "S", 3 to "T", 4 to "Q", 5 to "Q", 6 to "S", 7 to "S")
        fun hhmm(m: Int) = String.format(PT_BR, "%02d:%02d", (m / 60) % 24, m % 60)
    }
}

/** Pontos bons: salvos no celular em pontos.json. */
object Spots {

    private const val FILE = "pontos.json"
    private val list = mutableListOf<Spot>()
    private var loaded = false

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun all(ctx: Context): List<Spot> {
        load(ctx)
        return list.toList()
    }

    @Synchronized
    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = o.getJSONArray("dias")
                list.add(
                    Spot(
                        o.getLong("id"), o.getString("nome"), o.getDouble("lat"), o.getDouble("lng"),
                        (0 until d.length()).map { d.getInt(it) }.toSet(),
                        o.getInt("inicio"), o.getInt("fim"), o.optInt("raio", 2000), o.optBoolean("ativo", true)
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun save(ctx: Context) {
        load(ctx)
        try {
            val arr = JSONArray()
            for (s in list) {
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("nome", s.name)
                    put("lat", s.lat)
                    put("lng", s.lng)
                    put("dias", JSONArray(s.days.sorted()))
                    put("inicio", s.startMin)
                    put("fim", s.endMin)
                    put("raio", s.radiusM)
                    put("ativo", s.enabled)
                })
            }
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(arr.toString(2))
            tmp.renameTo(file(ctx))
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun add(ctx: Context, s: Spot) {
        load(ctx)
        list.add(s)
        save(ctx)
    }

    @Synchronized
    fun remove(ctx: Context, s: Spot) {
        load(ctx)
        list.removeAll { it.id == s.id }
        save(ctx)
    }
}

/**
 * Depois de tocar em "Waze" no aviso: uma faixa no topo lembra PARA ONDE você está indo
 * ("Indo para: Urias – Carranca · Audio · saída ~01:30"). Some ao chegar (150 m), ao aceitar
 * uma corrida, com um toque, ou depois de 90 min.
 */
object GoingTo {
    private const val ARRIVE_M = 150f
    private const val MAX_MS = 90 * 60_000L
    @Volatile
    var spot: Spot? = null
        private set
    private var since = 0L

    fun start(s: Spot) {
        spot = s
        since = System.currentTimeMillis()
        val e = s.event
        val txt = if (e != null)
            "Indo para: ${s.name} · ${e.place.ifBlank { e.address }} · saída ~${Spot.hhmm(e.endMin)}   ✕"
        else "Indo para: ★ ${s.name} · bom até ${Spot.hhmm(s.endMin)}   ✕"
        RadarService.instance?.showGoing(txt, if (e != null) 0xFF9C1B4A.toInt() else 0xFF5232B8.toInt())
    }

    fun stop() {
        spot = null
        RadarService.instance?.hideGoing()
    }

    fun onLocation(loc: Location) {
        val s = spot ?: return
        val d = loc.distanceTo(Location("g").apply { latitude = s.lat; longitude = s.lng })
        if (d < ARRIVE_M || TripTracker.busy || System.currentTimeMillis() - since > MAX_MS) stop()
    }
}

/** Destino da oferta perto de um ponto bom no horário em que você chegaria lá. */
object SpotMatch {
    fun near(ctx: Context, dest: LatLng, arriveInMin: Double): Pair<Spot, Double>? {
        val maxM = Prefs(ctx).spotStarKm * 1000.0
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, arriveInMin.toInt()) }
        // 1º os seus pontos bons; evento só se nenhum ponto bom bater (e se estiver ligado)
        fun closest(list: List<Spot>): Pair<Spot, Double>? {
            var best: Pair<Spot, Double>? = null
            for (s in list) {
                if (!s.activeAt(cal)) continue
                val d = Geo.meters(dest, LatLng(s.lat, s.lng))
                if (d <= maxM && (best == null || d < best.second)) best = s to d
            }
            return best
        }
        closest(Spots.all(ctx))?.let { return it }
        return if (Prefs(ctx).eventsInOffer) closest(Events.spots(ctx)) else null
    }

    fun note(s: Spot, d: Double): String =
        if (s.event != null)
            String.format(PT_BR, "★ Destino a %.1f km de %s · saída ~%s", d / 1000.0, s.name, Spot.hhmm(s.event.endMin))
        else
            String.format(PT_BR, "★ Destino a %.1f km de %s · bom até %s", d / 1000.0, s.name, Spot.hhmm(s.endMin))
}

/**
 * Avisa quando você está perto de um ponto bom no horário dele.
 * Usa o GPS que já fica ligado com a Uber/99 aberta.
 */
object SpotWatch {

    private const val SHOW_MS = 10_000L
    private const val REARM_MS = 45 * 60_000L
    private val PURPLE = 0xFF6741D9.toInt()
    private val PINK = 0xFFC2255C.toInt()

    private val handler = Handler(Looper.getMainLooper())
    private var lastCheck = 0L

    /** Quando cada ponto avisou pela última vez; e se você já saiu do raio depois disso. */
    private val alertedAt = HashMap<Long, Long>()
    private val leftSince = HashSet<Long>()

    @Volatile
    var showing = false
        private set

    private val hide = Runnable {
        if (showing) {
            showing = false
            RadarService.instance?.hideSpeedBanner()
        }
    }

    fun onLocation(ctx: Context, loc: Location, kmh: Double, bannerBusy: Boolean) {
        GoingTo.onLocation(loc)
        // Com oferta na tela ou corrida aceita, nada de ponto/evento (e o que estiver aberto sai)
        val offerUp = RadarService.instance?.isOfferCardShowing == true
        if (TripTracker.busy || offerUp) {
            if (showing) hide.run()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCheck < 5_000L) return
        lastCheck = now
        val p = Prefs(ctx)
        if (!p.spotAlerts && !p.eventAlerts) return
        val cal = Calendar.getInstance()
        var best: Spot? = null
        var bestD = Double.MAX_VALUE
        for (s in (if (p.spotAlerts) Spots.all(ctx) else emptyList()) + Events.spots(ctx)) {
            val d = loc.distanceTo(Location("p").apply { latitude = s.lat; longitude = s.lng }).toDouble()
            if (d > s.radiusM * 1.3) leftSince.add(s.id)
            if (!s.activeAt(cal) || d > s.radiusM) continue
            val last = alertedAt[s.id]
            // Avisa de novo só se você saiu do raio e passou um tempo
            if (last != null && (now - last < REARM_MS || s.id !in leftSince)) continue
            if (d < bestD) {
                best = s
                bestD = d
            }
        }
        val s = best ?: return
        if (bannerBusy) return
        // Antes de avisar: a tela da Uber tem que confirmar, há poucos segundos, que você está livre.
        // Se não confirmou, pede uma leitura agora e decide na próxima posição (uns 5 s depois).
        if (!RideScreen.confirmedFree()) {
            RadarService.instance?.requestScan()
            return
        }
        alertedAt[s.id] = now
        leftSince.remove(s.id)
        show(ctx, s, bestD, kmh, beep = true)
    }

    /** Ponto mostrado no aviso agora (o toque no aviso abre o Waze até ele). */
    @Volatile
    var current: Spot? = null
        private set

    private fun show(ctx: Context, s: Spot, d: Double, kmh: Double, beep: Boolean) {
        current = s
        val (dist, unit) = if (d >= 1000) String.format(PT_BR, "%.1f", d / 1000) to "km" else "${(d / 10).roundToInt() * 10}" to "metros"
        RadarService.instance?.showSpeedBanner(
            SpeedBanner(
                null, kmh.roundToInt(),
                (if (s.event != null) "${s.event.place.ifBlank { "" }}${if (s.event.place.isNotBlank()) " · " else ""}saída ~${Spot.hhmm(s.event.endMin)}"
                 else "Bom até ${Spot.hhmm(s.endMin)}") +
                    " · a $dist ${if (unit == "metros") "m" else unit}",
                if (s.event != null) PINK else PURPLE,
                null, (1.0 - d / s.radiusM).coerceIn(0.0, 1.0).toFloat(),
                sign = "★", title = s.name, action = "Waze"
            )
        )
        showing = true
        if (beep && Prefs(ctx).speedSound) Beeper.play(ctx, Beeper.Kind.PONTO)
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, SHOW_MS)
    }

    fun onBannerClosed() {
        handler.removeCallbacks(hide)
        showing = false
        current = null
    }

    fun demo(ctx: Context) {
        val s = Spot(0, "Teatro Exemplo", 0.0, 0.0, setOf(5, 6, 7), 21 * 60 + 30, 23 * 60, 2000, true)
        show(ctx, s, 1800.0, 28.0, beep = true)
    }
}
