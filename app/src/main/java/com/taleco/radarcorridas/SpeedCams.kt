package com.taleco.radarcorridas

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor

/**
 * Um radar de velocidade.
 * limit = limite em km/h (null se não soubermos)
 * bearing = sentido da via em graus (null se a via for mão dupla: vale para os dois sentidos)
 */
class SpeedCam(
    val lat: Double,
    val lng: Double,
    var limit: Int?,
    val bearing: Float?,
    val name: String,
    val mine: Boolean = false,   // marcado por você (não veio do mapa)
    val id: Long = 0L
)

/**
 * Lista de radares da Grande São Paulo, baixada do OpenStreetMap (mapa colaborativo, gratuito).
 * Fica salva no celular e é atualizada uma vez por mês.
 */
object SpeedCams {

    private const val FILE = "radares.tsv"
    private const val CELL = 0.01 // ~1,1 km
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    /** sul, oeste, norte, leste: Grande São Paulo e arredores. */
    private const val BBOX = "-24.10,-47.10,-23.20,-46.20"

    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    private const val ROADS =
        "^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|" +
            "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"

    private val QUERY = """
        [out:json][timeout:180];
        rel["type"="enforcement"]["enforcement"~"maxspeed|average_speed"]($BBOX)->.e;
        (
          node["highway"="speed_camera"]($BBOX);
          node(r.e:"device");
        )->.c;
        .c out body;
        .e out body;
        way(around.c:35)["highway"~"$ROADS"];
        out tags geom;
    """.trimIndent()

    private const val USER_FILE = "meus_radares.tsv"

    @Volatile
    private var grid: Map<Long, List<SpeedCam>> = emptyMap()
    private var mapCams: List<SpeedCam> = emptyList()
    private val userCams = mutableListOf<SpeedCam>()
    private var userLoaded = false

    @Volatile
    var count = 0
        private set

    @Volatile
    var downloading = false
        private set

    private val main = Handler(Looper.getMainLooper())

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun updatedAt(ctx: Context): Long = file(ctx).let { if (it.exists()) it.lastModified() else 0L }

    fun isStale(ctx: Context) = count == 0 || System.currentTimeMillis() - updatedAt(ctx) > MAX_AGE_MS

    private fun key(lat: Double, lng: Double): Long {
        val a = floor(lat / CELL).toLong()
        val b = floor(lng / CELL).toLong()
        return a * 100_000L + b
    }

    /** Lê a lista salva no celular. */
    fun load(ctx: Context) {
        loadUser(ctx)
        val f = file(ctx)
        if (!f.exists()) {
            setAll(emptyList())
            return
        }
        try {
            val list = f.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) return@mapNotNull null
                val lat = p[0].toDoubleOrNull() ?: return@mapNotNull null
                val lng = p[1].toDoubleOrNull() ?: return@mapNotNull null
                SpeedCam(lat, lng, p[2].toIntOrNull(), p[3].toFloatOrNull(), p[4])
            }
            setAll(list)
        } catch (_: Exception) {
        }
    }

    private fun setAll(list: List<SpeedCam>) {
        mapCams = list
        rebuild()
    }

    @Synchronized
    private fun rebuild() {
        val g = HashMap<Long, MutableList<SpeedCam>>()
        for (c in mapCams) g.getOrPut(key(c.lat, c.lng)) { mutableListOf() }.add(c)
        for (c in userCams) g.getOrPut(key(c.lat, c.lng)) { mutableListOf() }.add(c)
        grid = g
        count = mapCams.size + userCams.size
    }

    // ---------- Radares marcados por você ----------

    fun userFile(ctx: Context) = File(ctx.filesDir, USER_FILE)

    @Synchronized
    private fun loadUser(ctx: Context) {
        if (userLoaded) return
        userLoaded = true
        val f = userFile(ctx)
        if (!f.exists()) return
        try {
            f.readLines().forEach { line ->
                val p = line.split('\t')
                if (p.size < 5) return@forEach
                userCams.add(
                    SpeedCam(
                        p[1].toDoubleOrNull() ?: return@forEach, p[2].toDoubleOrNull() ?: return@forEach,
                        p[3].toIntOrNull(), p[4].toFloatOrNull(), p.getOrElse(5) { "" }, true,
                        p[0].toLongOrNull() ?: 0L
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun saveUser(ctx: Context) {
        try {
            val sb = StringBuilder()
            for (c in userCams) {
                sb.append(c.id).append('\t')
                    .append(String.format(Locale.US, "%.6f\t%.6f\t", c.lat, c.lng))
                    .append(c.limit?.toString() ?: "").append('\t')
                    .append(c.bearing?.let { String.format(Locale.US, "%.0f", it) } ?: "").append('\t')
                    .append(c.name.replace('\t', ' ')).append('\n')
            }
            userFile(ctx).writeText(sb.toString())
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun userList(ctx: Context): List<SpeedCam> {
        loadUser(ctx)
        return userCams.toList()
    }

    /** Marca um radar que faltava no mapa. O limite pode ser escolhido depois no app. */
    @Synchronized
    fun addUser(ctx: Context, lat: Double, lng: Double, bearing: Float?, limit: Int?): SpeedCam {
        loadUser(ctx)
        val c = SpeedCam(lat, lng, limit, bearing, "", true, System.currentTimeMillis())
        userCams.add(c)
        saveUser(ctx)
        rebuild()
        return c
    }

    @Synchronized
    fun removeUser(ctx: Context, c: SpeedCam) {
        loadUser(ctx)
        userCams.removeAll { it.id == c.id }
        saveUser(ctx)
        rebuild()
    }

    @Synchronized
    fun setUserLimit(ctx: Context, c: SpeedCam, limit: Int?) {
        loadUser(ctx)
        userCams.firstOrNull { it.id == c.id }?.limit = limit
        saveUser(ctx)
    }

    /** Radares a até ~1 km do ponto. */
    fun nearby(lat: Double, lng: Double): List<SpeedCam> {
        val g = grid
        if (g.isEmpty()) return emptyList()
        val out = ArrayList<SpeedCam>()
        for (dy in -1..1) for (dx in -1..1) {
            g[key(lat + dy * CELL, lng + dx * CELL)]?.let { out.addAll(it) }
        }
        return out
    }

    /**
     * Baixa a lista nova do OpenStreetMap, sem travar a tela.
     * onDone(quantidade, erro) volta na thread principal.
     */
    fun download(ctx: Context, onDone: ((Int?, String?) -> Unit)? = null) {
        if (downloading) return
        downloading = true
        val app = ctx.applicationContext
        Thread {
            var result: Int? = null
            var error: String? = null
            for (endpoint in ENDPOINTS) {
                try {
                    val json = fetch(endpoint)
                    val cams = parse(json)
                    if (cams.isEmpty()) {
                        error = "o mapa não retornou radares"
                        continue
                    }
                    save(app, cams)
                    setAll(cams)
                    result = cams.size
                    error = null
                    break
                } catch (e: Exception) {
                    error = "${e.javaClass.simpleName}: ${e.message}"
                }
            }
            downloading = false
            OfferLog.appendDiag(app, if (result != null) "RADARES: lista atualizada, $result radares" else "RADARES: falha ao baixar ($error)")
            main.post { onDone?.invoke(result, error) }
        }.start()
    }

    private fun fetch(endpoint: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 200_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "RadarCorridas/1.0 (Android)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(QUERY, "UTF-8")).toByteArray()) }
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("resposta $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Montagem da lista ----------

    private class Seg(
        val aLat: Double, val aLng: Double, val bLat: Double, val bLng: Double,
        val limit: Int?, val oneway: Int, val name: String
    )

    private fun parseLimit(v: String?): Int? {
        if (v.isNullOrBlank()) return null
        val n = Regex("\\d+").find(v)?.value?.toIntOrNull() ?: return null
        if (n < 10 || n > 130) return null
        return if (v.contains("mph", ignoreCase = true)) (n * 1.609).toInt() else n
    }

    private fun parse(text: String): List<SpeedCam> {
        val elements = JSONObject(text).optJSONArray("elements") ?: return emptyList()

        class Node(val id: Long, val lat: Double, val lng: Double, val limit: Int?, val name: String)

        val nodes = ArrayList<Node>()
        val relLimit = HashMap<Long, Int>()
        val segGrid = HashMap<Long, MutableList<Seg>>()

        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val tags = el.optJSONObject("tags")
            when (el.optString("type")) {
                "node" -> nodes.add(
                    Node(
                        el.optLong("id"), el.optDouble("lat"), el.optDouble("lon"),
                        parseLimit(tags?.optString("maxspeed")), tags?.optString("name").orEmpty()
                    )
                )
                "relation" -> {
                    val limit = parseLimit(tags?.optString("maxspeed")) ?: continue
                    val members = el.optJSONArray("members") ?: continue
                    for (m in 0 until members.length()) {
                        val mem = members.optJSONObject(m) ?: continue
                        if (mem.optString("type") == "node" && mem.optString("role") == "device") {
                            relLimit[mem.optLong("ref")] = limit
                        }
                    }
                }
                "way" -> {
                    val geom = el.optJSONArray("geometry") ?: continue
                    val highway = tags?.optString("highway").orEmpty()
                    val ow = tags?.optString("oneway").orEmpty()
                    val oneway = when {
                        ow == "-1" -> -1
                        ow == "yes" || ow == "true" || ow == "1" -> 1
                        ow == "no" -> 0
                        tags?.optString("junction") == "roundabout" -> 1
                        highway == "motorway" || highway == "motorway_link" -> 1
                        else -> 0
                    }
                    val limit = parseLimit(tags?.optString("maxspeed"))
                    val name = tags?.optString("name").orEmpty()
                    for (k in 0 until geom.length() - 1) {
                        val a = geom.optJSONObject(k) ?: continue
                        val b = geom.optJSONObject(k + 1) ?: continue
                        val s = Seg(a.optDouble("lat"), a.optDouble("lon"), b.optDouble("lat"), b.optDouble("lon"), limit, oneway, name)
                        val keys = hashSetOf(key(s.aLat, s.aLng), key(s.bLat, s.bLng), key((s.aLat + s.bLat) / 2, (s.aLng + s.bLng) / 2))
                        for (kk in keys) segGrid.getOrPut(kk) { mutableListOf() }.add(s)
                    }
                }
            }
        }

        val out = ArrayList<SpeedCam>()
        val seen = HashSet<Long>()
        for (n in nodes) {
            if (!seen.add(n.id)) continue
            // Via mais próxima do radar (até 35 m)
            var best: Seg? = null
            var bestD = 35.0
            for (dy in -1..1) for (dx in -1..1) {
                val list = segGrid[key(n.lat + dy * CELL, n.lng + dx * CELL)] ?: continue
                for (s in list) {
                    val d = distToSeg(n.lat, n.lng, s)
                    if (d < bestD) {
                        bestD = d
                        best = s
                    }
                }
            }
            val limit = n.limit ?: relLimit[n.id] ?: best?.limit
            val bearing: Float? = best?.let { s ->
                when (s.oneway) {
                    1 -> bearing(s.aLat, s.aLng, s.bLat, s.bLng)
                    -1 -> bearing(s.bLat, s.bLng, s.aLat, s.aLng)
                    else -> null
                }
            }
            val name = best?.name?.takeIf { it.isNotBlank() } ?: n.name
            // Junta radares duplicados (mesmo ponto)
            if (out.any { abs(it.lat - n.lat) < 0.00012 && abs(it.lng - n.lng) < 0.00012 && it.bearing == bearing }) continue
            out.add(SpeedCam(n.lat, n.lng, limit, bearing, name))
        }
        return out
    }

    private fun save(ctx: Context, cams: List<SpeedCam>) {
        val sb = StringBuilder()
        for (c in cams) {
            sb.append(String.format(Locale.US, "%.6f\t%.6f\t", c.lat, c.lng))
                .append(c.limit?.toString() ?: "").append('\t')
                .append(c.bearing?.let { String.format(Locale.US, "%.0f", it) } ?: "").append('\t')
                .append(c.name.replace('\t', ' ').replace('\n', ' ')).append('\n')
        }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(sb.toString())
        tmp.renameTo(file(ctx))
    }

    /** Distância (m) de um ponto até um trecho de rua. */
    private fun distToSeg(lat: Double, lng: Double, s: Seg): Double {
        val k = 111_320.0
        val c = cos(Math.toRadians(lat))
        val ax = (s.aLng - lng) * k * c
        val ay = (s.aLat - lat) * k
        val bx = (s.bLng - lng) * k * c
        val by = (s.bLat - lat) * k
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        return Math.sqrt(px * px + py * py)
    }

    private fun bearing(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Float {
        val c = cos(Math.toRadians(aLat))
        val deg = Math.toDegrees(atan2((bLng - aLng) * c, bLat - aLat))
        return ((deg + 360) % 360).toFloat()
    }
}
