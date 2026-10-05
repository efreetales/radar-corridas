package com.taleco.radarcorridas

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
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
 * Acompanha a corrida depois que uma oferta some da tela:
 *  - se a tela inicial volta logo, a oferta foi recusada ou expirou;
 *  - se não volta, a corrida foi aceita: liga o GPS e mede cada fase.
 *
 * Fases medidas pelo GPS, comparando com os endereços da oferta:
 *  aceite -> chegada ao embarque -> saída com o passageiro -> chegada ao destino -> fim (tela inicial de novo).
 */
object TripTracker {

    private const val DECISION_MS = 4_000L        // tempo sem a tela inicial para considerar "aceita"
    private const val PICKUP_RADIUS_M = 120.0     // chegou ao embarque
    private const val LEAVE_PICKUP_M = 250.0      // saiu do embarque com o passageiro
    private const val DROPOFF_RADIUS_M = 150.0    // chegou ao destino
    private const val MAX_TRIP_MS = 3 * 60 * 60 * 1000L
    private const val MIN_ACCURACY_M = 60f

    private val HOME_MARKERS = listOf("Procurando viagens", "Você está online", "Você está offline")

    fun isHomeScreen(texts: List<String>): Boolean =
        texts.any { t -> HOME_MARKERS.any { m -> t.contains(m, ignoreCase = true) } }

    private enum class Phase { LIVRE, OFERTA, A_CAMINHO, NO_EMBARQUE, EM_VIAGEM }

    private class Point(val time: Long, val lat: Double, val lng: Double, val phase: Phase)

    private class Trip(val id: String, val offer: Offer, val acceptedAt: Long) {
        var origin: LatLng? = null
        var destination: LatLng? = null
        var arrivedPickupAt: Long? = null
        var leftPickupAt: Long? = null
        var arrivedDestAt: Long? = null
        val points = mutableListOf<Point>()
        var kmToPickup = 0.0
        var kmTrip = 0.0
        var kmAfter = 0.0
    }

    private val handler = Handler(Looper.getMainLooper())
    private var phase = Phase.LIVRE
    private var lastOffer: Offer? = null
    private var offerGoneAt = 0L
    private var trip: Trip? = null
    private var appContext: Context? = null

    private val tick = object : Runnable {
        override fun run() {
            checkTimeouts()
            if (phase != Phase.LIVRE) handler.postDelayed(this, 2_000L)
        }
    }

    val isOnTrip: Boolean get() = trip != null

    /** Chamado a cada leitura da tela. */
    fun onScreen(ctx: Context, prefs: Prefs, state: ScreenState, offer: Offer?) {
        appContext = ctx.applicationContext
        if (!prefs.trackTrips) {
            if (trip != null) finish(ctx, "registro desligado")
            phase = Phase.LIVRE
            return
        }
        val now = System.currentTimeMillis()

        when (phase) {
            Phase.LIVRE -> if (state == ScreenState.OFERTA && offer != null) {
                lastOffer = offer
                offerGoneAt = 0L
                setPhase(Phase.OFERTA)
            }

            Phase.OFERTA -> when (state) {
                ScreenState.OFERTA -> {
                    if (offer != null) lastOffer = offer
                    offerGoneAt = 0L
                }
                ScreenState.INICIO -> {
                    // Recusada ou expirada.
                    lastOffer = null
                    setPhase(Phase.LIVRE)
                }
                else -> {
                    if (offerGoneAt == 0L) offerGoneAt = now
                }
            }

            Phase.A_CAMINHO, Phase.NO_EMBARQUE, Phase.EM_VIAGEM -> {
                if (state == ScreenState.INICIO) finish(ctx, null)
            }
        }
        checkTimeouts()
    }

    private fun checkTimeouts() {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        if (phase == Phase.OFERTA && offerGoneAt > 0 && now - offerGoneAt >= DECISION_MS) {
            val offer = lastOffer
            if (offer != null) startTrip(ctx, offer, offerGoneAt) else setPhase(Phase.LIVRE)
        }
        val t = trip
        if (t != null && now - t.acceptedAt > MAX_TRIP_MS) finish(ctx, "tempo máximo")
    }

    private fun setPhase(p: Phase) {
        val wasIdle = phase == Phase.LIVRE
        phase = p
        if (wasIdle && p != Phase.LIVRE) {
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 2_000L)
        }
        if (p == Phase.LIVRE) handler.removeCallbacks(tick)
    }

    private fun startTrip(ctx: Context, offer: Offer, acceptedAt: Long) {
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(acceptedAt))
        val t = Trip(id, offer, acceptedAt)
        trip = t
        lastOffer = null
        setPhase(Phase.A_CAMINHO)
        OfferLog.appendDiag(ctx, "CORRIDA $id: aceita (R$ ${offer.price}) — ligando o GPS")

        Geo.geocode(ctx, offer.origin) { t.origin = it }
        Geo.geocode(ctx, offer.destination) { t.destination = it }

        TrackingService.onPoint = { loc -> onLocation(loc) }
        if (!TrackingService.start(ctx)) {
            OfferLog.appendDiag(ctx, "CORRIDA $id: não foi possível ligar o GPS")
        }
    }

    private fun onLocation(loc: Location) {
        val t = trip ?: return
        if (loc.hasAccuracy() && loc.accuracy > MIN_ACCURACY_M) return
        val here = Geo.of(loc)
        val prev = t.points.lastOrNull()
        val stepKm = if (prev == null) 0.0 else Geo.meters(LatLng(prev.lat, prev.lng), here) / 1000.0

        when (phase) {
            Phase.A_CAMINHO -> {
                t.kmToPickup += stepKm
                val o = t.origin
                if (o != null && Geo.meters(here, o) <= PICKUP_RADIUS_M) {
                    t.arrivedPickupAt = loc.time
                    phase = Phase.NO_EMBARQUE
                }
            }
            Phase.NO_EMBARQUE -> {
                val o = t.origin
                if (o != null && Geo.meters(here, o) > LEAVE_PICKUP_M) {
                    t.leftPickupAt = loc.time
                    phase = Phase.EM_VIAGEM
                    t.kmTrip += stepKm
                } else {
                    t.kmToPickup += stepKm
                }
            }
            Phase.EM_VIAGEM -> {
                if (t.arrivedDestAt == null) {
                    t.kmTrip += stepKm
                    val d = t.destination
                    if (d != null && Geo.meters(here, d) <= DROPOFF_RADIUS_M) t.arrivedDestAt = loc.time
                } else {
                    t.kmAfter += stepKm
                }
            }
            else -> {}
        }
        t.points.add(Point(loc.time, here.lat, here.lng, phase))
    }

    private fun finish(ctx: Context, reason: String?) {
        val t = trip ?: return
        trip = null
        setPhase(Phase.LIVRE)
        TrackingService.onPoint = null
        TrackingService.stop(ctx)
        val endedAt = System.currentTimeMillis()
        val status = when {
            reason != null -> "interrompida ($reason)"
            t.leftPickupAt != null -> "concluída"
            t.arrivedPickupAt != null -> "cancelada no embarque"
            else -> "cancelada antes do embarque"
        }
        TripLog.save(ctx, t.id, t.offer, t.acceptedAt, t.arrivedPickupAt, t.leftPickupAt, t.arrivedDestAt, endedAt,
            t.kmToPickup, t.kmTrip, t.origin, t.destination, status,
            t.points.map { TripLog.RoutePoint(it.time, it.lat, it.lng, it.phase.name) })
        OfferLog.appendDiag(ctx, "CORRIDA ${t.id}: $status, ${t.points.size} pontos de GPS")
    }

    /** Se o app reiniciar no meio da corrida, encerra o que estava aberto. */
    fun shutdown(ctx: Context) {
        if (trip != null) finish(ctx, "Radar desligado")
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
        ctx: Context, id: String, o: Offer, accepted: Long, arrivedPickup: Long?, leftPickup: Long?,
        arrivedDest: Long?, ended: Long, kmToPickup: Double, kmTrip: Double,
        origin: LatLng?, dest: LatLng?, status: String, route: List<RoutePoint>
    ) {
        try {
            val tf = tripsFile(ctx)
            if (!tf.exists()) tf.writeText(TRIPS_HEADER + "\n")
            val tripEnd = arrivedDest ?: ended
            val totalMin = mins(accepted, tripEnd)
            val realPerHour = if (status == "concluída" && totalMin != null && totalMin > 0) o.price / (totalMin / 60.0) else null
            val line = listOf(
                id, o.app, q(o.category), n(o.price), n(o.totalKm), n(o.totalMin),
                ts(accepted), ts(arrivedPickup), ts(leftPickup), ts(arrivedDest), ts(ended),
                n(mins(accepted, arrivedPickup)), n(mins(arrivedPickup, leftPickup)), n(mins(leftPickup, tripEnd)), n(totalMin),
                n(kmToPickup), n(kmTrip), n(realPerHour), q(status),
                q(o.origin), q(o.destination), c(origin?.lat), c(origin?.lng), c(dest?.lat), c(dest?.lng)
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
