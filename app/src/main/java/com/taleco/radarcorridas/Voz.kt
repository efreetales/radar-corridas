package com.taleco.radarcorridas

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Fala com o passageiro pelo som do celular (e do carro, quando o celular está ligado nele por
 * Bluetooth ou Android Auto). A frase é gravada em arquivo e tocada pelo Tocador, que aplica a
 * "altura da voz" (reforço acima de 100%) e abaixa a música enquanto fala.
 */
object Voz {
    private var tts: TextToSpeech? = null
    private var pronto = false
    private val fila = mutableListOf<String>()
    private val arquivos = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())


    fun falar(ctx: Context, texto: String) {
        val app = ctx.applicationContext
        if (tts == null) {
            tts = TextToSpeech(app) { st ->
                val t = tts ?: return@TextToSpeech
                if (st != TextToSpeech.SUCCESS) OfferLog.appendDiag(app, "VOZ: motor de voz do celular não iniciou ($st)")
                if (st == TextToSpeech.SUCCESS) {
                    t.language = Locale("pt", "BR")
                    escolherVoz(app, t)
                    t.setSpeechRate(1.0f)
                    t.setPitch(1.0f)
                    t.setAudioAttributes(VozCanal.attr(app))
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        // a frase terminou de ser gravada em arquivo: toca com a altura escolhida
                        override fun onDone(id: String?) { val f = arquivos.remove(id ?: return) ?: return; main.post { if (!Tocador.tocar(app, f) { f.delete() }) f.delete() } }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) { erro(app, id, null) }
                        override fun onError(id: String?, code: Int) { erro(app, id, code) }
                    })
                    pronto = true
                    fila.forEach { dizer(app, it) }
                    fila.clear()
                }
            }
        }
        if (pronto) dizer(app, texto) else fila.add(texto)
    }

    /** Vozes em português do Brasil instaladas no celular, da mais natural para a mais simples. */
    fun vozes(): List<Voice> = (try { tts?.voices?.toList() } catch (_: Exception) { null } ?: emptyList())
        .filter { it.locale.language == "pt" && (it.locale.country == "BR" || it.locale.country.isEmpty()) && !it.features.contains("notInstalled") }
        .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })

    /** Usa a voz escolhida pelo motorista; se não escolheu, a de melhor qualidade. */
    private fun escolherVoz(ctx: Context, t: TextToSpeech) {
        val salva = Prefs(ctx).voiceName
        val v = vozes().firstOrNull { it.name == salva } ?: vozes().firstOrNull() ?: return
        try { t.voice = v } catch (_: Exception) { }
    }

    /** Troca a voz e fala um exemplo com ela. */
    fun usarVoz(ctx: Context, nome: String) {
        Prefs(ctx).voiceName = nome
        tts?.let { t -> vozes().firstOrNull { it.name == nome }?.let { try { t.voice = it } catch (_: Exception) { } } }
        tts?.stop()
        inicio(ctx, "Ana", "Avenida Paulista, 1000")
    }

    /** Prepara o motor de voz (para a lista de vozes ficar disponível). */
    fun preparar(ctx: Context, pronto: () -> Unit) {
        if (this.pronto) { pronto(); return }
        fila.clear()
        falar(ctx.applicationContext, "")
        Thread { var n = 0; while (!this.pronto && n++ < 40) Thread.sleep(100)
            android.os.Handler(android.os.Looper.getMainLooper()).post(pronto) }.start()
    }

    /** Grava a frase em arquivo (o Tocador toca quando ficar pronta, com o reforço de altura). */
    private fun dizer(ctx: Context, texto: String) {
        if (texto.isBlank()) return
        val t = tts ?: return
        val id = "radar-" + System.currentTimeMillis()
        val f = java.io.File(java.io.File(ctx.cacheDir, "fala").apply { mkdirs() }, "$id.wav")
        arquivos[id] = f
        val r = try { t.synthesizeToFile(texto, Bundle(), f, id) } catch (_: Exception) { TextToSpeech.ERROR }
        if (r != TextToSpeech.SUCCESS) { arquivos.remove(id); falarDireto(ctx, texto) }
    }

    /** Se não der para gravar a frase, fala direto pelo motor de voz (sem o reforço de altura). */
    private fun falarDireto(ctx: Context, texto: String) {
        try { tts?.setAudioAttributes(VozCanal.attr(ctx)) } catch (_: Exception) { }
        val p = Bundle()
        p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        tts?.speak(texto, TextToSpeech.QUEUE_ADD, p, "direto-" + System.currentTimeMillis())
    }

    private fun erro(ctx: Context, id: String?, code: Int?) {
        OfferLog.appendDiag(ctx, "VOZ: erro do motor de voz do celular" + (if (code != null) " ($code)" else ""))
        arquivos.remove(id ?: return)?.delete()
    }

    /** Só o primeiro nome, como aparece na Uber ("Gabriel", "Ana Paula" -> "Ana"). */
    private fun nome(n: String?) = n?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.length >= 2 }

    /** "Avenida 9 de Julho, 2040, Bela Vista" -> "Avenida 9 de Julho"; "R. Augusta" -> "Rua Augusta" (a voz não lê "R"). */
    private fun rua(d: String?): String? = d?.substringBefore(",")?.substringBefore(" - ")?.trim()?.let { porExtenso(it) }?.takeIf { it.length >= 4 }

    private val ABREV = listOf(
        "R" to "Rua", "Av" to "Avenida", "Al" to "Alameda", "Pç" to "Praça", "Pça" to "Praça", "Pc" to "Praça", "Pca" to "Praça",
        "Tv" to "Travessa", "Trav" to "Travessa", "Estr" to "Estrada", "Est" to "Estrada", "Rod" to "Rodovia", "Lgo" to "Largo", "Lg" to "Largo",
        "Vd" to "Viaduto", "Pq" to "Parque", "Jd" to "Jardim", "Vl" to "Vila", "Res" to "Residencial",
        "Dr" to "Doutor", "Dra" to "Doutora", "Prof" to "Professor", "Profa" to "Professora", "Eng" to "Engenheiro", "Brg" to "Brigadeiro",
        "Brig" to "Brigadeiro", "Cel" to "Coronel", "Gen" to "General", "Cap" to "Capitão", "Maj" to "Major", "Ten" to "Tenente",
        "Mal" to "Marechal", "Alm" to "Almirante", "Des" to "Desembargador", "Pres" to "Presidente", "Gov" to "Governador",
        "Sen" to "Senador", "Dep" to "Deputado", "Ver" to "Vereador", "Pe" to "Padre", "Fr" to "Frei", "Sta" to "Santa", "Sto" to "Santo",
        "S" to "São", "N. Sra" to "Nossa Senhora", "Nsa" to "Nossa", "Sra" to "Senhora", "Cons" to "Conselheiro", "Com" to "Comendador",
        "Visc" to "Visconde", "Bar" to "Barão", "Dq" to "Duque", "Min" to "Ministro", "Arq" to "Arquiteto", "Dom" to "Dom"
    )

    /** Troca as abreviações dos endereços pelas palavras inteiras. "R" sozinho só vira "Rua" no começo. */
    fun porExtenso(nome: String): String {
        var t = nome.trim()
        // tipo do logradouro no começo: "R.", "R ", "Av.", "Al."…
        Regex("^(R|Av|Al|Pç|Pça|Pc|Pca|Tv|Trav|Estr|Est|Rod|Lgo|Lg|Vd)\\.?\\s+", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            val k = m.groupValues[1]
            val nomeLongo = ABREV.firstOrNull { it.first.equals(k, ignoreCase = true) }?.second
            if (nomeLongo != null) t = nomeLongo + " " + t.substring(m.range.last + 1)
        }
        // títulos no meio do nome, só com ponto: "Av. Brg. Faria Lima" -> "Avenida Brigadeiro Faria Lima"
        for ((ab, longo) in ABREV) {
            if (ab.length < 2) continue
            t = t.replace(Regex("(?<=^|\\s)" + Regex.escape(ab) + "\\.(?=\\s|$)", RegexOption.IGNORE_CASE), longo)
        }
        return t.replace(Regex("\\s{2,}"), " ").trim()
    }

    const val PADRAO_INICIO = "Oi, {nome}, tudo bem? Vamos lá, nosso destino é {rua}. Se precisar de alguma coisa, é só me falar. Boa viagem!"
    const val PADRAO_CHEGADA = "Pronto, {nome}, chegamos! Obrigado pela companhia, espero que tenha curtido a viagem. Se puder, deixa sua avaliação no aplicativo da Uber. Até a próxima!"

    /** Troca {nome} e {rua}; se faltar o nome tira só ele, se faltar a rua tira a frase do destino. */
    fun montar(modelo: String, passageiro: String?, destino: String?): String {
        var t = modelo
        val n = nome(passageiro)
        t = if (n != null) t.replace("{nome}", n) else t.replace(Regex(",?\\s*\\{nome\\}"), "")
        val r = rua(destino)
        t = if (r != null) t.replace("{rua}", r) else t.replace(Regex("[^.!?]*\\{rua\\}[^.!?]*[.!?]?\\s*"), " ")   // sem destino: tira a frase do destino
        return t.replace(Regex("\\s+([,.!?])"), "$1").replace(Regex("\\s{2,}"), " ").trim()
    }

    fun textoInicio(ctx: Context, passageiro: String?, destino: String?) =
        montar(Prefs(ctx).voiceStart.ifBlank { PADRAO_INICIO }, passageiro, destino)

    fun textoChegada(ctx: Context, passageiro: String?) =
        montar(Prefs(ctx).voiceEnd.ifBlank { PADRAO_CHEGADA }, passageiro, null)

    /** Voz de estúdio quando ligada (cai para a voz do celular se faltar internet ou crédito). */
    private fun fala(ctx: Context, texto: String) {
        if (VozEstudio.ativa(ctx)) VozEstudio.falar(ctx, texto) { falar(ctx, texto) } else falar(ctx, texto)
    }

    /** No aceite: já gera as duas falas da corrida, para tocarem na hora sem esperar a internet. */
    fun adiantar(ctx: Context, passageiro: String?, destino: String?) {
        VozEstudio.preparar(ctx, textoInicio(ctx, passageiro, destino))
        VozEstudio.preparar(ctx, textoChegada(ctx, passageiro))
    }

    fun inicio(ctx: Context, passageiro: String?, destino: String?) = fala(ctx, textoInicio(ctx, passageiro, destino))

    fun chegada(ctx: Context, passageiro: String?) = fala(ctx, textoChegada(ctx, passageiro))
}
