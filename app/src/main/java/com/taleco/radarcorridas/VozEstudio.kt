package com.taleco.radarcorridas

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Voz de estúdio (ElevenLabs, modelo v4, que entende o contexto da frase e fala com naturalidade).
 * A chave é do próprio motorista, digitada no app e guardada só no celular.
 * Cada fala fica guardada em cache: a mesma frase não é gerada (nem cobrada) duas vezes.
 */
object VozEstudio {
    /** Vozes brasileiras escolhidas para conversar com o passageiro. */
    val VOZES = listOf(
        Triple("NfX2pe1EoEnKaP3Fqwrx", "Diego", "masculina, calma e amigável"),
        Triple("mmTLZX6tO1YdiYVoUVN5", "Adriane", "feminina, calorosa"),
        Triple("jEPP1Cc87U474IKg6R8j", "Lu", "feminina, jovem e simpática")
    )
    private const val MODELO = "eleven_v4"
    private val rede = Executors.newSingleThreadExecutor()


    fun ativa(ctx: Context): Boolean { val p = Prefs(ctx); return p.studioVoice && p.elevenKey.isNotBlank() }

    private fun voz(ctx: Context) = Prefs(ctx).studioVoiceId.ifBlank { VOZES[0].first }

    private fun arquivo(ctx: Context, voz: String, texto: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest("$MODELO|$voz|$texto".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(ctx.cacheDir, "voz").apply { mkdirs() }, "$h.mp3")
    }

    /** Gera (ou pega do cache) o áudio da frase. Roda fora da tela; devolve null se falhar. */
    private fun gerar(ctx: Context, texto: String): File? {
        val voz = voz(ctx)
        val f = arquivo(ctx, voz, texto)
        if (f.exists() && f.length() > 1000) return f
        val key = Prefs(ctx).elevenKey.trim()
        if (key.isBlank()) return null
        return try {
            val c = URL("https://api.elevenlabs.io/v1/text-to-speech/$voz?output_format=mp3_44100_64").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000; c.readTimeout = 15000
            c.doOutput = true
            c.setRequestProperty("xi-api-key", key)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "audio/mpeg")
            val body = JSONObject().put("text", texto).put("model_id", MODELO)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) {
                val err = c.errorStream?.bufferedReader()?.readText()?.take(160) ?: ""
                OfferLog.appendDiag(ctx, "VOZ DE ESTÚDIO: erro ${c.responseCode} $err")
                null
            } else {
                val tmp = File(f.path + ".tmp")
                c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                tmp.renameTo(f); f
            }
        } catch (e: Exception) {
            OfferLog.appendDiag(ctx, "VOZ DE ESTÚDIO: sem conexão (${e.javaClass.simpleName})")
            null
        }
    }

    /** Deixa a frase pronta antes da hora (ex.: logo no aceite, para tocar na hora do embarque). */
    fun preparar(ctx: Context, texto: String) {
        if (!ativa(ctx)) return
        val app = ctx.applicationContext
        rede.execute { gerar(app, texto) }
    }

    /** Fala com a voz de estúdio; se não der (sem internet, sem créditos), usa a voz do celular. */
    fun falar(ctx: Context, texto: String, reserva: () -> Unit) {
        val app = ctx.applicationContext
        rede.execute {
            val f = gerar(app, texto)
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (f == null || !tocar(app, f)) reserva() }
        }
    }

    /** Toca com a "altura da voz" escolhida (o Tocador cuida do reforço, do canal e de abaixar a música). */
    private fun tocar(ctx: Context, f: File): Boolean = Tocador.tocar(ctx, f)
}
