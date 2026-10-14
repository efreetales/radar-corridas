package com.taleco.radarcorridas

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Importa uma correção de corridas (arquivo .json preparado a partir das ofertas + GPS + resumo da Uber).
 * - "remover": ids de corridas a apagar (ex.: canceladas que nunca existiram)
 * - "corridas": linhas de corridas.csv; substituem as de mesmo id ou entram como novas
 * - "rotas": pontos de rotas.csv dessas corridas; trocam os pontos antigos delas e o trajeto "livre" no mesmo horário
 * Antes de mexer, guarda cópia dos arquivos (corridas_antes_correcao.csv / rotas_antes_correcao.csv).
 */
object Correcao {

    fun importar(ctx: Context, uri: Uri): String {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            ?: return "Não consegui abrir o arquivo"
        return importarTexto(ctx, text)
    }

    /** Volta corridas e rotas para como estavam antes da última correção. */
    fun desfazer(ctx: Context): String {
        val c = File(ctx.filesDir, "corridas_antes_correcao.csv"); val r = File(ctx.filesDir, "rotas_antes_correcao.csv")
        if (!c.exists() && !r.exists()) return "Não há correção para desfazer"
        if (c.exists()) { c.copyTo(TripLog.tripsFile(ctx), overwrite = true); c.delete() }
        if (r.exists()) { r.copyTo(TripLog.routesFile(ctx), overwrite = true); r.delete() }
        OfferLog.appendDiag(ctx, "CONFERÊNCIA: última correção desfeita")
        return "Correção desfeita"
    }

    fun podeDesfazer(ctx: Context) = File(ctx.filesDir, "corridas_antes_correcao.csv").exists()

    fun importarTexto(ctx: Context, text: String): String {
        val j = try { JSONObject(text) } catch (_: Exception) { return "Arquivo inválido" }
        if (j.optString("tipo") != "radar-correcao") return "Este arquivo não é uma correção do Radar"

        val remover = mutableSetOf<String>()
        j.optJSONArray("remover")?.let { a -> for (i in 0 until a.length()) remover.add(a.getString(i)) }
        val novas = mutableListOf<String>()
        j.optJSONArray("corridas")?.let { a -> for (i in 0 until a.length()) novas.add(a.getString(i)) }
        val pontos = mutableListOf<String>()
        j.optJSONArray("rotas")?.let { a -> for (i in 0 until a.length()) pontos.add(a.getString(i)) }
        val idsNovas = novas.map { it.substringBefore(';') }.toSet()
        val de = j.optString("rotas_de"); val ate = j.optString("rotas_ate")

        // corridas.csv
        val tf = TripLog.tripsFile(ctx)
        val header = j.optString("corridas_header").ifBlank { null }
        val antigas = if (tf.exists()) tf.readLines() else emptyList()
        if (tf.exists()) tf.copyTo(File(ctx.filesDir, "corridas_antes_correcao.csv"), overwrite = true)
        val cab = antigas.firstOrNull()?.takeIf { it.startsWith("id;") } ?: header ?: return "Sem cabeçalho de corridas"
        val ficam = antigas.drop(1).filter { it.isNotBlank() && it.substringBefore(';') !in remover && it.substringBefore(';') !in idsNovas }
        tf.writeText(cab + "\n" + (ficam + novas).sortedBy { it.substringBefore(';') }.joinToString("\n") + "\n")

        // rotas.csv
        val rf = TripLog.routesFile(ctx)
        if (pontos.isNotEmpty()) {
            val linhas = if (rf.exists()) rf.readLines() else listOf("id;data_hora;lat;lng;fase")
            if (rf.exists()) rf.copyTo(File(ctx.filesDir, "rotas_antes_correcao.csv"), overwrite = true)
            val rcab = linhas.firstOrNull()?.takeIf { it.startsWith("id;") } ?: "id;data_hora;lat;lng;fase"
            val idsComPontos = pontos.map { it.substringBefore(';') }.toSet()
            val janelas = novas.filter { it.substringBefore(';') in idsComPontos }.mapNotNull { l -> val c = l.split(';'); if (c.size > 10 && c[6].isNotBlank() && c[10].isNotBlank()) c[6] to c[10] else null }
            val rficam = linhas.drop(1).filter { l ->
                if (l.isBlank()) return@filter false
                val id = l.substringBefore(';')
                if (id in remover || id in idsComPontos) return@filter false
                if (id.startsWith("livre")) {
                    val t = l.split(';').getOrNull(1) ?: return@filter true
                    // o trajeto "livre" que na verdade era corrida sai (a correção traz esses pontos como corrida)
                    if (t >= de && t <= ate && janelas.any { (a, b) -> t >= a && t <= b }) return@filter false
                }
                true
            }
            rf.writeText(rcab + "\n" + (rficam + pontos).joinToString("\n") + "\n")
        }
        // resumo(s) da sessão da Uber
        j.optJSONArray("sessoes")?.let { a ->
            val sf = File(ctx.filesDir, "sessoes_uber.csv")
            if (!sf.exists()) sf.writeText("periodo;valor;concluidas;oferecidas;lido_em\n")
            val atual = sf.readText()
            for (i in 0 until a.length()) { val l = a.getString(i); if (!atual.contains(l.substringBefore(';'))) sf.appendText(l + "\n") }
        }
        val desc = j.optString("descricao")
        return "Correção importada: ${novas.size} corridas" + (if (remover.isNotEmpty()) ", ${remover.size} removidas" else "") +
            (if (desc.isNotBlank()) "\n$desc" else "")
    }
}
