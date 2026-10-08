package com.taleco.radarcorridas

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * Envia para a nuvem (Supabase) tudo o que o Radar grava no celular:
 * ofertas, corridas, rotas do GPS e o diagnóstico.
 *
 * - Lê só o que é novo em cada arquivo (guarda até onde já enviou).
 * - Sem internet, tenta de novo no próximo ciclo; nada se perde.
 * - Cada linha leva uma "impressão digital" (client_id): se for enviada duas vezes,
 *   o banco descarta a repetida.
 * - A chave do app só consegue INSERIR. Ler e apagar não é possível com ela.
 */
object CloudSync {

    private const val BASE_URL = "https://mheprhibuknmnnbcisxa.supabase.co/rest/v1/"
    // Chave pública (anon). No banco, ela só tem permissão de inserir nas tabelas radar_*.
    private const val API_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1oZXByaGlidWtubW5uYmNpc3hhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM0OTA1MDQsImV4cCI6MjA3OTA2NjUwNH0.WmGHtGmVCDxVwNmnT18lWkS5cRrZRohc_ytI1GoLKHw"

    /** Intervalo entre envios automáticos. */
    const val INTERVAL_MS = 3 * 60 * 1000L
    private const val BATCH_ROWS = 400
    private const val DIAG_CHUNK_BYTES = 150_000

    private val NUMERIC = setOf(
        "valor", "km_total", "min_total", "km_busca", "min_busca", "km_viagem", "min_viagem", "nota",
        "rs_km", "rs_hora", "lucro", "km_oferta", "min_oferta", "min_ate_embarque", "min_espera_passageiro",
        "min_total", "km_ate_embarque", "rs_hora_real"
    )
    private val COORD = setOf(
        "motorista_lat", "motorista_lng", "origem_lat", "origem_lng", "destino_lat", "destino_lng", "lat", "lng"
    )

    private class Table(val name: String, val columns: Set<String>, val rename: Map<String, String> = emptyMap())

    private val OFERTAS = Table(
        "radar_ofertas",
        setOf("data_hora", "app", "categoria", "valor", "km_total", "min_total", "km_busca", "min_busca", "km_viagem",
            "min_viagem", "nota", "rs_km", "rs_hora", "lucro", "veredito", "origem", "destino",
            "motorista_lat", "motorista_lng", "origem_lat", "origem_lng")
    )
    private val CORRIDAS = Table(
        "radar_corridas",
        setOf("id", "app", "categoria", "valor", "km_oferta", "min_oferta", "aceite", "chegada_embarque",
            "saida_embarque", "chegada_destino", "fim", "min_ate_embarque", "min_espera_passageiro", "min_viagem",
            "min_total", "km_ate_embarque", "km_viagem", "rs_hora_real", "status", "origem", "destino",
            "origem_lat", "origem_lng", "destino_lat", "destino_lng")
    )
    private val ROTAS = Table("radar_rotas", setOf("id", "data_hora", "lat", "lng", "fase"))

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    private fun sp(ctx: Context) = ctx.getSharedPreferences("nuvem", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean("ligado", true)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ligado", v).apply()
    fun lastOkAt(ctx: Context) = sp(ctx).getLong("ultimo_ok", 0L)
    fun lastError(ctx: Context): String? = sp(ctx).getString("ultimo_erro", null)

    /** Texto curto para a tela de configuração. */
    fun status(ctx: Context): String {
        if (!enabled(ctx)) return "Envio para a nuvem desligado"
        val ok = lastOkAt(ctx)
        val err = lastError(ctx)
        val fmt = SimpleDateFormat("dd/MM HH:mm", PT_BR)
        return when {
            err != null && ok == 0L -> "Ainda não enviou ($err)"
            err != null -> "Último envio: ${fmt.format(Date(ok))} — agora sem conexão, tenta de novo sozinho"
            ok == 0L -> "Ainda não enviou"
            else -> "Tudo enviado. Último envio: ${fmt.format(Date(ok))}"
        }
    }

    /** Envia o que houver de novo, em segundo plano. [onDone] volta na thread principal. */
    fun syncNow(ctx: Context, onDone: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (!enabled(app) || running) {
            onDone?.let { main.post { it(false) } }
            return
        }
        running = true
        executor.execute {
            val ok = try {
                syncCsv(app, OfferLog.offersFile(app), OFERTAS)
                syncCsv(app, TripLog.tripsFile(app), CORRIDAS)
                syncCsv(app, TripLog.routesFile(app), ROTAS)
                for (f in OfferLog.diagFiles(app)) syncDiag(app, f)
                sp(app).edit().putLong("ultimo_ok", System.currentTimeMillis()).remove("ultimo_erro").apply()
                true
            } catch (e: Exception) {
                sp(app).edit().putString("ultimo_erro", e.message ?: e.javaClass.simpleName).apply()
                false
            } finally {
                running = false
            }
            onDone?.let { main.post { it(ok) } }
        }
    }

    // ---------- Controle de até onde já foi enviado ----------

    /**
     * Chave de "até onde já enviei". O diagnóstico é identificado pela 1ª linha, porque muda
     * de nome quando enche (vira diagnostico_anterior.txt) e o envio continua de onde parou.
     */
    private fun offsetKey(kind: String, identity: String) = "env_" + sha1("$kind|$identity")

    private fun firstLine(f: File): String? =
        try { f.bufferedReader().use { it.readLine() } } catch (_: Exception) { null }

    /** Lê do byte [from] até o fim, devolvendo só linhas completas e onde elas terminam. */
    private fun readFrom(f: File, from: Long, maxBytes: Int): Pair<String, Long> {
        RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            if (from >= len) return "" to from
            val size = minOf(len - from, maxBytes.toLong()).toInt()
            val buf = ByteArray(size)
            raf.seek(from)
            raf.readFully(buf)
            val lastNl = buf.lastIndexOf('\n'.code.toByte())
            if (lastNl < 0) return "" to from
            return String(buf, 0, lastNl + 1, Charsets.UTF_8) to (from + lastNl + 1)
        }
    }

    // ---------- CSV ----------

    private fun syncCsv(ctx: Context, f: File, table: Table) {
        if (!f.exists() || f.length() == 0L) return
        val header = firstLine(f) ?: return
        val cols = splitCsv(header)
        val key = offsetKey("csv", f.name)
        val prefs = sp(ctx)
        var offset = prefs.getLong(key, 0L)
        if (offset > f.length()) offset = 0L        // arquivo recriado
        if (offset == 0L) offset = (header.toByteArray(Charsets.UTF_8).size + 1).toLong()

        while (true) {
            val (chunk, end) = readFrom(f, offset, 512 * 1024)
            if (chunk.isEmpty()) break
            val lines = chunk.split('\n').filter { it.isNotBlank() }
            var i = 0
            while (i < lines.size) {
                val batch = lines.subList(i, minOf(i + BATCH_ROWS, lines.size))
                val arr = JSONArray()
                val seen = HashSet<String>()
                for (line in batch) {
                    val id = sha1(line)
                    if (!seen.add(id)) continue
                    arr.put(toJson(id, cols, splitCsv(line), table))
                }
                post(table.name, arr)
                i += BATCH_ROWS
            }
            offset = end
            prefs.edit().putLong(key, offset).apply()
        }
    }

    private fun toJson(id: String, cols: List<String>, values: List<String>, table: Table): JSONObject {
        val o = JSONObject()
        o.put("client_id", id)
        for ((idx, c) in cols.withIndex()) {
            val col = table.rename[c] ?: c
            if (col !in table.columns) continue
            val raw = values.getOrNull(idx)?.trim().orEmpty()
            when {
                raw.isEmpty() -> o.put(col, JSONObject.NULL)
                col in NUMERIC -> o.put(col, raw.replace(',', '.').toDoubleOrNull() ?: JSONObject.NULL)
                col in COORD -> o.put(col, raw.toDoubleOrNull() ?: JSONObject.NULL)
                else -> o.put(col, raw)
            }
        }
        return o
    }

    /** Separa uma linha "a;\"b c\";d" respeitando as aspas. */
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == ';' && !quoted -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    // ---------- Diagnóstico ----------

    private val STAMP = Regex("""\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")

    private fun syncDiag(ctx: Context, f: File) {
        if (!f.exists() || f.length() == 0L) return
        val identity = firstLine(f) ?: return
        val key = offsetKey("diag", identity)
        val prefs = sp(ctx)
        var offset = prefs.getLong(key, 0L)
        if (offset > f.length()) offset = 0L
        while (true) {
            val (chunk, end) = readFrom(f, offset, DIAG_CHUNK_BYTES)
            if (chunk.isEmpty()) break
            val stamps = STAMP.findAll(chunk).map { it.groupValues[1] }.toList()
            val row = JSONObject()
                .put("client_id", sha1(identity + "|" + offset + "|" + chunk))
                .put("arquivo", f.name)
                .put("inicio", stamps.firstOrNull() ?: JSONObject.NULL)
                .put("fim", stamps.lastOrNull() ?: JSONObject.NULL)
                .put("texto", chunk)
            post("radar_diagnostico", JSONArray().put(row))
            offset = end
            prefs.edit().putLong(key, offset).apply()
        }
    }

    // ---------- Rede ----------

    private fun post(table: String, rows: JSONArray) {
        if (rows.length() == 0) return
        val conn = URL(BASE_URL + table).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("apikey", API_KEY)
            conn.setRequestProperty("Authorization", "Bearer $API_KEY")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.outputStream.use { it.write(rows.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val msg = try { conn.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                throw IllegalStateException("erro $code em $table: ${msg?.take(160)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
