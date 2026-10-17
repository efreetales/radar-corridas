package com.taleco.radarcorridas

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import java.io.File
import kotlin.math.log10

/**
 * Toca as falas com o passageiro (voz do celular gravada em arquivo, ou voz de estúdio) com a
 * "altura da voz" escolhida no app. Acima de 100% o próprio som da fala é reforçado (LoudnessEnhancer),
 * então sobe mesmo quando o volume do celular já está no máximo e o carro (Android Auto) controla o resto.
 */
object Tocador {
    private var player: MediaPlayer? = null
    private var reforco: LoudnessEnhancer? = null   // guardado aqui: solto, o Android desliga o efeito
    private var foco: AudioFocusRequest? = null

    /** Altura 100% = como sai do motor de voz. 200% ≈ +6 dB, 300% ≈ +9,5 dB, 400% = +12 dB. */
    fun ganhoMb(pct: Int): Int = if (pct <= 100) 0 else (2000 * log10(pct / 100.0)).toInt()

    fun tocar(ctx: Context, f: File, fim: () -> Unit = {}): Boolean {
        val app = ctx.applicationContext
        parar(app)
        return try {
            val am = app.getSystemService(AudioManager::class.java)
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(VozCanal.attr(app)).build()
            am?.requestAudioFocus(r); foco = r
            val pct = Prefs(app).voiceGain
            val mp = MediaPlayer()
            mp.setAudioAttributes(VozCanal.attr(app))
            mp.setDataSource(f.path)
            mp.setOnCompletionListener { if (player === it) parar(app); fim() }
            mp.setOnErrorListener { p, _, _ -> if (player === p) parar(app); fim(); true }
            mp.prepare()
            if (pct < 100) mp.setVolume(pct / 100f, pct / 100f)
            val g = ganhoMb(pct)
            if (g > 0) reforco = try { LoudnessEnhancer(mp.audioSessionId).apply { setTargetGain(g); enabled = true } } catch (_: Exception) { null }
            player = mp
            mp.start()
            OfferLog.appendDiag(app, "VOZ: tocando com altura $pct% (${VozCanal.nome(app)})")
            true
        } catch (e: Exception) {
            OfferLog.appendDiag(app, "VOZ: não consegui tocar o áudio (${e.javaClass.simpleName})")
            parar(app)
            false
        }
    }

    fun parar(ctx: Context) {
        try { reforco?.release() } catch (_: Exception) { }
        reforco = null
        player?.let { try { it.stop() } catch (_: Exception) { }; try { it.release() } catch (_: Exception) { } }
        player = null
        foco?.let { ctx.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        foco = null
    }

    /** Para o diagnóstico. */
    fun estado(ctx: Context): String = "[${VozCanal.nome(ctx)}, altura ${Prefs(ctx).voiceGain}%]"
}
