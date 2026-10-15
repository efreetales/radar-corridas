package com.taleco.radarcorridas

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Cores das telas do Radar (configurações, painel, mapa). Seguem o modo claro/escuro do celular,
 * ou o que o motorista escolher em Aparência. O card da oferta e os avisos sobre a Uber continuam
 * escuros (Colors), para contrastar com o mapa da Uber de dia e de noite.
 */
object Ui {
    var escuro = true
        private set

    /** Modo para o AppCompat (diálogos e barras do sistema na mesma cor). */
    fun modo(ctx: Context) = when (Prefs(ctx).tema) {
        "claro" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        "escuro" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** Lê o tema (chame no onCreate de cada tela). */
    fun atualizar(ctx: Context) {
        escuro = when (Prefs(ctx).tema) {
            "claro" -> false
            "escuro" -> true
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
        }
    }

    private fun c(dark: String, light: String) = Color.parseColor(if (escuro) dark else light)
    val BG get() = c("#0D1117", "#F4F6F8")
    val SURFACE get() = c("#161B22", "#FFFFFF")
    val SURFACE_2 get() = c("#21262D", "#EEF1F4")
    val TEXT get() = c("#E6EDF3", "#14181D")
    val MUTED get() = c("#8B949E", "#5F6B76")
    val ACCENT get() = c("#2FD3A6", "#0E9F7A")
    val LINE get() = c("#30363D", "#DDE2E7")
    /** Para as páginas web: "claro" ou "escuro". */
    val nomeTema get() = if (escuro) "escuro" else "claro"
}
