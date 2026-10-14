package com.taleco.radarcorridas

import android.content.Context
import android.media.AudioManager

/**
 * Sobe o volume só enquanto o Radar fala com o passageiro e devolve depois.
 * A fala sai pelo mesmo volume da música (no Bluetooth/Android Auto também).
 */
object Volume {
    private var antes = -1

    /** Põe o volume da mídia em pelo menos X% do máximo (escolhido em Corridas). */
    fun subir(ctx: Context) {
        val pct = Prefs(ctx).voiceVolume
        if (pct <= 0) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val atual = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val alvo = (max * pct / 100.0).toInt().coerceIn(1, max)
            if (atual < alvo) {
                if (antes < 0) antes = atual
                am.setStreamVolume(AudioManager.STREAM_MUSIC, alvo, 0)
            }
        } catch (_: Exception) { }
    }

    /** Volta ao volume de antes da fala. */
    fun voltar(ctx: Context) {
        if (antes < 0) return
        val v = antes; antes = -1
        try { ctx.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) } catch (_: Exception) { }
    }
}
