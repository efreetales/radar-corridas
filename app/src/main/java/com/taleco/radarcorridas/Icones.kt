package com.taleco.radarcorridas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.os.Build
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat

/**
 * Ícones Material Symbols (arredondado) no lugar de símbolos de fonte. O radar é desenho próprio no mesmo estilo.
 * Em qualquer texto, "{nome}" vira o ícone, do tamanho da letra e da cor do texto:
 *   Icones.texto(ctx, "{check} Valeta marcada", textSizePx, cor)
 */
object Icones {
    private val RES = mapOf(
        "check" to R.drawable.ic_check, "x" to R.drawable.ic_close, "undo" to R.drawable.ic_undo,
        "star" to R.drawable.ic_star_fill, "valeta" to R.drawable.ic_warning_fill, "alerta" to R.drawable.ic_warning,
        "camera" to R.drawable.ic_radar, "navigation" to R.drawable.ic_near_me,
        "lock" to R.drawable.ic_lock, "lock_open" to R.drawable.ic_lock_open, "mobile" to R.drawable.ic_mobile,
        "volume" to R.drawable.ic_volume_up, "chevron" to R.drawable.ic_chevron_right, "like" to R.drawable.ic_thumb_up,
        "check_circle" to R.drawable.ic_check_circle, "pin" to R.drawable.ic_location_on,
        // eventos (Events.icon)
        "theater_comedy" to R.drawable.ic_theater_comedy, "music_note" to R.drawable.ic_music_note, "sports_soccer" to R.drawable.ic_sports_soccer,
        "church" to R.drawable.ic_church, "school" to R.drawable.ic_school, "confirmation_number" to R.drawable.ic_confirmation_number,
    )
    private val TOKEN = Regex("\\{([a-z_-]+)\\}")

    fun res(nome: String): Int? = RES[nome]

    /** Ícone no meio da linha, centralizado na altura do texto e pintado na cor do texto (ou na cor dada).
     *  É um ImageSpan (o jeito padrão do Android de pôr imagem em texto). */
    private class IconSpan(d: Drawable, private val cor: Int?) :
        ImageSpan(d, if (Build.VERSION.SDK_INT >= 29) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE) {
        override fun draw(c: Canvas, t: CharSequence?, s: Int, e: Int, x: Float, top: Int, y: Int, bottom: Int, p: Paint) {
            drawable.setTint(cor ?: p.color)
            super.draw(c, t, s, e, x, top, y, bottom, p)
        }
    }

    /** Troca cada "{nome}" pelo ícone. sizePx = tamanho da letra em px; cor null = cor do texto. */
    fun texto(ctx: Context, s: CharSequence?, sizePx: Float, cor: Int? = null): CharSequence {
        if (s == null) return ""
        if (!s.contains('{')) return s
        val out = SpannableStringBuilder(s)
        TOKEN.findAll(s).toList().asReversed().forEach { m ->
            val r = RES[m.groupValues[1]] ?: return@forEach
            val d = ContextCompat.getDrawable(ctx, r)?.mutate() ?: return@forEach
            val px = (sizePx * 1.15f).toInt()
            d.setBounds(0, 0, px, px)
            out.replace(m.range.first, m.range.last + 1, "\uFFFC")   // caractere de objeto (o mesmo que o ImageSpan usa) segura o ícone
            out.setSpan(IconSpan(d, cor), m.range.first, m.range.first + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** Para textos sem imagem (notificações, listas do sistema): tira os "{nome}". */
    fun semIcones(s: String): String = s.replace(TOKEN, "").replace(Regex("\\s{2,}"), " ").trim()
}
