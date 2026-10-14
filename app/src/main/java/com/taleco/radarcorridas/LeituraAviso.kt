package com.taleco.radarcorridas

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Avisa quando a leitura de ofertas (acessibilidade) está desligada — sem ela o card não aparece.
 * - logo depois de uma atualização do Radar (o Android às vezes desliga ao instalar a versão nova)
 * - quando a Uber/99 posta notificação (está online) e a leitura está desligada (via leitura de notificações)
 * Some sozinho quando a leitura volta (RadarService.onServiceConnected).
 */
object LeituraAviso {
    private const val CHANNEL = "leitura_ofertas"
    private const val ID = 4242
    private const val REPETE_MS = 10 * 60_000L
    private var ultimo = 0L
    private val APPS = setOf("com.ubercab.driver", "com.app99.driver", "com.taxis99")

    fun ligada(): Boolean = RadarService.instance != null

    fun deApp(pkg: String) = pkg in APPS

    @SuppressLint("MissingPermission")
    fun avisar(ctx: Context, aposAtualizar: Boolean) {
        if (ligada()) return
        val now = System.currentTimeMillis()
        if (!aposAtualizar && now - ultimo < REPETE_MS) return
        ultimo = now
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Leitura de ofertas desligada", NotificationManager.IMPORTANCE_HIGH))
            val abrir = PendingIntent.getActivity(
                ctx, ID,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val titulo = if (aposAtualizar) "Radar atualizado: religue a leitura" else "Leitura de ofertas desligada"
            val texto = if (aposAtualizar) "O Android desligou a leitura de ofertas ao atualizar. Toque para religar antes de rodar."
                else "O card das corridas não vai aparecer. Toque para religar em Acessibilidade."
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_radar)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(abrir)
                .addAction(0, "Religar agora", abrir)
                .setOngoing(true)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(ID, n)
            OfferLog.appendDiag(ctx, "LEITURA DESLIGADA: aviso enviado (${if (aposAtualizar) "após atualizar" else "Uber online"})")
        } catch (_: Exception) {
        }
    }

    fun limpar(ctx: Context) {
        try { NotificationManagerCompat.from(ctx).cancel(ID) } catch (_: Exception) {}
    }
}

/** Depois de instalar uma versão nova: espera o Android religar o serviço; se não religou, avisa. */
class AtualizadoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try { LeituraAviso.avisar(app, aposAtualizar = true) } finally { pending.finish() }
        }, 8_000L)
    }
}
