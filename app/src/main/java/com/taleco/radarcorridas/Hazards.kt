package com.taleco.radarcorridas

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Uma valeta (ou buraco) marcada no mapa.
 * heading = sentido em que você passava (null = vale para os dois sentidos)
 * auto = detectada pelo solavanco (sem você marcar)
 */
class Hazard(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val heading: Float?,
    var count: Int,
    var auto: Boolean,
    var lastHit: Long
)

/** Valetas marcadas: ficam salvas no celular em valetas.csv. */
object Hazards {

    private const val FILE = "valetas.csv"
    private const val HEADER = "id;lat;lng;sentido;vezes;origem;ultima_vez"
    private const val MERGE_M = 25f

    private val list = mutableListOf<Hazard>()
    private var loaded = false

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun all(ctx: Context): List<Hazard> {
        load(ctx)
        return list.toList()
    }

    fun count(ctx: Context) = all(ctx).size
    fun autoCount(ctx: Context) = all(ctx).count { it.auto }

    @Synchronized
    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        try {
            f.readLines().drop(1).forEach { line ->
                val p = line.split(';')
                if (p.size < 7) return@forEach
                list.add(
                    Hazard(
                        p[0].toLongOrNull() ?: return@forEach,
                        p[1].toDoubleOrNull() ?: return@forEach,
                        p[2].toDoubleOrNull() ?: return@forEach,
                        p[3].toFloatOrNull(),
                        p[4].toIntOrNull() ?: 1,
                        p[5] == "auto",
                        p[6].toLongOrNull() ?: 0L
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun save(ctx: Context) {
        try {
            val sb = StringBuilder(HEADER).append('\n')
            for (h in list) {
                sb.append(h.id).append(';')
                    .append(String.format(Locale.US, "%.6f;%.6f;", h.lat, h.lng))
                    .append(h.heading?.let { String.format(Locale.US, "%.0f", it) } ?: "").append(';')
                    .append(h.count).append(';')
                    .append(if (h.auto) "auto" else "manual").append(';')
                    .append(h.lastHit).append('\n')
            }
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(sb.toString())
            tmp.renameTo(file(ctx))
        } catch (_: Exception) {
        }
    }

    private fun angleDiff(a: Float, b: Float): Float {
        val d = abs(((a - b) % 360f + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }

    /**
     * Marca uma valeta. Se já existir uma a menos de 25 m no mesmo sentido, só soma mais uma vez.
     * Devolve a valeta marcada e se ela é nova.
     */
    @Synchronized
    fun mark(ctx: Context, lat: Double, lng: Double, heading: Float?, auto: Boolean): Pair<Hazard, Boolean> {
        load(ctx)
        val here = Location("v").apply { latitude = lat; longitude = lng }
        val now = System.currentTimeMillis()
        val existing = list.firstOrNull { h ->
            val hl = Location("v").apply { latitude = h.lat; longitude = h.lng }
            here.distanceTo(hl) <= MERGE_M &&
                (h.heading == null || heading == null || angleDiff(h.heading, heading) <= 60f)
        }
        if (existing != null) {
            existing.count++
            existing.lastHit = now
            if (!auto) existing.auto = false
            save(ctx)
            return existing to false
        }
        val h = Hazard(now, lat, lng, heading, 1, auto, now)
        list.add(h)
        save(ctx)
        return h to true
    }

    /** Desfaz uma marcação (tira uma "vez"; se era a única, apaga a valeta). */
    @Synchronized
    fun unmark(ctx: Context, h: Hazard, wasNew: Boolean) {
        load(ctx)
        if (wasNew) list.remove(h) else h.count = (h.count - 1).coerceAtLeast(1)
        save(ctx)
    }

    /** Apaga a valeta marcada por último. */
    @Synchronized
    fun deleteLast(ctx: Context): Boolean {
        load(ctx)
        val last = list.maxByOrNull { it.lastHit } ?: return false
        list.remove(last)
        save(ctx)
        return true
    }

    /** Apaga a valeta mais perto (até 60 m): para quando o aviso apita num lugar sem valeta. */
    @Synchronized
    fun deleteNear(ctx: Context, lat: Double, lng: Double): Boolean {
        load(ctx)
        val here = Location("v").apply { latitude = lat; longitude = lng }
        val h = list.minByOrNull { here.distanceTo(Location("v").apply { latitude = it.lat; longitude = it.lng }) } ?: return false
        if (here.distanceTo(Location("v").apply { latitude = h.lat; longitude = h.lng }) > 60f) return false
        list.remove(h)
        save(ctx)
        return true
    }
}

/**
 * Avisa ao se aproximar de uma valeta marcada e cuida das marcações:
 *  - manual: volume − duas vezes rápido, ou segurar o botão "R";
 *  - automática: solavanco forte medido pelo acelerômetro.
 * Usa o GPS que o alerta de radar já liga quando a Uber/99 está aberta.
 */
object HazardWatch {

    private const val LOOKAHEAD_M = 600.0
    private const val PASS_RADIUS_M = 30.0
    private const val MIN_SPEED_KMH = 8.0
    private const val REPEAT_MS = 2 * 60_000L
    private const val UNDO_MS = 6_000L
    private const val ORANGE = 0xFFE8590C.toInt()

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private var target: Hazard? = null
    private var minDist = Double.MAX_VALUE
    private var startDist = LOOKAHEAD_M
    private var showing = false
    val isShowing: Boolean get() = showing
    private var dismissed: Hazard? = null
    private val recentlyPassed = HashMap<Hazard, Long>()

    // Últimas posições (para marcar onde o carro estava 1,5 s antes do aperto)
    private val history = ArrayDeque<Location>()
    private var lastKmh = 0.0
    private var lastHeading: Float? = null

    // Desfazer a última marcação
    private var undoCam: SpeedCam? = null
    private var undoHazard: Hazard? = null
    private var undoWasNew = false
    private var undoUntil = 0L

    private val hideBanner = Runnable {
        if (target == null || !showing) RadarService.instance?.hideSpeedBanner()
        undoHazard = null
        undoCam = null
    }

    // ---------- GPS ----------

    fun onLocation(ctx: Context, loc: Location, kmh: Double, heading: Float?, radarBusy: Boolean) {
        appContext = ctx.applicationContext
        history.addLast(loc)
        while (history.size > 15) history.removeFirst()
        lastKmh = kmh
        if (heading != null) lastHeading = heading

        val prefs = Prefs(ctx)
        if (!prefs.valetaAlerts) {
            if (showing) hide()
            target = null
            return
        }
        val now = System.currentTimeMillis()
        recentlyPassed.entries.removeAll { now - it.value > REPEAT_MS }

        val t = target ?: run {
            if (heading == null || kmh < MIN_SPEED_KMH) return
            val found = find(ctx, loc, heading, prefs.alertDistance(kmh)) ?: return
            target = found
            minDist = Double.MAX_VALUE
            startDist = loc.distanceTo(locOf(found)).toDouble().coerceAtLeast(40.0)
            if (!radarBusy && Prefs(ctx).speedSound) Beeper.play(ctx, Beeper.Kind.VALETA)
            found
        }

        val d = loc.distanceTo(locOf(t)).toDouble()
        if (d < minDist) minDist = d
        val behind = heading != null && d > 12 && angleDiff(heading, (loc.bearingTo(locOf(t)) + 360f) % 360f) > 110f
        val passed = minDist <= PASS_RADIUS_M && (d > minDist + 15 || behind)
        val gaveUp = d > LOOKAHEAD_M + 120 || (minDist > PASS_RADIUS_M && (d > minDist + 50 || behind))
        if (passed || gaveUp) {
            recentlyPassed[t] = now
            target = null
            if (dismissed === t) dismissed = null
            if (showing) hide()
            return
        }
        if (radarBusy || dismissed === t || now < undoUntil) {
            // o radar tem prioridade na tela; a valeta volta assim que ele sair
            if (radarBusy) showing = false
            return
        }
        val where = if (t.count > 1) "Valeta · marcada ${t.count}x" else if (t.auto) "Solavanco registrado" else "Valeta"
        handler.removeCallbacks(hideBanner)
        RadarService.instance?.showSpeedBanner(
            SpeedBanner(
                null, kmh.roundToInt(), where, ORANGE,
                if (d >= 100) (d / 10).roundToInt() * 10 else d.roundToInt(),
                (1.0 - d / startDist).coerceIn(0.0, 1.0).toFloat(),
                sign = "⚠"
            )
        )
        showing = true
    }

    private fun hide() {
        showing = false
        RadarService.instance?.hideSpeedBanner()
    }

    private fun locOf(h: Hazard) = Location("valeta").apply { latitude = h.lat; longitude = h.lng }

    private fun angleDiff(a: Float, b: Float): Float {
        val d = abs(((a - b) % 360f + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }

    private fun find(ctx: Context, loc: Location, heading: Float, lookahead: Double): Hazard? {
        var best: Hazard? = null
        var bestD = lookahead
        for (h in Hazards.all(ctx)) {
            if (recentlyPassed.containsKey(h)) continue
            val hl = locOf(h)
            val d = loc.distanceTo(hl).toDouble()
            if (d > bestD) continue
            if (h.heading != null && angleDiff(heading, h.heading) > 60f) continue
            if (d > 25) {
                val ang = angleDiff(heading, (loc.bearingTo(hl) + 360f) % 360f)
                if (ang > 40f) continue
                if (d * kotlin.math.sin(Math.toRadians(ang.toDouble())) > maxOf(25.0, d * 0.10)) continue // rua paralela
            }
            best = h
            bestD = d
        }
        return best
    }

    // ---------- Marcar ----------

    /** Posição de alguns segundos atrás (você aperta logo depois de passar pela valeta). Ajustável nas configurações. */
    private fun positionForMark(delayMs: Long): Location? {
        val now = System.currentTimeMillis()
        return history.lastOrNull { now - it.time >= delayMs } ?: history.lastOrNull()
    }

    /** Marcação manual (volume − duas vezes ou segurar o "R"). */
    fun markManual(ctx: Context) {
        appContext = ctx.applicationContext
        val loc = positionForMark((Prefs(ctx).valetaDelaySec * 1000).toLong())
        if (loc == null || System.currentTimeMillis() - loc.time > 30_000L) {
            Beeper.play(ctx, Beeper.Kind.ERRO)
            RadarService.instance?.showSpeedBanner(
                SpeedBanner(null, lastKmh.roundToInt(), "Sem GPS agora: abra a Uber/99 para marcar valetas", Colors.SURFACE_2, sign = "⚠")
            )
            handler.removeCallbacks(hideBanner)
            handler.postDelayed(hideBanner, 3_000L)
            return
        }
        val (h, isNew) = Hazards.mark(ctx, loc.latitude, loc.longitude, lastHeading, auto = false)
        confirm(ctx, h, isNew, if (isNew) "✓ Valeta marcada · toque para desfazer" else "✓ Valeta confirmada (${h.count}x) · toque para desfazer")
    }

    /** Marca um radar que faltava no mapa (volume + duas vezes). */
    fun markRadar(ctx: Context) {
        appContext = ctx.applicationContext
        val loc = positionForMark((Prefs(ctx).valetaDelaySec * 1000).toLong())
        if (loc == null || System.currentTimeMillis() - loc.time > 30_000L) {
            Beeper.play(ctx, Beeper.Kind.ERRO)
            RadarService.instance?.showSpeedBanner(
                SpeedBanner(null, lastKmh.roundToInt(), "Sem GPS agora: abra a Uber/99 para marcar radares", Colors.SURFACE_2, sign = "📷")
            )
            handler.removeCallbacks(hideBanner)
            handler.postDelayed(hideBanner, 3_000L)
            return
        }
        val cam = SpeedCams.addUser(ctx, loc.latitude, loc.longitude, lastHeading, null)
        if (Prefs(ctx).speedSound) Beeper.play(ctx, Beeper.Kind.MARCADA)
        undoCam = cam
        undoHazard = null
        undoUntil = System.currentTimeMillis() + UNDO_MS
        RadarService.instance?.showSpeedBanner(
            SpeedBanner(null, lastKmh.roundToInt(), "✓ Radar marcado · defina o limite no app · toque para desfazer",
                Colors.SURFACE_2, null, 1f, sign = "📷")
        )
        handler.removeCallbacks(hideBanner)
        handler.postDelayed(hideBanner, UNDO_MS)
    }

    /** Marcação automática pelo solavanco. */
    fun markAuto(ctx: Context) {
        val loc = history.lastOrNull() ?: return
        if (System.currentTimeMillis() - loc.time > 5_000L) return
        val (h, isNew) = Hazards.mark(ctx, loc.latitude, loc.longitude, lastHeading, auto = true)
        OfferLog.appendDiag(ctx, "VALETAS: solavanco a ${lastKmh.roundToInt()} km/h (${if (isNew) "novo ponto" else "${h.count}x"})")
        confirm(ctx, h, isNew, if (isNew) "Solavanco registrado · toque para desfazer" else "Valeta confirmada (${h.count}x) · toque para desfazer")
    }

    private fun confirm(ctx: Context, h: Hazard, isNew: Boolean, text: String) {
        if (Prefs(ctx).speedSound) Beeper.play(ctx, Beeper.Kind.MARCADA)
        undoCam = null
        undoHazard = h
        undoWasNew = isNew
        undoUntil = System.currentTimeMillis() + UNDO_MS
        recentlyPassed[h] = System.currentTimeMillis()
        RadarService.instance?.showSpeedBanner(
            SpeedBanner(null, lastKmh.roundToInt(), text, ORANGE, null, 1f, sign = "✓")
        )
        handler.removeCallbacks(hideBanner)
        handler.postDelayed(hideBanner, UNDO_MS)
    }

    val lastSpeedKmh: Double get() = lastKmh

    /** Toque no aviso: desfaz a marcação recente, ou fecha o aviso. */
    fun onBannerTap() {
        // Aviso de ponto bom: o toque abre o Waze até o ponto
        val spot = SpotWatch.current
        val svc = RadarService.instance
        if (SpotWatch.showing && spot != null && svc != null && spot.id != 0L) {
            SpotWatch.onBannerClosed()
            svc.hideSpeedBanner()
            Nav.waze(svc, spot.lat, spot.lng)
            return
        }
        val ctx = appContext
        val cam = undoCam
        if (ctx != null && cam != null && System.currentTimeMillis() < undoUntil) {
            SpeedCams.removeUser(ctx, cam)
            undoCam = null
            undoUntil = 0L
            RadarService.instance?.showSpeedBanner(
                SpeedBanner(null, lastKmh.roundToInt(), "Marcação desfeita", Colors.SURFACE_2, null, 0f, sign = "↺")
            )
            handler.removeCallbacks(hideBanner)
            handler.postDelayed(hideBanner, 2_000L)
            return
        }
        val u = undoHazard
        if (ctx != null && u != null && System.currentTimeMillis() < undoUntil) {
            Hazards.unmark(ctx, u, undoWasNew)
            undoHazard = null
            undoUntil = 0L
            recentlyPassed.remove(u)
            RadarService.instance?.showSpeedBanner(
                SpeedBanner(null, lastKmh.roundToInt(), "Marcação desfeita", Colors.SURFACE_2, null, 0f, sign = "↺")
            )
            handler.removeCallbacks(hideBanner)
            handler.postDelayed(hideBanner, 2_000L)
            return
        }
        target?.let { dismissed = it }
        showing = false
        SpotWatch.onBannerClosed()
        SpeedWatch.dismiss()
    }

    fun reset() {
        target = null
        showing = false
        history.clear()
    }

    // ---------- Teste pela tela de configuração ----------

    fun demo(ctx: Context) {
        appContext = ctx.applicationContext
        val s = RadarService.instance ?: return
        val steps = listOf(
            SpeedBanner(null, 32, "Valeta · marcada 3x", ORANGE, 200, 0.2f, sign = "⚠"),
            SpeedBanner(null, 25, "Valeta · marcada 3x", ORANGE, 120, 0.5f, sign = "⚠"),
            SpeedBanner(null, 18, "Valeta · marcada 3x", ORANGE, 50, 0.8f, sign = "⚠"),
            SpeedBanner(null, 12, "Valeta · marcada 3x", ORANGE, 10, 0.96f, sign = "⚠"),
            SpeedBanner(null, 12, "✓ Valeta marcada · toque para desfazer", ORANGE, null, 1f, sign = "✓")
        )
        steps.forEachIndexed { i, b ->
            handler.postDelayed({
                s.showSpeedBanner(b)
                if (i == 0) Beeper.play(ctx, Beeper.Kind.VALETA)
                if (i == 4) Beeper.play(ctx, Beeper.Kind.MARCADA)
            }, i * 1200L)
        }
        handler.postDelayed({ if (target == null) s.hideSpeedBanner() }, steps.size * 1200L + 2500L)
    }
}

/**
 * Detecta solavancos fortes (valeta, buraco, lombada mal passada) pelo acelerômetro.
 * Mede a aceleração na vertical (direção da gravidade), então funciona com o celular em qualquer posição no suporte.
 */
object BumpDetector : SensorEventListener {

    private var sm: SensorManager? = null
    private var appContext: Context? = null
    private var running = false

    private val g = FloatArray(3)
    private var gReady = false
    private var samples = 0

    // Janela de ~0,6 s da aceleração vertical
    private val window = ArrayDeque<Pair<Long, Float>>()
    private var lastBumpAt = 0L
    private var lastGDir = FloatArray(3)
    private var lastGDirAt = 0L
    private var handledUntil = 0L
    private var prefs: Prefs? = null

    fun start(ctx: Context) {
        if (running) return
        val manager = ctx.getSystemService(SensorManager::class.java) ?: return
        val acc = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        appContext = ctx.applicationContext
        prefs = Prefs(ctx)
        sm = manager
        gReady = false
        samples = 0
        window.clear()
        running = manager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        try { sm?.unregisterListener(this) } catch (_: Exception) {}
        running = false
        window.clear()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val ctx = appContext ?: return
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        // Gravidade: média lenta (filtro passa-baixa)
        val a = if (gReady) 0.97f else 0.5f
        g[0] = a * g[0] + (1 - a) * x
        g[1] = a * g[1] + (1 - a) * y
        g[2] = a * g[2] + (1 - a) * z
        if (++samples > 60) gReady = true
        if (!gReady) return
        val gn = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        if (gn < 5f) return
        val ux = g[0] / gn
        val uy = g[1] / gn
        val uz = g[2] / gn
        val now = System.currentTimeMillis()

        // Se a posição do celular mudou (alguém pegou o celular), ignora por 3 s.
        if (now - lastGDirAt > 1000L) {
            val dot = ux * lastGDir[0] + uy * lastGDir[1] + uz * lastGDir[2]
            if (lastGDirAt > 0 && dot < 0.94f) handledUntil = now + 3_000L // mudou mais de ~20°
            lastGDir = floatArrayOf(ux, uy, uz)
            lastGDirAt = now
        }

        val vertical = (x - g[0]) * ux + (y - g[1]) * uy + (z - g[2]) * uz
        window.addLast(now to vertical)
        while (window.isNotEmpty() && now - window.first().first > 600L) window.removeFirst()

        if (now < handledUntil || now - lastBumpAt < 5_000L) return
        if (HazardWatch.lastSpeedKmh < 5.0) return
        val prefs = prefs ?: Prefs(ctx).also { prefs = it }
        if (!prefs.valetaAuto) return

        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for ((_, v) in window) {
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        // Sensibilidade: 0 (baixa) = 11 m/s² de pico a pico; 1 (alta) = 5 m/s²
        val threshold = 11f - 6f * prefs.valetaSensitivity
        if (mx - mn >= threshold) {
            lastBumpAt = now
            window.clear()
            HazardWatch.markAuto(ctx)
        }
    }
}
