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
    var enabled: Boolean
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
 * Avisa quando você está perto de um ponto bom no horário dele.
 * Usa o GPS que já fica ligado com a Uber/99 aberta.
 */
object SpotWatch {

    private const val SHOW_MS = 10_000L
    private const val REARM_MS = 45 * 60_000L
    private val PURPLE = 0xFF6741D9.toInt()

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
        val now = System.currentTimeMillis()
        if (now - lastCheck < 5_000L) return
        lastCheck = now
        if (!Prefs(ctx).spotAlerts) return
        val cal = Calendar.getInstance()
        var best: Spot? = null
        var bestD = Double.MAX_VALUE
        for (s in Spots.all(ctx)) {
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
        alertedAt[s.id] = now
        leftSince.remove(s.id)
        show(ctx, s, bestD, kmh, beep = true)
    }

    private fun show(ctx: Context, s: Spot, d: Double, kmh: Double, beep: Boolean) {
        val (dist, unit) = if (d >= 1000) String.format(PT_BR, "%.1f", d / 1000) to "km" else "${(d / 10).roundToInt() * 10}" to "metros"
        RadarService.instance?.showSpeedBanner(
            SpeedBanner(
                null, kmh.roundToInt(), "Bom agora até ${Spot.hhmm(s.endMin)} · ${s.daysText()}", PURPLE,
                null, (1.0 - d / s.radiusM).coerceIn(0.0, 1.0).toFloat(),
                sign = "★", title = s.name, distText = dist, distUnit = unit
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
    }

    fun demo(ctx: Context) {
        val s = Spot(0, "Teatro Exemplo", 0.0, 0.0, setOf(5, 6, 7), 21 * 60 + 30, 23 * 60, 2000, true)
        show(ctx, s, 1800.0, 28.0, beep = true)
    }
}
