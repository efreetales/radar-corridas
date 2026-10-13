package com.taleco.radarcorridas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
                if (st == TextToSpeech.SUCCESS) {
                    t.language = Locale("pt", "BR")
                    t.setSpeechRate(0.95f)
                    t.setAudioAttributes(ATTR)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { soltarFoco(app) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) { soltarFoco(app) }
                    })
                    pronto = true
                    fila.forEach { dizer(app, it) }
                    fila.clear()
                }
            }
        }
        if (pronto) dizer(app, texto) else fila.add(texto)
    }

    private fun dizer(ctx: Context, texto: String) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am != null) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(ATTR).build()
            foco = r
            am.requestAudioFocus(r)
        }
        val p = Bundle()
        tts?.speak(texto, TextToSpeech.QUEUE_ADD, p, "radar-" + System.currentTimeMillis())
    }

    private fun soltarFoco(ctx: Context) {
        val r = foco ?: return
        if (tts?.isSpeaking == true) return
        ctx.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(r)
        foco = null
    }

    /** Só o primeiro nome, como aparece na Uber ("Gabriel", "Ana Paula" -> "Ana"). */
    private fun nome(n: String?) = n?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.length >= 2 }

    /** "Avenida 9 de Julho, 2040, Bela Vista" -> "Avenida 9 de Julho" */
    private fun rua(d: String?): String? = d?.substringBefore(",")?.substringBefore(" - ")?.trim()?.takeIf { it.length >= 4 }

    fun inicio(ctx: Context, passageiro: String?, destino: String?) {
        val n = nome(passageiro)
        val r = rua(destino)
        falar(ctx, (if (n != null) "Olá, $n! " else "Olá! ") + "Vamos começar. " +
            (if (r != null) "Estamos indo para $r. " else "Estamos indo para o seu destino. ") + "Boa viagem!")
    }

    fun chegada(ctx: Context, passageiro: String?) {
        val n = nome(passageiro)
        falar(ctx, "Chegamos" + (if (n != null) ", $n" else "") + "! Espero que tenha gostado da viagem. " +
            "Por favor, não esqueça de avaliar a viagem no aplicativo da Uber. Obrigado e até a próxima!")
    }
}
