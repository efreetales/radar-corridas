package com.taleco.radarcorridas

import android.content.Context
import android.media.AudioAttributes

/**
 * Por qual canal de som a voz com o passageiro sai.
 * Navegação = o canal do Waze. Assistente = o canal em que o Android Auto lê as mensagens do WhatsApp.
 */
object VozCanal {
    fun attr(ctx: Context): AudioAttributes = AudioAttributes.Builder()
        .setUsage(when (Prefs(ctx).voiceChannel) {
            "midia" -> AudioAttributes.USAGE_MEDIA
            "assistente" -> AudioAttributes.USAGE_ASSISTANT
            else -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        })
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun nome(ctx: Context): String = when (Prefs(ctx).voiceChannel) { "midia" -> "canal de mídia"; "assistente" -> "canal do assistente"; else -> "canal de navegação" }
}
