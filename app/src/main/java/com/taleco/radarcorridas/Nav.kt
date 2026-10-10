package com.taleco.radarcorridas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Abre a navegação até um ponto: Waze (rota direto) ou Uber Motorista (endereço copiado para o Modo Destino). */
object Nav {

    private val main = Handler(Looper.getMainLooper())

    fun waze(ctx: Context, lat: Double, lng: Double) {
        val uri = Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")
        val withWaze = Intent(Intent.ACTION_VIEW, uri).setPackage("com.waze").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(withWaze)
        } catch (_: Exception) {
            // Waze não instalado: abre no navegador / outro app de mapa
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                toast(ctx, "Não encontrei o Waze")
            }
        }
    }

    /**
     * A Uber Motorista não aceita receber um destino de outro app.
     * Então copiamos o endereço e abrimos a Uber: é só colar no Modo Destino.
     */
    fun uber(ctx: Context, lat: Double, lng: Double, name: String) {
        val app = ctx.applicationContext
        Thread {
            val address: String = try {
                @Suppress("DEPRECATION")
                Geocoder(app, PT_BR).getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
            } catch (_: Exception) {
                null
            } ?: String.format(java.util.Locale.US, "%.6f, %.6f", lat, lng)
            main.post {
                try {
                    val cm = app.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("Destino", address))
                } catch (_: Exception) {
                }
                val launch = app.packageManager.getLaunchIntentForPackage("com.ubercab.driver")
                if (launch == null) {
                    toast(app, "Uber Motorista não encontrada. Endereço copiado: $address")
                    return@post
                }
                try {
                    app.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    toast(app, "Endereço de \"$name\" copiado. Na Uber, abra o Modo Destino e cole.")
                } catch (_: Exception) {
                    toast(app, "Não consegui abrir a Uber. Endereço copiado: $address")
                }
            }
        }.start()
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_LONG).show() }
    }
}
