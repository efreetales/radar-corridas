package com.taleco.radarcorridas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Bipes do alerta de radar.
 * Toca como "instrução de navegação" (o mesmo canal da voz do Waze):
 * sai no som do carro quando o celular está conectado e abaixa a música por um instante.
 * Usa o volume de mídia do celular.
 */
object Beeper {

    enum class Kind { RADAR_A_FRENTE, ACIMA, MULTA, VALETA, MARCADA, ERRO }

    private const val RATE = 44_100
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** (frequência Hz, duração ms); frequência 0 = silêncio. */
    private fun pattern(kind: Kind): List<Pair<Double, Int>> = when (kind) {
        Kind.RADAR_A_FRENTE -> listOf(880.0 to 160, 0.0 to 90, 1320.0 to 220)
        Kind.ACIMA -> listOf(1400.0 to 130, 0.0 to 70, 1400.0 to 130, 0.0 to 70, 1400.0 to 130)
        Kind.MULTA -> listOf(1200.0 to 260, 0.0 to 60, 900.0 to 260, 0.0 to 60, 600.0 to 420)
        Kind.VALETA -> listOf(520.0 to 200, 0.0 to 80, 520.0 to 200, 0.0 to 80, 390.0 to 320)
        Kind.MARCADA -> listOf(1050.0 to 90, 0.0 to 40, 1400.0 to 140)
        Kind.ERRO -> listOf(300.0 to 350)
    }

    fun play(ctx: Context, kind: Kind) {
        val app = ctx.applicationContext
        try {
            val samples = buildSamples(pattern(kind))
            stop(app)
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            t.write(samples, 0, samples.size)
            requestFocus(app)
            t.play()
            track = t
            val ms = samples.size * 1000L / RATE + 150L
            main.postDelayed({ if (track === t) stop(app) }, ms)
        } catch (e: Exception) {
            OfferLog.appendDiag(app, "BIPE: falhou (${e.javaClass.simpleName}: ${e.message})")
        }
    }

    private fun buildSamples(parts: List<Pair<Double, Int>>): ShortArray {
        val total = parts.sumOf { it.second } * RATE / 1000
        val out = ShortArray(total)
        var i = 0
        for ((freq, ms) in parts) {
            val n = ms * RATE / 1000
            val fade = min(n / 6, RATE / 200) // ~5 ms de rampa: evita estalo
            for (k in 0 until n) {
                if (i >= total) break
                if (freq > 0) {
                    val env = when {
                        k < fade -> k.toDouble() / fade
                        k > n - fade -> (n - k).toDouble() / fade
                        else -> 1.0
                    }
                    out[i] = (sin(2 * PI * freq * k / RATE) * env * 0.9 * Short.MAX_VALUE).toInt().toShort()
                }
                i++
            }
        }
        return out
    }

    private fun requestFocus(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .build()
                focus = req
                am.requestAudioFocus(req)
            }
        } catch (_: Exception) {
        }
    }

    private fun stop(ctx: Context) {
        track?.let { t ->
            try { t.stop() } catch (_: Exception) {}
            try { t.release() } catch (_: Exception) {}
        }
        track = null
        val am = ctx.getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus?.let { f -> try { am?.abandonAudioFocusRequest(f) } catch (_: Exception) {} }
        }
        focus = null
    }
}
