package com.taleco.radarcorridas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Fala com o passageiro pelo som do celular (e do carro, quando o celular está ligado nele por
 * Bluetooth ou Android Auto). Usa o canal de "navegação": abaixa a música enquanto fala, como o Waze.
 */
object Voz {
    private var tts: TextToSpeech? = null
    private var pronto = false
    private val fila = mutableListOf<String>()
    private var foco: AudioFocusRequest? = null

    private val ATTR = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

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
                    t.setAudioAttributes(ATTR)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { soltarFoco(app) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) { soltarFoco(app); OfferLog.appendDiag(app, "VOZ: erro do motor de voz do celular") }
                        override fun onError(id: String?, code: Int) { soltarFoco(app); OfferLog.appendDiag(app, "VOZ: erro do motor de voz do celular ($code)") }
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

    private fun dizer(ctx: Context, texto: String) {
        if (texto.isBlank()) return
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am != null) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(ATTR).build()
            foco = r
            am.requestAudioFocus(r)
        }
        Volume.subir(ctx)
        val p = Bundle()
        p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        tts?.speak(texto, TextToSpeech.QUEUE_ADD, p, "radar-" + System.currentTimeMillis())
    }

    private fun soltarFoco(ctx: Context) {
        val r = foco ?: return
        if (tts?.isSpeaking == true) return
        ctx.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(r)
        foco = null
        Volume.voltar(ctx)
    }

    /** Só o primeiro nome, como aparece na Uber ("Gabriel", "Ana Paula" -> "Ana"). */
    private fun nome(n: String?) = n?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.length >= 2 }

    /** "Avenida 9 de Julho, 2040, Bela Vista" -> "Avenida 9 de Julho" */
    private fun rua(d: String?): String? = d?.substringBefore(",")?.substringBefore(" - ")?.trim()?.takeIf { it.length >= 4 }

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
