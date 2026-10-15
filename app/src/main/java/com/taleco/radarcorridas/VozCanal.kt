package com.taleco.radarcorridas

import android.content.Context
import android.media.AudioAttributes

/** Por qual canal de som a voz com o passageiro sai. Mídia é o mesmo do áudio do WhatsApp no viva-voz (mais alto no carro). */
object VozCanal {
    fun attr(ctx: Context): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (Prefs(ctx).voiceChannel == "navegacao") AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE else AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
}
