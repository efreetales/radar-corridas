package com.taleco.radarcorridas

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** O que o aviso de radar mostra na tela. */
class SpeedBanner(
    val limit: Int?,
    val speedKmh: Int,
    val line: String,
    val color: Int,
    val distanceM: Int? = null,   // metros até o radar (null depois de passar)
    val progress: Float = 0f,     // 0 = acabou de avistar o radar, 1 = em cima dele
    val sign: String? = null,     // no lugar da placa de limite (ex.: "⚠" para valeta)
    val title: String? = null,    // no lugar da velocidade (ex.: nome do ponto)
    val distText: String? = null, // distância já formatada (ex.: "1,8")
    val distUnit: String = "metros"
)

/**
 * Alerta de radar:
 *  - liga o GPS enquanto o app da Uber/99 estiver em uso;
 *  - ao se aproximar de um radar, mostra o limite e a sua velocidade, e apita se estiver acima;
 *  - depois de passar, avisa se a velocidade passou da tolerância (provável multa) e guarda no histórico.
 */
object SpeedWatch {

    private const val LOOKAHEAD_M = 400.0        // começa a avisar a esta distância
    private const val PASS_RADIUS_M = 45.0       // passou "pelo" radar
    private const val NEAR_M = 50.0              // velocidade medida perto do radar
    private const val AHEAD_TOLERANCE = 45f      // radar à frente (graus)
    private const val DIRECTION_TOLERANCE = 60f  // mesmo sentido da via (graus)
    private const val MIN_SPEED_KMH = 12.0
    private const val MAX_ACCURACY_M = 30f
    private const val IDLE_STOP_MS = 20 * 60_000L
    private const val REPEAT_MS = 3 * 60_000L
    private const val RESULT_SHOW_MS = 5_000L

    private const val CHANNEL = "multas"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    @Volatile
    var active = false
        private set

    private var lastRideAppAt = 0L
    private var lastLoc: Location? = null
    private var lastHeading: Float? = null

    private var target: SpeedCam? = null
    private var minDist = Double.MAX_VALUE
    private var maxNearKmh = 0.0
    private var speedAtMin = 0.0
    private var lastBeepAt = 0L
    private var startDist = LOOKAHEAD_M
    private var dismissedCam: SpeedCam? = null
    private val recentlyPassed = HashMap<SpeedCam, Long>()

    private val idleCheck = object : Runnable {
        override fun run() {
            val ctx = appContext ?: return
            if (!active) return
            if (System.currentTimeMillis() - lastRideAppAt > IDLE_STOP_MS) {
                stop(ctx)
                OfferLog.appendDiag(ctx, "RADARES: GPS desligado (Uber/99 fechada há 20 min)")
            } else {
                handler.postDelayed(this, 60_000L)
            }
        }
    }

    private val hideResult = Runnable { if (target == null) RadarService.instance?.hideSpeedBanner() }

    // ---------- Liga / desliga ----------

    /** Chamado sempre que a tela da Uber/99 aparece. */
    fun onRideAppSeen(ctx: Context, prefs: Prefs) {
        appContext = ctx.applicationContext
        lastRideAppAt = System.currentTimeMillis()
        if (!prefs.speedAlerts && !prefs.valetaAlerts && !prefs.valetaAuto && !prefs.spotAlerts) {
            if (active) stop(ctx)
            return
        }
        if (!active || !TrackingService.running) start(ctx)
    }

    private fun hasLocation(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private var warnedNoPermission = false

    private fun start(ctx: Context) {
        if (!hasLocation(ctx)) {
            if (!warnedNoPermission) OfferLog.appendDiag(ctx, "RADARES: sem permissão de localização")
            warnedNoPermission = true
            return
        }
        active = TrackingService.start(ctx, TrackingService.RADARES)
        if (active) {
            if (Prefs(ctx).valetaAuto) BumpDetector.start(ctx)
            handler.removeCallbacks(idleCheck)
            handler.postDelayed(idleCheck, 60_000L)
        }
    }

    fun stop(ctx: Context) {
        active = false
        handler.removeCallbacks(idleCheck)
        clearTarget()
        lastLoc = null
        lastHeading = null
        TrackingService.stop(ctx, TrackingService.RADARES)
        BumpDetector.stop()
        HazardWatch.reset()
        RadarService.instance?.hideSpeedBanner()
    }

    // ---------- GPS ----------

    fun onLocation(ctx: Context, loc: Location) {
        if (!active) return
        if (loc.provider != LocationManager.GPS_PROVIDER) return
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        val prev = lastLoc
        val kmh = when {
            loc.hasSpeed() -> loc.speed * 3.6
            prev != null && loc.time > prev.time -> prev.distanceTo(loc) / ((loc.time - prev.time) / 1000.0) * 3.6
            else -> 0.0
        }
        val heading: Float? = when {
            loc.hasBearing() && loc.speed > 2f -> loc.bearing
            prev != null && prev.distanceTo(loc) > 8f -> prev.bearingTo(loc).let { (it + 360f) % 360f }
            else -> lastHeading
        }
        lastLoc = loc
        if (heading != null) lastHeading = heading

        val now = System.currentTimeMillis()
        recentlyPassed.entries.removeAll { now - it.value > REPEAT_MS }

        val radarOn = Prefs(ctx).speedAlerts
        if (!radarOn && target != null) clearTarget()
        val t = target
        if (t != null) {
            followTarget(ctx, loc, t, kmh, heading)
        } else if (radarOn && heading != null && kmh >= MIN_SPEED_KMH) {
            findTarget(loc, heading)?.let { cam ->
                target = cam
                minDist = Double.MAX_VALUE
                maxNearKmh = 0.0
                speedAtMin = kmh
                startDist = loc.distanceTo(camLocation(cam)).toDouble().coerceAtLeast(50.0)
                handler.removeCallbacks(hideResult)
                beep(Beeper.Kind.RADAR_A_FRENTE)
                followTarget(ctx, loc, cam, kmh, heading)
            }
        }

        // Valetas: aparecem quando não há radar na tela
        val radarBusy = target != null && target !== dismissedCam
        HazardWatch.onLocation(ctx, loc, kmh, heading, radarBusy)

        // Pontos bons: só quando não há radar nem valeta na tela
        SpotWatch.onLocation(ctx, loc, kmh, radarBusy || HazardWatch.isShowing)
    }

    private fun camLocation(c: SpeedCam) = Location("radar").apply {
        latitude = c.lat
        longitude = c.lng
    }

    private fun angleDiff(a: Float, b: Float): Float {
        val d = abs(((a - b) % 360f + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }

    private fun findTarget(loc: Location, heading: Float): SpeedCam? {
        var best: SpeedCam? = null
        var bestD = LOOKAHEAD_M
        for (c in SpeedCams.nearby(loc.latitude, loc.longitude)) {
            if (recentlyPassed.containsKey(c)) continue
            val cl = camLocation(c)
            val d = loc.distanceTo(cl).toDouble()
            if (d > bestD) continue
            if (c.bearing != null && angleDiff(heading, c.bearing) > DIRECTION_TOLERANCE) continue
            if (d > 30) {
                val ang = angleDiff(heading, (loc.bearingTo(cl) + 360f) % 360f)
                if (ang > AHEAD_TOLERANCE) continue
                // Distância lateral do radar à sua linha de direção: evita pegar radar da rua paralela
                // (ex.: Alameda Santos quando você está na Paulista, ~100 m ao lado).
                val lateral = d * kotlin.math.sin(Math.toRadians(ang.toDouble()))
                if (lateral > maxOf(30.0, d * 0.10)) continue
            }
            best = c
            bestD = d
        }
        return best
    }

    private fun followTarget(ctx: Context, loc: Location, cam: SpeedCam, kmh: Double, heading: Float?) {
        val cl = camLocation(cam)
        val d = loc.distanceTo(cl).toDouble()
        if (d < minDist) {
            minDist = d
            speedAtMin = kmh
        }
        if (d <= NEAR_M && kmh > maxNearKmh) maxNearKmh = kmh

        val behind = heading != null && d > 15 && angleDiff(heading, (loc.bearingTo(cl) + 360f) % 360f) > 110f
        // Radar ficou de lado (você entrou em outra rua, ou era de uma rua paralela): desiste dele
        val sideways = heading != null && d > 60 &&
            d * kotlin.math.sin(Math.toRadians(angleDiff(heading, (loc.bearingTo(cl) + 360f) % 360f).toDouble())) > maxOf(45.0, d * 0.15)
        val passed = minDist <= PASS_RADIUS_M && (d > minDist + 20 || behind)
        val gaveUp = d > LOOKAHEAD_M + 150 || sideways || (minDist > PASS_RADIUS_M && (d > minDist + 60 || behind))

        when {
            passed -> {
                val measured = if (maxNearKmh > 0) maxNearKmh else speedAtMin
                recentlyPassed[cam] = System.currentTimeMillis()
                clearTarget()
                onPassed(ctx, cam, measured, loc)
            }
            gaveUp -> {
                recentlyPassed[cam] = System.currentTimeMillis()
                clearTarget()
                if (dismissedCam === cam) dismissedCam = null
                else RadarService.instance?.hideSpeedBanner()
            }
            else -> showApproach(cam, kmh, d)
        }
    }

    private fun clearTarget() {
        target = null
        minDist = Double.MAX_VALUE
        maxNearKmh = 0.0
    }

    /** Até quanto o radar tolera antes de multar: 7 km/h até 100 km/h, 7% acima disso. */
    fun toleratedUpTo(limit: Int): Int = if (limit <= 100) limit + 7 else limit + ceil(limit * 0.07).toInt()

    private fun colorFor(limit: Int?, kmh: Int): Int = when {
        limit == null -> Colors.SURFACE_2
        kmh > toleratedUpTo(limit) -> Colors.RED
        kmh > limit -> Colors.YELLOW
        else -> Colors.GREEN_DARK
    }

    /** Fecha o aviso do radar atual (o botão ✕). Ele volta no próximo radar. */
    fun dismiss() {
        target?.let { dismissedCam = it }
        handler.removeCallbacks(hideResult)
        RadarService.instance?.hideSpeedBanner()
    }

    private fun roundDist(d: Double): Int = if (d >= 100) (d / 10).roundToInt() * 10 else d.roundToInt()

    private fun showApproach(cam: SpeedCam, kmhD: Double, d: Double) {
        if (cam === dismissedCam) return
        val kmh = kmhD.roundToInt()
        val where = cam.name.ifBlank { if (cam.mine) "Radar marcado por você" else "Radar de velocidade" }
        val progress = (1.0 - d / startDist).coerceIn(0.0, 1.0).toFloat()
        RadarService.instance?.showSpeedBanner(
            SpeedBanner(cam.limit, kmh, where, colorFor(cam.limit, kmh), roundDist(d), progress)
        )
        val limit = cam.limit ?: return
        val now = System.currentTimeMillis()
        if (kmh > limit && now - lastBeepAt > 2_500L) {
            lastBeepAt = now
            beep(Beeper.Kind.ACIMA)
        }
    }

    // ---------- Depois do radar ----------

    private fun onPassed(ctx: Context, cam: SpeedCam, kmhD: Double, loc: Location) {
        val kmh = kmhD.roundToInt()
        val limit = cam.limit
        val result = when {
            limit == null -> "limite desconhecido"
            kmh > toleratedUpTo(limit) -> "provável multa"
            kmh > limit -> "acima do limite, dentro da tolerância"
            else -> "dentro do limite"
        }
        PassLog.append(ctx, cam, kmh, result, loc)

        val line = when {
            limit == null -> "Passou a $kmh km/h (limite desconhecido)"
            kmh > toleratedUpTo(limit) -> "⚠️ Provável multa: $kmh no limite de $limit"
            kmh > limit -> "Passou a $kmh no limite de $limit (tolera até ${toleratedUpTo(limit)})"
            else -> "✓ Passou a $kmh no limite de $limit"
        }
        val wasDismissed = dismissedCam === cam
        dismissedCam = null
        if (!wasDismissed) {
            RadarService.instance?.showSpeedBanner(SpeedBanner(limit, kmh, line, colorFor(limit, kmh), null, 1f))
            handler.removeCallbacks(hideResult)
            handler.postDelayed(hideResult, RESULT_SHOW_MS)
        }

        if (limit != null && kmh > toleratedUpTo(limit)) {
            beep(Beeper.Kind.MULTA)
            notifyFine(ctx, cam, kmh, limit)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyFine(ctx: Context, cam: SpeedCam, kmh: Int, limit: Int) {
        try {
            createChannel(ctx)
            val time = SimpleDateFormat("HH:mm", PT_BR).format(Date())
            val where = cam.name.ifBlank { "radar sem nome no mapa" }
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_radar)
                .setContentTitle("⚠️ Possível multa: $kmh km/h (limite $limit)")
                .setContentText("$where · $time · o radar tolera até ${toleratedUpTo(limit)} km/h")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "$where · $time\nVelocidade pelo GPS: $kmh km/h. Limite: $limit km/h " +
                            "(o radar tolera até ${toleratedUpTo(limit)} km/h).\n" +
                            "É uma estimativa. A confirmação chega pelo SNE na Carteira Digital de Trânsito."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() / 1000).toInt(), n)
        } catch (_: Exception) {
        }
    }

    private fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Possíveis multas de radar", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun beep(kind: Beeper.Kind) {
        val ctx = appContext ?: return
        if (!Prefs(ctx).speedSound) return
        Beeper.play(ctx, kind)
    }

    // ---------- Teste pela tela de configuração ----------

    fun demo(ctx: Context) {
        appContext = ctx.applicationContext
        val s = RadarService.instance ?: return
        val steps = listOf(
            SpeedBanner(50, 48, "Av. Exemplo", Colors.GREEN_DARK, 380, 0.05f),
            SpeedBanner(50, 49, "Av. Exemplo", Colors.GREEN_DARK, 300, 0.25f),
            SpeedBanner(50, 55, "Av. Exemplo", Colors.YELLOW, 200, 0.5f),
            SpeedBanner(50, 63, "Av. Exemplo", Colors.RED, 100, 0.75f),
            SpeedBanner(50, 63, "Av. Exemplo", Colors.RED, 30, 0.93f),
            SpeedBanner(50, 63, "⚠️ Provável multa: 63 no limite de 50", Colors.RED, null, 1f)
        )
        steps.forEachIndexed { i, b ->
            handler.postDelayed({
                s.showSpeedBanner(b)
                if (i == 0) beep(Beeper.Kind.RADAR_A_FRENTE)
                if (i == 3) beep(Beeper.Kind.ACIMA)
                if (i == 5) beep(Beeper.Kind.MULTA)
            }, i * 1200L)
        }
        handler.postDelayed({ if (target == null) s.hideSpeedBanner() }, steps.size * 1200L + 3000L)
    }
}

/** Histórico das passagens por radar: passagens_radar.csv */
object PassLog {

    private const val HEADER = "data_hora;via;limite;velocidade_gps;tolera_ate;resultado;lat;lng"

    fun file(ctx: Context) = File(ctx.filesDir, "passagens_radar.csv")

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", PT_BR)

    fun append(ctx: Context, cam: SpeedCam, kmh: Int, result: String, loc: Location) {
        try {
            val f = file(ctx)
            if (!f.exists()) f.writeText(HEADER + "\n")
            val limit = cam.limit
            val line = listOf(
                fmt.format(Date()),
                "\"" + cam.name.replace("\"", "'") + "\"",
                limit?.toString() ?: "",
                kmh.toString(),
                limit?.let { SpeedWatch.toleratedUpTo(it).toString() } ?: "",
                result,
                String.format(Locale.US, "%.6f", loc.latitude),
                String.format(Locale.US, "%.6f", loc.longitude)
            ).joinToString(";")
            f.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }

    /** Últimas passagens com provável multa (mais recentes primeiro). */
    fun recentFines(ctx: Context, max: Int = 5): List<String> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines().drop(1).filter { it.contains(";provável multa;") }.takeLast(max).reversed().map { l ->
                val p = l.split(';')
                val date = p.getOrNull(0)?.let { d ->
                    // 2026-10-08 14:32:10 -> 08/10 14:32
                    if (d.length >= 16) "${d.substring(8, 10)}/${d.substring(5, 7)} ${d.substring(11, 16)}" else d
                } ?: ""
                val via = p.getOrNull(1)?.trim('"')?.ifBlank { "radar" } ?: "radar"
                "$date · $via · ${p.getOrNull(3)} km/h no limite de ${p.getOrNull(2)}"
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun count(ctx: Context): Int {
        val f = file(ctx)
        if (!f.exists()) return 0
        return try { (f.readLines().count { it.isNotBlank() } - 1).coerceAtLeast(0) } catch (_: Exception) { 0 }
    }
}
