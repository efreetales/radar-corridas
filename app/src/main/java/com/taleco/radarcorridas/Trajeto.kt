package com.taleco.radarcorridas

import android.content.Context
import android.location.Location
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tudo o que acontece enquanto o motorista está online, além das ofertas e corridas:
 *
 *  - trajeto.csv  posição a cada poucos segundos durante o turno (rodando vazio, parado esperando,
 *                 indo buscar, com passageiro), com velocidade, altitude e precisão.
 *                 As posições vêm "de carona" do GPS que a Uber já está usando (sem gasto extra).
 *  - eventos.csv  ficou online/offline, oferta aceita/não aceita, preço dinâmico na tela inicial.
 *  - ganhos.csv   valor final de cada viagem, lido quando o motorista abre o histórico de ganhos da Uber.
 */
object Trajeto {

    // ---------- Turno online ----------

    private val ONLINE_MARKERS = listOf("Procurando viagens", "Você está online", "FICAR OFFLINE")
    private val OFFLINE_MARKERS = listOf("Você está offline", "Ficar online")
    /** Sem ver a Uber por tanto tempo (e sem corrida), considera o turno encerrado. */
    private const val SESSION_TIMEOUT_MS = 90 * 60 * 1000L

    @Volatile
    var online = false
        private set
    private var lastUberSeenAt = 0L
    private var lastSurge: String? = null

    /** Chamado a cada leitura da tela da Uber (textos da janela principal). */
    fun onUberScreen(ctx: Context, prefs: Prefs, texts: List<String>) {
        val now = System.currentTimeMillis()
        lastUberSeenAt = now
        val isOnline = TripTracker.isTripScreen(texts) || texts.any { t -> ONLINE_MARKERS.any { t.startsWith(it) } }
        val isOffline = !isOnline && texts.any { t -> OFFLINE_MARKERS.any { t.startsWith(it) } }
        if (isOnline && !online) setOnline(ctx, prefs, true)
        if (isOffline && online && !TripTracker.isOnTrip) setOnline(ctx, prefs, false)

        if (online || isOffline) checkSurge(ctx, texts)
        checkLastTrip(ctx, texts)
        checkDayTotal(ctx, texts)
        if (texts.contains("Histórico de ganhos")) readEarnings(ctx, texts)
    }

    // ---------- Valor final (tela inicial logo depois da corrida) ----------

    private var lastFinalFor: String? = null
    private var lastDayTotal: String? = null

    /** "Última viagem de Black" + "R$ 35,00": valor que a Uber pagou de fato (com aumento e dinâmico). */
    private fun checkLastTrip(ctx: Context, texts: List<String>) {
        val i = texts.indexOfFirst { it.startsWith("Última viagem de ") }
        if (i < 0) return
        val price = texts.getOrNull(i + 1)?.let { PRICE.find(it) } ?: return
        val tripId = TripTracker.lastFinishedTripId ?: return
        if (tripId == lastFinalFor) return
        lastFinalFor = tripId
        val category = texts[i].removePrefix("Última viagem de ").trim()
        event(ctx, "VALOR_FINAL", money(price.groupValues[1]), null, null, "$tripId | $category", null)
    }

    /** "R$ 371,25" + "HOJE" e "15 viagens concluídas": total do dia, para conferir. */
    private fun checkDayTotal(ctx: Context, texts: List<String>) {
        val i = texts.indexOf("HOJE")
        if (i < 1) return
        val price = PRICE.find(texts[i - 1]) ?: return
        val trips = texts.firstOrNull { it.endsWith("viagens concluídas") || it.endsWith("viagem concluída") }
        val key = price.groupValues[1] + "|" + trips
        if (key == lastDayTotal) return
        lastDayTotal = key
        event(ctx, "TOTAL_DIA", money(price.groupValues[1]), null, null, trips, null)
    }

    /** Chamado de tempos em tempos: encerra turno esquecido. */
    fun tick(ctx: Context, prefs: Prefs) {
        val now = System.currentTimeMillis()
        if (online && !TripTracker.isOnTrip && now - lastUberSeenAt > SESSION_TIMEOUT_MS) {
            setOnline(ctx, prefs, false)
        }
        TrackingService.sync(ctx, prefs)
    }

    private fun setOnline(ctx: Context, prefs: Prefs, value: Boolean) {
        online = value
        val here = Geo.lastKnown(ctx)
        event(ctx, if (value) "ONLINE" else "OFFLINE", null, null, null, null, here)
        TrackingService.sync(ctx, prefs)
    }

    // ---------- Trajeto ----------

    private const val MIN_INTERVAL_MS = 5_000L
    private const val STOPPED_INTERVAL_MS = 60_000L
    private const val MIN_MOVE_M = 15.0
    private const val MAX_ACCURACY_M = 50f
    private const val TRACK_HEADER = "data_hora;lat;lng;velocidade_kmh;altitude_m;precisao_m;rumo;estado;corrida_id;fonte"

    private var lastPoint: Location? = null

    fun trackFile(ctx: Context) = File(ctx.filesDir, "trajeto.csv")
    fun eventsFile(ctx: Context) = File(ctx.filesDir, "eventos.csv")
    fun earningsFile(ctx: Context) = File(ctx.filesDir, "ganhos.csv")

    /** Toda posição que o celular recebe (da Uber, do Waze ou nossa) passa por aqui. */
    fun onLocation(ctx: Context, loc: Location) {
        if (!online && !TripTracker.isOnTrip) return
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        val prev = lastPoint
        if (prev != null) {
            val dt = loc.time - prev.time
            if (dt < MIN_INTERVAL_MS) return
            // Parado: um ponto por minuto basta para saber quanto tempo ficou ali.
            if (prev.distanceTo(loc) < MIN_MOVE_M && dt < STOPPED_INTERVAL_MS) return
        }
        lastPoint = Location(loc)
        val line = listOf(
            ts(loc.time),
            c(loc.latitude), c(loc.longitude),
            if (loc.hasSpeed()) n1(loc.speed * 3.6) else "",
            if (loc.hasAltitude()) n1(loc.altitude) else "",
            if (loc.hasAccuracy()) n1(loc.accuracy.toDouble()) else "",
            if (loc.hasBearing()) n1(loc.bearing.toDouble()) else "",
            TripTracker.currentPhase, TripTracker.currentTripId ?: "", loc.provider ?: ""
        ).joinToString(";")
        append(trackFile(ctx), TRACK_HEADER, line)
    }

    // ---------- Eventos ----------

    private const val EVENTS_HEADER = "data_hora;tipo;valor;km;minutos;detalhe;lat;lng"

    fun event(ctx: Context, type: String, value: Double?, km: Double?, minutes: Double?, detail: String?, at: LatLng?, time: Long = System.currentTimeMillis()) {
        val line = listOf(
            ts(time), type, n2(value), n2(km), n2(minutes), q(detail), c(at?.lat), c(at?.lng)
        ).joinToString(";")
        append(eventsFile(ctx), EVENTS_HEADER, line)
    }

    /** Oferta aceita ou não: liga o resultado à oferta pelo horário e valor. */
    fun offerOutcome(ctx: Context, offer: Offer, firstSeenAt: Long, accepted: Boolean, driverAt: LatLng?) {
        event(
            ctx, if (accepted) "OFERTA_ACEITA" else "OFERTA_NAO_ACEITA",
            offer.price, offer.totalKm, offer.totalMin,
            listOfNotNull(offer.app, offer.category, offer.origin, offer.destination).joinToString(" | "),
            driverAt, firstSeenAt
        )
    }

    private val SURGE_VALUE = Regex("""^\+R\$\s*([\d.]+,\d{2})$""")

    /** "Preço dinâmico no Cambuci!", "+R$ 5,25", "Área de espera do CGH"... grava quando muda. */
    private fun checkSurge(ctx: Context, texts: List<String>) {
        val items = texts.filter {
            it.startsWith("Preço dinâmico") || SURGE_VALUE.matches(it) || it.startsWith("Área de espera")
        }
        val key = items.joinToString(" | ")
        if (key == lastSurge) return
        lastSurge = key
        if (items.isEmpty()) return
        val max = items.mapNotNull { SURGE_VALUE.find(it)?.groupValues?.get(1)?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull() }.maxOrNull()
        event(ctx, "DINAMICO", max, null, null, key, Geo.lastKnown(ctx))
    }

    // ---------- Histórico de ganhos da Uber ----------

    private const val EARNINGS_HEADER =
        "data_rotulo;hora;data_hora_estimada;valor;aumentou;dinamico;categoria;duracao_min;km;origem;destino"
    private val PRICE = Regex("""^R\$\s*([\d.]+,\d{2})$""")
    private val DYNAMIC = Regex("""^R\$\s*([\d.]+,\d{2})\s+Preço dinâmico""")
    private val HOUR = Regex("""^(\d{1,2}):(\d{2})$""")
    private val DAY_LABEL = Regex("""^[a-zçáéíóú]{3}\.,\s*(\d{1,2}) de ([a-zç]{3})""", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val seenEarnings = LinkedHashSet<String>()

    private fun readEarnings(ctx: Context, texts: List<String>) {
        val now = System.currentTimeMillis()
        var dayLabel: String? = null
        var i = 0
        while (i < texts.size) {
            val t = texts[i]
            if (DAY_LABEL.containsMatchIn(t)) dayLabel = t
            val price = PRICE.find(t)
            if (price == null) { i++; continue }
            var j = i + 1
            var raised = false
            if (texts.getOrNull(j) == "Aumentou") { raised = true; j++ }
            val info = texts.getOrNull(j)?.replace("⁨", "")?.replace("⁩", "")
            if (info == null || !info.contains(" · ")) { i++; continue }
            j++
            val hour = texts.getOrNull(j)?.takeIf { HOUR.matches(it) }
            if (hour == null) { i++; continue }
            j++
            var dynamic: Double? = null
            DYNAMIC.find(texts.getOrNull(j) ?: "")?.let { dynamic = money(it.groupValues[1]); j++ }
            val origin = texts.getOrNull(j)?.takeIf { it.contains(" - ") }
            if (origin != null) j++
            val dest = texts.getOrNull(j)?.takeIf { it.contains(" - ") }
            if (dest != null) j++

            val parts = info.split(" · ")
            val category = parts.getOrNull(0)
            val duration = parts.getOrNull(1)?.let { durationMin(it) }
            val km = parts.getOrNull(2)?.substringBefore(" km")?.replace(',', '.')?.toDoubleOrNull()
            val value = money(price.groupValues[1])
            val key = "$hour|$value|$km|$origin"
            if (seenEarnings.add(key)) {
                if (seenEarnings.size > 300) seenEarnings.remove(seenEarnings.first())
                val line = listOf(
                    q(dayLabel), hour, ts(estimateTime(hour, dayLabel, now)), n2(value),
                    if (raised) "sim" else "nao", n2(dynamic), q(category), n2(duration), n2(km), q(origin), q(dest)
                ).joinToString(";")
                // A mesma viagem aparece toda vez que a lista é aberta: a linha sai igual
                // e o banco descarta a repetida.
                append(earningsFile(ctx), EARNINGS_HEADER, line)
            }
            i = j
        }
    }

    private fun money(s: String) = s.replace(".", "").replace(',', '.').toDoubleOrNull()

    private fun durationMin(s: String): Double? {
        var total = 0.0
        var found = false
        Regex("""(\d+)\s*(h|hora|horas|min|minutos?|s|seg|segundos?)\b""").findAll(s).forEach {
            val v = it.groupValues[1].toDouble()
            when (it.groupValues[2].first()) {
                'h' -> total += v * 60
                'm' -> total += v
                's' -> total += v / 60
            }
            found = true
        }
        return if (found) total else null
    }

    /** Data/hora da viagem: o último horário "hh:mm" até agora, respeitando o dia do rótulo. */
    private fun estimateTime(hour: String, dayLabel: String?, now: Long): Long {
        val (h, m) = HOUR.find(hour)!!.destructured
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, h.toInt())
        cal.set(Calendar.MINUTE, m.toInt())
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_MONTH, -1)
        val label = dayLabel?.let { DAY_LABEL.find(it) }
        if (label != null) {
            val day = label.groupValues[1].toInt()
            val month = MONTHS.indexOf(label.groupValues[2].lowercase(Locale.ROOT).take(3))
            if (month >= 0) {
                val start = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                }
                // A Uber agrupa pelo dia em que o turno começou: viagens de madrugada ficam
                // no dia anterior. Se a estimativa caiu antes do dia do rótulo, avança um dia.
                if (cal.before(start)) cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return cal.timeInMillis
    }

    // ---------- Arquivos ----------

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", PT_BR)
    private fun ts(v: Long) = fmt.format(Date(v))
    private fun c(v: Double?) = if (v == null) "" else String.format(Locale.US, "%.6f", v)
    private fun n1(v: Double) = String.format(PT_BR, "%.1f", v)
    private fun n2(v: Double?) = if (v == null) "" else String.format(PT_BR, "%.2f", v)
    private fun q(s: String?) = if (s == null) "" else "\"" + s.replace("\"", "'").replace(";", ",") + "\""

    @Synchronized
    private fun append(f: File, header: String, line: String) {
        try {
            if (!f.exists() || f.length() == 0L) f.writeText(header + "\n")
            f.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }
}
