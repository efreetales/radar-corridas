package com.taleco.radarcorridas

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diário leve das telas da Uber, para conferir as corridas depois sem depender do diagnóstico:
 *   ENCONTRO  (aceitou, indo buscar)      nome · categoria · endereço de embarque
 *   EMBARQUE  (chegou no local)           nome · endereço
 *   DESTINO   (tocou em Iniciar)          nome · endereço de destino
 *   AVALIACAO ("Como foi a viagem?")      nome
 *   CANCELADA (a Uber falou em cancelamento)
 * Arquivo eventos_uber.csv: data_hora;evento;nome;categoria;endereco. Só grava quando algo muda.
 */
object UberEventos {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val visto = HashMap<String, Long>()   // "EVENTO|nome|endereço" -> quando gravou
    private const val REPETE_MS = 30 * 60_000L

    fun file(ctx: Context) = File(ctx.filesDir, "eventos_uber.csv")

    // "Rua X, 840 - Bairro" ou "Alameda Y, 909 - Bairro, São Paulo - SP, 01408-001" (não a linha "São Paulo - SP, 05605-070")
    private val RUA = Regex("^[^\\d,][^,]{2,}, \\d+")
    private fun endereco(tt: List<String>): String =
        tt.firstOrNull { RUA.containsMatchIn(it) && !it.startsWith("São Paulo") && !it.startsWith("Perto de ") && !it.startsWith("R$") }
            ?: tt.firstOrNull { it.startsWith("Perto de ") }?.removePrefix("Perto de ")?.trim()
            ?: ""

    fun registrar(ctx: Context, texts: List<String>) {
        val tt = texts.map { it.trim() }.filter { it.isNotEmpty() }
        val meet = tt.firstOrNull { it.startsWith("Encontro com ") }?.removePrefix("Encontro com ")?.trim()
        val dest = tt.firstOrNull { it.startsWith("Destino de ") }?.removePrefix("Destino de ")?.trim()
        val cat = tt.firstOrNull { it.startsWith("•") }?.removePrefix("•")?.trim()
            ?: tt.firstOrNull { it.startsWith("Iniciar ") }?.removePrefix("Iniciar ")?.trim() ?: ""
        val ri = tt.indexOfFirst { it.startsWith("Como foi a viagem") }
        val rated = if (ri >= 0) tt.getOrNull(ri + 1) else null
        val atPickup = tt.any { it.contains("Aguardando", ignoreCase = true) || it.startsWith("Perto de ") }
        // "Confira o U-Código e tente de novo. 4 tentativas restantes antes de a viagem ser cancelada" não é cancelamento
        val cancel = tt.firstOrNull { (it.contains("cancelou", ignoreCase = true) || it.contains("cancelada", ignoreCase = true)) &&
            !it.contains("U-Código", ignoreCase = true) && !it.contains("tentativas", ignoreCase = true) }

        if (rated != null) gravar(ctx, "AVALIACAO", rated, "", "")
        if (dest != null) gravar(ctx, "DESTINO", dest, cat, endereco(tt))
        else if (meet != null) {
            if (atPickup) gravar(ctx, "EMBARQUE", meet, cat, endereco(tt))
            else gravar(ctx, "ENCONTRO", meet, cat, endereco(tt))
        }
        if (cancel != null) gravar(ctx, "CANCELADA", meet ?: dest ?: "", "", cancel.take(80))
    }

    private fun gravar(ctx: Context, ev: String, nome: String, cat: String, end: String) {
        val now = System.currentTimeMillis()
        // grava de novo só se mudou o endereço (o destino aparece alguns segundos depois de "Destino de")
        val k = "$ev|$nome|$end"
        val last = visto[k]
        if (last != null && now - last < REPETE_MS) return
        if (end.isEmpty() && visto.any { it.key.startsWith("$ev|$nome|") && it.value > now - REPETE_MS }) return
        val n = visto.count { it.key.startsWith("$ev|$nome|") && it.value > now - REPETE_MS }
        if (n >= 4) return
        if (visto.size > 300) visto.entries.removeIf { now - it.value > REPETE_MS }
        visto[k] = now
        try {
            val f = file(ctx)
            if (!f.exists()) f.writeText("data_hora;evento;nome;categoria;endereco\n")
            fun q(s: String) = "\"" + s.replace("\"", "'").replace(";", ",") + "\""
            f.appendText("${fmt.format(Date(now))};$ev;${q(nome)};${q(cat)};${q(end)}\n")
        } catch (_: Exception) {
        }
    }
}
