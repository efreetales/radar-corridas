package com.taleco.radarcorridas

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lê as notificações de compra dos apps de banco e separa os gastos do carro.
 * Nada sai do celular; só o que parece gasto de carro é guardado (em gastos.csv).
 */
class BankListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        // Uber/99 online com a leitura de ofertas desligada: o card não vai aparecer, avisa
        if (LeituraAviso.deApp(n.packageName)) { LeituraAviso.avisar(this, aposAtualizar = false); return }
        try {
            if (!Prefs(this).readBankNotifs) return
            val ex = n.notification.extras ?: return
            val titulo = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val texto = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            if (!texto.contains("R$") && !titulo.contains("R$")) return
            val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.packageName, 0)).toString() } catch (_: Exception) { null }
            val g = Gastos.ler(n.packageName, titulo, texto, app)
            if (g == null) {
                // Ajuda a ensinar novos formatos: notificação de banco/tag com valor que não virou gasto do carro
                if (Gastos.financeiro(n.packageName, app)) OfferLog.appendDiag(this, "NOTIFICAÇÃO NÃO RECONHECIDA (${app ?: n.packageName}): ${"$titulo | $texto".take(160)}")
                return
            }
            val id = Gastos.guardar(this, g) ?: return
            Gastos.avisar(this, id, g)
            OfferLog.appendDiag(this, "gasto do carro lido: ${g.cat} ${g.valor} (${g.banco})")
        } catch (_: Exception) {
        }
    }
}
