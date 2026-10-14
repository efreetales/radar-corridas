package com.taleco.radarcorridas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
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

    /** Ícone desenhado no meio da linha, na cor do texto (ou na cor dada). */
    private class IconSpan(val d: Drawable, val size: Int, val cor: Int?) : ReplacementSpan() {
        override fun getSize(p: Paint, t: CharSequence?, s: Int, e: Int, fm: Paint.FontMetricsInt?): Int = size
        override fun draw(c: Canvas, t: CharSequence?, s: Int, e: Int, x: Float, top: Int, y: Int, bottom: Int, p: Paint) {
            d.setTint(cor ?: p.color)
            val fm = p.fontMetricsInt
            val mid = y + (fm.ascent + fm.descent) / 2f
            c.save()
            c.translate(x, mid - size / 2f)
            d.setBounds(0, 0, size, size)
            d.draw(c)
            c.restore()
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
            out.replace(m.range.first, m.range.last + 1, "\uFFFC")   // caractere de objeto (o mesmo que o ImageSpan usa) segura o ícone
            out.setSpan(IconSpan(d, px, cor), m.range.first, m.range.first + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** Para textos sem imagem (notificações, listas do sistema): tira os "{nome}". */
    fun semIcones(s: String): String = s.replace(TOKEN, "").replace(Regex("\\s{2,}"), " ").trim()
}
