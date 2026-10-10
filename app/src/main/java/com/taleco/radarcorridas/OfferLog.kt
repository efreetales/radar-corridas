package com.taleco.radarcorridas

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Guarda cada oferta vista num CSV (para alimentar o Zonas depois)
 * e, no modo diagnóstico, os textos lidos da tela.
 */
object OfferLog {

    private const val HEADER =
        "data_hora;app;categoria;valor;km_total;min_total;km_busca;min_busca;km_viagem;min_viagem;nota;rs_km;rs_hora;lucro;veredito;origem;destino;" +
            "motorista_lat;motorista_lng;origem_lat;origem_lng"
    private const val DIAG_MAX_BYTES = 800_000L

    fun offersFile(ctx: Context) = File(ctx.filesDir, "ofertas.csv")
    fun diagFile(ctx: Context) = File(ctx.filesDir, "diagnostico.txt")

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", PT_BR).format(Date())

    private fun n(v: Double?): String =
        if (v == null) "" else String.format(PT_BR, "%.2f", v)

    private fun q(s: String?): String =
        if (s == null) "" else "\"" + s.replace("\"", "'") + "\""

    private fun c(v: Double?): String = if (v == null) "" else String.format(java.util.Locale.US, "%.6f", v)

    /** Se o arquivo é de uma versão antiga (sem as colunas novas), guarda com outro nome. */
    private fun ensureHeader(f: File) {
        if (f.exists()) {
            val first = try { f.bufferedReader().use { it.readLine() } } catch (_: Exception) { null }
            if (first == HEADER) return
            f.renameTo(File(f.parentFile, "ofertas_antigas.csv"))
        }
        f.writeText(HEADER + "\n")
    }

    fun append(ctx: Context, eval: Evaluation, driverAt: LatLng? = null, originAt: LatLng? = null) {
        try {
            val o = eval.offer
            val f = offersFile(ctx)
            ensureHeader(f)
            val perKm = if (o.totalKm > 0) o.price / o.totalKm else null
            val perHour = if (o.totalMin > 0) o.price / (o.totalMin / 60.0) else null
            val line = listOf(
                now(), o.app, q(o.category), n(o.price), n(o.totalKm), n(o.totalMin),
                n(o.pickupKm), n(o.pickupMin), n(o.tripKm), n(o.tripMin), n(o.rating),
                n(perKm), n(perHour), n(eval.profit), eval.overall.name, q(o.origin), q(o.destination),
                c(driverAt?.lat), c(driverAt?.lng), c(originAt?.lat), c(originAt?.lng)
            ).joinToString(";")
            f.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }

    fun appendDiag(ctx: Context, text: String) {
        try {
            val f = diagFile(ctx)
            if (f.exists() && f.length() > DIAG_MAX_BYTES) f.writeText("")
            f.appendText("[" + now() + "]\n" + text + "\n\n")
        } catch (_: Exception) {
        }
    }

    fun offerCount(ctx: Context): Int {
        val f = offersFile(ctx)
        if (!f.exists()) return 0
        return try {
            (f.readLines().count { it.isNotBlank() } - 1).coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    /** Envia vários arquivos de uma vez (ofertas, corridas e rotas). */
    fun shareAll(ctx: Context, files: List<File>, title: String): Boolean {
        val existing = files.filter { it.exists() && it.length() > 0L }
        if (existing.isEmpty()) return false
        val uris = ArrayList(existing.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("text/csv")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, title))
        return true
    }

    fun share(ctx: Context, file: File, mime: String, title: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, title))
        return true
    }
}
