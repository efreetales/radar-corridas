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
/**
 * Corrida em andamento lida direto da tela da Uber:
 * "Embarque em …" (indo buscar / esperando), "Insira o código …", "Destino de <passageiro>" (em viagem).
 * Vale até a Uber voltar para "Procurando viagens" sem nada disso, ou 15 min sem ver a tela da Uber.
 */
object RideScreen {
    private val PREFIXES = listOf("Destino de ", "Embarque em ", "Insira o código")
    private val HOME = listOf("Procurando viagens", "Você está online", "Você está offline")
    private const val GRACE_MS = 15 * 60_000L

    @Volatile
    private var lastSeen = 0L

    /** Última vez que a tela da Uber mostrou "Procurando viagens" SEM corrida. */
    @Volatile
    private var lastFree = 0L

    fun isRide(texts: List<String>): Boolean =
        texts.any { t -> val x = t.trim(); PREFIXES.any { x.startsWith(it, ignoreCase = true) } }

    fun update(texts: List<String>) {
        if (isRide(texts)) { lastSeen = System.currentTimeMillis(); lastFree = 0L }
        else if (texts.any { t -> HOME.any { t.contains(it, ignoreCase = true) } }) { lastSeen = 0L; lastFree = System.currentTimeMillis() }
    }

    val active: Boolean get() = lastSeen > 0 && System.currentTimeMillis() - lastSeen < GRACE_MS

    /** A tela da Uber confirmou "livre" há pouco (antes de mostrar aviso de evento/ponto). */
    fun confirmedFree(maxAgeMs: Long = 20_000L): Boolean =
        lastFree > 0 && System.currentTimeMillis() - lastFree < maxAgeMs
}

object TripTracker {

    private const val DECISION_MS = 4_000L        // tempo sem a tela inicial para considerar "aceita"
    private const val PICKUP_RADIUS_M = 120.0     // chegou ao embarque
    private const val LEAVE_PICKUP_M = 250.0      // saiu do embarque com o passageiro
    private const val DROPOFF_RADIUS_M = 150.0    // chegou ao destino
    private const val MAX_TRIP_MS = 3 * 60 * 60 * 1000L
    private const val MIN_ACCURACY_M = 60f

    private val HOME_MARKERS = listOf("Procurando viagens", "Você está online", "Você está offline")

    /** Tela inicial de verdade: "procurando viagens" e nenhum sinal de corrida em andamento. */
    fun isHomeScreen(texts: List<String>): Boolean =
        !RideScreen.isRide(texts) && texts.any { t -> HOME_MARKERS.any { m -> t.contains(m, ignoreCase = true) } }

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
        var passenger: String? = null   // nome lido na tela da Uber ("Encontro com X", "Destino de X")
        var ratedDone = false           // apareceu "Como foi a viagem?": corrida concluída de verdade
    }

    // Corrida emendada: oferta aceita enquanto outra corrida está em andamento
    private var nextOffer: Offer? = null
    private var nextGoneAt = 0L
    private var queued: Offer? = null
    private var queuedAt = 0L

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

    /** Oferta na tela, corrida aceita ou em andamento: não é hora de sugerir ponto/evento.
     *  Além do que o app acompanhou, vale o que a tela da Uber mostra (sobrevive a reiniciar/atualizar o app). */
    val busy: Boolean get() = phase != Phase.LIVRE || trip != null || RideScreen.active

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
                when {
                    state == ScreenState.OFERTA && offer != null -> { nextOffer = offer; nextGoneAt = 0L }
                    state == ScreenState.INICIO -> { nextOffer = null; queued = null; finish(ctx, null) }
                    nextOffer != null -> {
                        if (nextGoneAt == 0L) nextGoneAt = now
                        else if (now - nextGoneAt >= DECISION_MS) {
                            // sumiu sem voltar para a tela inicial: aceita, vira a próxima corrida
                            queued = nextOffer; queuedAt = nextGoneAt; nextOffer = null
                            OfferLog.appendDiag(ctx, "CORRIDA: emendada aceita (R$ ${queued?.price}), começa quando a atual terminar")
                        }
                    }
                }
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
        if (!TrackingService.start(ctx, TrackingService.CORRIDA)) {
            OfferLog.appendDiag(ctx, "CORRIDA $id: não foi possível ligar o GPS")
        }
    }

    private fun onLocation(loc: Location) {
        val t = trip ?: return
        if (loc.hasAccuracy() && loc.accuracy > MIN_ACCURACY_M) return
        val here = Geo.of(loc)
        val prev = t.points.lastOrNull()
        // Com o alerta de radar o GPS manda um ponto por segundo; para o percurso, 1 a cada 3 s basta.
        if (prev != null && loc.time - prev.time < 2_500L) return
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
        TrackingService.stop(ctx, TrackingService.CORRIDA)
        val endedAt = System.currentTimeMillis()
        // Quando o endereço da oferta não pôde ser localizado, as fases não são detectadas.
        // Aí usamos o quanto o carro andou e o tempo: se bate com a corrida, ela foi feita.
        val moved = t.kmToPickup + t.kmTrip + t.kmAfter
        val minutes = (endedAt - t.acceptedAt) / 60_000.0
        val looksDone = moved >= t.offer.totalKm * 0.6 && minutes >= t.offer.totalMin * 0.5
        val status = when {
            t.ratedDone -> "concluída"
            reason != null -> "interrompida ($reason)"
            t.leftPickupAt != null -> "concluída"
            looksDone -> "concluída (estimada)"
            t.arrivedPickupAt != null -> "cancelada no embarque"
            else -> "cancelada antes do embarque"
        }
        TripLog.save(ctx, t.id, t.offer, t.acceptedAt, t.arrivedPickupAt, t.leftPickupAt, t.arrivedDestAt, endedAt,
            t.kmToPickup, t.kmTrip, t.origin, t.destination, status,
            t.points.map { TripLog.RoutePoint(it.time, it.lat, it.lng, it.phase.name) })
        OfferLog.appendDiag(ctx, "CORRIDA ${t.id}: $status${t.passenger?.let { " ($it)" } ?: ""}, ${t.points.size} pontos de GPS")
    }

    /**
     * Fases lidas direto da tela da Uber (mais confiável que o GPS):
     *  "Encontro com X" = indo buscar X · botão "Iniciar ..." = chegou no embarque
     *  "Destino de X" = X embarcou · "Como foi a viagem?" + X = corrida de X concluída.
     * Também cuida da corrida emendada: termina a atual e já começa a próxima.
     */
    fun onUberTexts(ctx: Context, prefs: Prefs, texts: List<String>) {
        if (!prefs.trackTrips) return
        appContext = ctx.applicationContext
        val now = System.currentTimeMillis()
        val tt = texts.map { it.trim() }
        val meet = tt.firstOrNull { it.startsWith("Encontro com ") }?.removePrefix("Encontro com ")?.trim()
        val dest = tt.firstOrNull { it.startsWith("Destino de ") }?.removePrefix("Destino de ")?.trim()
        // Chegou no embarque: a Uber mostra "Aguardando…" / "Perto de …" (o botão "Iniciar" aparece desde o aceite)
        val atPickup = tt.any { it.contains("Aguardando", ignoreCase = true) || it.startsWith("Perto de ") }
        val ri = tt.indexOfFirst { it.startsWith("Como foi a viagem") }
        val rated = if (ri >= 0) tt.getOrNull(ri + 1) else null

        // 1) Avaliação = a corrida (de quem foi avaliado) terminou
        val cur = trip
        if (rated != null && cur != null && (cur.passenger == null || cur.passenger == rated)) {
            cur.passenger = cur.passenger ?: rated
            cur.ratedDone = true
            if (cur.arrivedDestAt == null) cur.arrivedDestAt = now
            finish(ctx, null)
        }
        // 2) Começa a próxima (emendada) quando a tela já mostra o próximo passageiro
        if (trip == null && meet != null && meet != rated) {
            val q = queued ?: lastOffer.takeIf { phase == Phase.OFERTA }
            if (q != null) {
                val at = if (queued != null) queuedAt else now
                queued = null
                startTrip(ctx, q, at)
                trip?.passenger = meet
            }
        }
        // 3) Fases da corrida atual
        val t = trip ?: return
        if (t.passenger == null) t.passenger = meet ?: dest
        if (atPickup && dest == null && t.arrivedPickupAt == null) {
            t.arrivedPickupAt = now
            if (phase == Phase.A_CAMINHO) phase = Phase.NO_EMBARQUE
        }
        if (dest != null && dest == t.passenger && t.leftPickupAt == null) {
            if (t.arrivedPickupAt == null) t.arrivedPickupAt = now
            t.leftPickupAt = now
            phase = Phase.EM_VIAGEM
        }
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
            val realPerHour = if (status.startsWith("concluída") && totalMin != null && totalMin > 0) o.price / (totalMin / 60.0) else null
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

/**
 * Trajeto LIVRE (sem corrida), para o Itinerário desenhar o dia inteiro como o Google.
 * Um ponto a cada 10 s se você andou 30 m ou mais. Vai para rotas.csv com id "livre-AAAAMMDD" e fase LIVRE.
 */
object FreeTrack {
    private const val EVERY_MS = 10_000L
    private const val MIN_MOVE_M = 30f
    private var last: android.location.Location? = null
    private var lastAt = 0L
    private val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
    private val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)

    fun onLocation(ctx: android.content.Context, loc: android.location.Location) {
        if (TripTracker.isOnTrip || RideScreen.active) { last = null; return }
        if (loc.hasAccuracy() && loc.accuracy > 60f) return
        val now = System.currentTimeMillis()
        if (now - lastAt < EVERY_MS) return
        val l = last
        if (l != null && l.distanceTo(loc) < MIN_MOVE_M) return
        last = loc
        lastAt = now
        try {
            val f = TripLog.routesFile(ctx)
            if (!f.exists()) f.writeText("id;data_hora;lat;lng;fase\n")
            val d = java.util.Date(now)
            f.appendText(String.format(java.util.Locale.US, "livre-%s;%s;%.6f;%.6f;LIVRE\n",
                day.format(d), fmt.format(d), loc.latitude, loc.longitude))
        } catch (_: Exception) {
        }
    }
}

/**
 * Resumo da sessão da Uber (aparece ao ficar offline):
 * "9 de out., 18:36 – 10 de out., 02:22 | R$ 458,29 | Viagens concluídas | Viagens oferecidas | 14 | 23".
 * Guardado em sessoes_uber.csv para conferir com o que o Radar registrou.
 */
object UberSession {
    private var lastKey = ""

    fun read(ctx: android.content.Context, texts: List<String>) {
        val tt = texts.map { it.trim() }
        val ci = tt.indexOf("Viagens concluídas")
        if (ci < 0) return
        val period = tt.firstOrNull { it.contains("–") && it.contains(" de ") && it.contains(":") } ?: return
        val money = tt.firstOrNull { it.startsWith("R$") } ?: return
        val nums = tt.drop(ci + 1).filter { it.matches(Regex("\\d{1,3}")) }
        val done = nums.getOrNull(0) ?: return
        val offered = nums.getOrNull(1) ?: ""
        val key = "$period|$money|$done"
        if (key == lastKey) return
        lastKey = key
        try {
            val f = java.io.File(ctx.filesDir, "sessoes_uber.csv")
            if (!f.exists()) f.writeText("periodo;valor;concluidas;oferecidas;lido_em\n")
            if (f.readText().contains("\"$period\";$money;$done;")) return
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            f.appendText("\"$period\";$money;$done;$offered;$now\n")
            OfferLog.appendDiag(ctx, "SESSÃO UBER: $period · $money · $done viagens")
        } catch (_: Exception) {
        }
    }
}
