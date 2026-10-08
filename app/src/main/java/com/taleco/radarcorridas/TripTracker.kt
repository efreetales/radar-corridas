package com.taleco.radarcorridas

import android.content.Context
import android.location.Location
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** O que o Radar está vendo na tela da Uber/99 a cada leitura. */
enum class ScreenState {
    OFERTA,     // uma oferta reconhecida
    INICIO,     // tela inicial: "Procurando viagens", "Você está online"...
    ESCONDIDA,  // a Uber escondeu o texto (oferta ou outra tela protegida)
    OUTRA,      // outra tela da Uber com texto (ex.: navegação até o passageiro)
    FORA        // o app de corrida não está na tela (ex.: motorista no Waze)
}

/**
 * Acompanha cada corrida aceita lendo a própria tela da Uber, que mostra a fase da corrida:
 *  - indo buscar:       "Encontro com Moises" + endereço completo do embarque
 *  - no embarque:       "Aguardando usuário" / "Usuário notificado"
 *  - com o passageiro:  "A caminho da última parada" / "Destino de Vitor" + endereço do destino
 *  - próxima já aceita: "Retirada" (corrida em sequência)
 *  - fim:               "Como foi a viagem?" ou a tela inicial ("Procurando viagens")
 *
 * O GPS fica ligado só durante a corrida, para medir os km de cada fase e marcar
 * onde foi o embarque e o desembarque (usado depois no mapa de calor).
 */
object TripTracker {

    private const val MAX_TRIP_MS = 3 * 60 * 60 * 1000L
    private const val MIN_ACCURACY_M = 60f
    /** Movimentos menores que isso são tremida do GPS, não deslocamento. */
    private const val MIN_STEP_M = 8.0
    /** Oferta lida há mais tempo que isso não é a da corrida que começou. */
    private const val OFFER_MAX_AGE_MS = 120_000L
    /** A tela inicial precisa ficar esse tempo na tela para encerrar a corrida (evita piscadas). */
    private const val HOME_CONFIRM_MS = 3_000L

    private val HOME_MARKERS = listOf("Procurando viagens", "Você está online", "Você está offline")
    private val PICKUP_MARKERS = listOf("Encontro com ", "Aguardando usuário", "Usuário notificado", "Você encontrou ", "Embarque em ")
    private val WAITING_MARKERS = listOf("Aguardando usuário", "Usuário notificado", "O usuário poderá ser cobrado")
    private val RIDING_MARKERS = listOf("A caminho da primeira parada", "A caminho da última parada", "A caminho da próxima parada", "Destino de ")
    private val END_MARKERS = listOf("Como foi a viagem?", "Avaliar usuário")
    /**
     * Endereço completo na tela, em duas linhas:
     *  "Rua Carlos Comenale, 96 - Bela Vista" + "São Paulo - SP, 01332-030"
     *  "Alameda Eduardo Prado, 150" + "- Campos Elíseos, São Paulo - SP"
     */
    private val CITY_LINE = Regex(""".+ - [A-Z]{2}(, \d{5}-\d{3})?$""")

    private fun hasAny(texts: List<String>, markers: List<String>) =
        texts.any { t -> markers.any { m -> t.startsWith(m) } }

    /** A tela mostra uma corrida em andamento. */
    fun isTripScreen(texts: List<String>): Boolean =
        hasAny(texts, PICKUP_MARKERS) || hasAny(texts, RIDING_MARKERS) || texts.any { it == "Contato" }

    fun isHomeScreen(texts: List<String>): Boolean =
        !isTripScreen(texts) && texts.any { t -> HOME_MARKERS.any { m -> t.contains(m, ignoreCase = true) } }

    private fun fullAddress(texts: List<String>, ignore: String? = null): String? {
        for (i in 1 until texts.size) {
            val city = texts[i]
            val street = texts[i - 1]
            if (!CITY_LINE.matches(city) || !(street.contains(" - ") || street.any { it.isDigit() })) continue
            val full = if (city.startsWith("- ")) "$street $city" else "$street, $city"
            if (full != ignore) return full
        }
        return null
    }

    /** Destino da corrida anterior: na corrida em sequência ele ainda aparece por alguns segundos. */
    private var previousDest: String? = null

    private enum class Phase { LIVRE, A_CAMINHO, NO_EMBARQUE, EM_VIAGEM }

    private class Point(val time: Long, val lat: Double, val lng: Double, val phase: Phase)

    private class Trip(val id: String, val offer: Offer?, val acceptedAt: Long) {
        var originText: String? = null
        var destText: String? = null
        var origin: LatLng? = null
        var destination: LatLng? = null
        var arrivedPickupAt: Long? = null
        var leftPickupAt: Long? = null
        var arrivedDestAt: Long? = null
        val points = mutableListOf<Point>()
        var kmToPickup = 0.0
        var kmTrip = 0.0
        var lastGpsAt = 0L
    }

    private var phase = Phase.LIVRE
    private var trip: Trip? = null
    private var lastOffer: Offer? = null
    private var lastOfferAt = 0L
    /** Última oferta lida durante uma corrida: se a Uber passar direto para outro embarque, é dela. */
    private var nextOffer: Offer? = null
    private var homeSince = 0L

    val isOnTrip: Boolean get() = trip != null
    val currentPhase: String get() = phase.name
    val currentTripId: String? get() = trip?.id
    /** Última corrida encerrada: o valor final aparece na tela inicial logo depois. */
    @Volatile var lastFinishedTripId: String? = null
        private set

    /** Quando a oferta atual apareceu pela primeira vez (para ligar o resultado à oferta). */
    private var offerFirstAt = 0L
    private var nextOfferFirstAt = 0L
    private var prefsRef: Prefs? = null

    private fun sameOffer(a: Offer?, b: Offer?) =
        a != null && b != null && a.app == b.app && kotlin.math.abs(a.price - b.price) < 0.01

    /** A oferta saiu de cena sem virar corrida. */
    private fun dropOffer(ctx: Context, o: Offer?, firstAt: Long) {
        if (o != null) Trajeto.offerOutcome(ctx, o, firstAt, false, Geo.lastKnown(ctx))
    }

    /**
     * Chamado a cada leitura da tela.
     * [texts] são os textos da janela da Uber (null quando a leitura foi pela imagem).
     */
    fun onScreen(ctx: Context, prefs: Prefs, state: ScreenState, offer: Offer?, texts: List<String>? = null) {
        if (!prefs.trackTrips) {
            if (trip != null) finish(ctx, "registro desligado", System.currentTimeMillis(), false)
            return
        }
        val now = System.currentTimeMillis()
        prefsRef = prefs

        if (state == ScreenState.OFERTA && offer != null) {
            if (trip != null) {
                if (!sameOffer(nextOffer, offer)) {
                    dropOffer(ctx, nextOffer, nextOfferFirstAt)
                    nextOfferFirstAt = now
                }
                nextOffer = offer
            } else {
                if (!sameOffer(lastOffer, offer)) {
                    dropOffer(ctx, lastOffer, offerFirstAt)
                    offerFirstAt = now
                }
                lastOffer = offer
                lastOfferAt = now
            }
            return
        }
        val t = texts ?: return
        val cur = trip
        val onTrip = isTripScreen(t)

        if (cur == null) {
            if (onTrip) {
                val o = if (now - lastOfferAt <= OFFER_MAX_AGE_MS) lastOffer else null
                if (o != null) Trajeto.offerOutcome(ctx, o, offerFirstAt, true, Geo.lastKnown(ctx))
                lastOffer = null
                val started = startTrip(ctx, o, now, gpsAlreadyOn = false)
                update(ctx, started, t, now)
            } else if (lastOffer != null && now - lastOfferAt > 30_000L) {
                // A oferta sumiu e nenhuma corrida começou: recusada ou expirou.
                dropOffer(ctx, lastOffer, offerFirstAt)
                lastOffer = null
            }
            return
        }

        if (now - cur.acceptedAt > MAX_TRIP_MS) {
            finish(ctx, "tempo máximo", now, false)
            return
        }
        if (hasAny(t, END_MARKERS)) {
            finish(ctx, null, now, false)
            return
        }
        if (!onTrip) {
            if (isHomeScreen(t)) {
                if (homeSince == 0L) homeSince = now
                if (now - homeSince >= HOME_CONFIRM_MS) finish(ctx, null, homeSince, false)
            }
            return
        }
        homeSince = 0L

        // Corrida em sequência: estava com passageiro e a Uber já mostra o próximo embarque.
        if (phase == Phase.EM_VIAGEM && hasAny(t, PICKUP_MARKERS)) {
            val next = nextOffer
            nextOffer = null
            if (next != null) Trajeto.offerOutcome(ctx, next, nextOfferFirstAt, true, Geo.lastKnown(ctx))
            finish(ctx, null, now, keepGps = true)
            OfferLog.appendDiag(ctx, "CORRIDA EM SEQUÊNCIA" + (next?.let { " (R$ ${it.price})" } ?: " (oferta não lida)"))
            val started = startTrip(ctx, next, now, gpsAlreadyOn = true)
            update(ctx, started, t, now)
            return
        }
        update(ctx, cur, t, now)
    }

    /** Avança a fase da corrida conforme o que a Uber mostra. */
    private fun update(ctx: Context, t: Trip, texts: List<String>, now: Long) {
        val addr = fullAddress(texts, previousDest)
        when {
            hasAny(texts, RIDING_MARKERS) -> {
                if (t.leftPickupAt == null) {
                    if (t.arrivedPickupAt == null) {
                        t.arrivedPickupAt = now
                        t.origin = lastPosition(ctx, t)
                    }
                    t.leftPickupAt = now
                    phase = Phase.EM_VIAGEM
                }
                if (addr != null) t.destText = addr
            }
            hasAny(texts, WAITING_MARKERS) -> {
                if (t.arrivedPickupAt == null) {
                    t.arrivedPickupAt = now
                    t.origin = lastPosition(ctx, t)
                    phase = Phase.NO_EMBARQUE
                }
                if (addr != null && t.originText == null) t.originText = addr
            }
            else -> if (phase == Phase.A_CAMINHO && addr != null) t.originText = addr
        }
    }

    private fun lastPosition(ctx: Context, t: Trip): LatLng? =
        t.points.lastOrNull()?.let { LatLng(it.lat, it.lng) } ?: Geo.lastKnown(ctx)

    private fun startTrip(ctx: Context, offer: Offer?, acceptedAt: Long, gpsAlreadyOn: Boolean): Trip {
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(acceptedAt))
        val t = Trip(id, offer, acceptedAt)
        trip = t
        phase = Phase.A_CAMINHO
        homeSince = 0L
        nextOffer = null
        OfferLog.appendDiag(ctx, "CORRIDA $id: aceita" + (offer?.let { " (R$ ${it.price})" } ?: " (oferta não lida)"))
        TrackingService.onPoint = { loc -> onLocation(loc) }
        if (!gpsAlreadyOn) prefsRef?.let { TrackingService.sync(ctx, it) }
        return t
    }

    private fun onLocation(loc: Location) {
        val t = trip ?: return
        if (loc.hasAccuracy() && loc.accuracy > MIN_ACCURACY_M) return
        // A posição pela rede "pula" e infla os km: só é usada quando o GPS está sem sinal.
        val now = System.currentTimeMillis()
        if (loc.provider == android.location.LocationManager.GPS_PROVIDER) {
            t.lastGpsAt = now
        } else if (now - t.lastGpsAt < 15_000L) {
            return
        }
        val here = Geo.of(loc)
        val prev = t.points.lastOrNull()
        val stepM = if (prev == null) 0.0 else Geo.meters(LatLng(prev.lat, prev.lng), here)
        if (prev != null && stepM < MIN_STEP_M) return
        val stepKm = stepM / 1000.0
        when (phase) {
            Phase.A_CAMINHO, Phase.NO_EMBARQUE -> t.kmToPickup += stepKm
            Phase.EM_VIAGEM -> t.kmTrip += stepKm
            else -> {}
        }
        t.points.add(Point(loc.time, here.lat, here.lng, phase))
    }

    private fun finish(ctx: Context, reason: String?, endedAt: Long, keepGps: Boolean) {
        val t = trip ?: return
        trip = null
        phase = Phase.LIVRE
        homeSince = 0L
        lastFinishedTripId = t.id
        if (!keepGps) {
            // Oferta recebida durante a corrida que não virou a próxima corrida.
            dropOffer(ctx, nextOffer, nextOfferFirstAt)
            nextOffer = null
            TrackingService.onPoint = null
            prefsRef?.let { TrackingService.sync(ctx, it) } ?: TrackingService.stop(ctx)
        }
        val status = when {
            reason != null -> "interrompida ($reason)"
            t.leftPickupAt != null -> "concluída"
            t.arrivedPickupAt != null -> "cancelada no embarque"
            else -> "cancelada antes do embarque"
        }
        previousDest = t.destText
        if (t.leftPickupAt != null) {
            t.arrivedDestAt = endedAt
            t.destination = lastPosition(ctx, t)
        }
        TripLog.save(ctx, t.id, t.offer, t.acceptedAt, t.arrivedPickupAt, t.leftPickupAt, t.arrivedDestAt, endedAt,
            t.kmToPickup, t.kmTrip, t.originText, t.destText, t.origin, t.destination, status,
            t.points.map { TripLog.RoutePoint(it.time, it.lat, it.lng, it.phase.name) })
        OfferLog.appendDiag(ctx, "CORRIDA ${t.id}: $status, ${t.points.size} pontos de GPS")
        CloudSync.syncNow(ctx)
    }

    /** Se o app reiniciar no meio da corrida, encerra o que estava aberto. */
    fun shutdown(ctx: Context) {
        if (trip != null) finish(ctx, "Radar desligado", System.currentTimeMillis(), false)
    }
}

/** Grava corridas.csv (uma linha por corrida) e rotas.csv (os pontos do GPS). */
object TripLog {

    class RoutePoint(val time: Long, val lat: Double, val lng: Double, val phase: String)

    private const val TRIPS_HEADER =
        "id;app;categoria;valor;km_oferta;min_oferta;aceite;chegada_embarque;saida_embarque;chegada_destino;fim;" +
            "min_ate_embarque;min_espera_passageiro;min_viagem;min_total;km_ate_embarque;km_viagem;rs_hora_real;status;" +
            "origem;destino;origem_lat;origem_lng;destino_lat;destino_lng"
    private const val ROUTE_HEADER = "id;data_hora;lat;lng;fase"

    fun tripsFile(ctx: Context) = File(ctx.filesDir, "corridas.csv")
    fun routesFile(ctx: Context) = File(ctx.filesDir, "rotas.csv")

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", PT_BR)
    private fun ts(v: Long?) = if (v == null) "" else fmt.format(Date(v))
    private fun n(v: Double?) = if (v == null) "" else String.format(PT_BR, "%.2f", v)
    private fun c(v: Double?) = if (v == null) "" else String.format(Locale.US, "%.6f", v)
    private fun q(s: String?) = if (s == null) "" else "\"" + s.replace("\"", "'") + "\""
    private fun mins(a: Long?, b: Long?): Double? = if (a == null || b == null || b < a) null else (b - a) / 60_000.0

    fun save(
        ctx: Context, id: String, o: Offer?, accepted: Long, arrivedPickup: Long?, leftPickup: Long?,
        arrivedDest: Long?, ended: Long, kmToPickup: Double, kmTrip: Double,
        originText: String?, destText: String?,
        origin: LatLng?, dest: LatLng?, status: String, route: List<RoutePoint>
    ) {
        try {
            val tf = tripsFile(ctx)
            if (!tf.exists()) tf.writeText(TRIPS_HEADER + "\n")
            val tripEnd = arrivedDest ?: ended
            val totalMin = mins(accepted, tripEnd)
            val realPerHour = if (o != null && status.startsWith("concluída") && totalMin != null && totalMin > 0) o.price / (totalMin / 60.0) else null
            val line = listOf(
                id, o?.app ?: "UBER", q(o?.category), n(o?.price), n(o?.totalKm), n(o?.totalMin),
                ts(accepted), ts(arrivedPickup), ts(leftPickup), ts(arrivedDest), ts(ended),
                n(mins(accepted, arrivedPickup)), n(mins(arrivedPickup, leftPickup)), n(mins(leftPickup, tripEnd)), n(totalMin),
                n(kmToPickup), n(kmTrip), n(realPerHour), q(status),
                q(originText ?: o?.origin), q(destText ?: o?.destination), c(origin?.lat), c(origin?.lng), c(dest?.lat), c(dest?.lng)
            ).joinToString(";")
            tf.appendText(line + "\n")

            val rf = routesFile(ctx)
            if (!rf.exists()) rf.writeText(ROUTE_HEADER + "\n")
            val sb = StringBuilder()
            for (p in route) {
                sb.append(id).append(';').append(ts(p.time)).append(';')
                    .append(c(p.lat)).append(';').append(c(p.lng)).append(';').append(p.phase).append('\n')
            }
            rf.appendText(sb.toString())
        } catch (_: Exception) {
        }
    }

    fun count(ctx: Context): Int {
        val f = tripsFile(ctx)
        if (!f.exists()) return 0
        return try { (f.readLines().count { it.isNotBlank() } - 1).coerceAtLeast(0) } catch (_: Exception) { 0 }
    }
}
