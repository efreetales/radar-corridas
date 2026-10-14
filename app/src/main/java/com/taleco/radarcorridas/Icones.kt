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
 * Ícones da biblioteca Mynaui (mynaui.com/icons) no lugar de símbolos de fonte (✓ ✕ ★ ⚠ emojis).
 * Em qualquer texto, "{nome}" vira o ícone, do tamanho da letra e da cor do texto:
 *   Icones.texto(ctx, "{check} Valeta marcada", textSizePx, cor)
 */
object Icones {
    private val RES = mapOf(
        "check" to R.drawable.mi_check, "x" to R.drawable.mi_x, "undo" to R.drawable.mi_undo,
        "star" to R.drawable.mi_star, "valeta" to R.drawable.mi_danger, "alerta" to R.drawable.mi_danger_outline,
        "camera" to R.drawable.mi_camera, "navigation" to R.drawable.mi_navigation,
        "lock" to R.drawable.mi_lock, "lock_open" to R.drawable.mi_lock_open, "mobile" to R.drawable.mi_mobile,
        "volume" to R.drawable.mi_volume, "chevron" to R.drawable.mi_chevron_right, "like" to R.drawable.mi_like,
        "check_circle" to R.drawable.mi_check_circle, "pin" to R.drawable.mi_map_pin,
        // eventos (Events.icon)
        "mask" to R.drawable.mi_mask, "music" to R.drawable.mi_music, "users-group" to R.drawable.mi_users_group,
        "building" to R.drawable.mi_building, "graduation-cap" to R.drawable.mi_graduation_cap, "ticket" to R.drawable.mi_ticket,
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
            out.replace(m.range.first, m.range.last + 1, "⁠")   // um caractere invisível segura o ícone
            out.setSpan(IconSpan(d, px, cor), m.range.first, m.range.first + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** Para textos sem imagem (notificações, listas do sistema): tira os "{nome}". */
    fun semIcones(s: String): String = s.replace(TOKEN, "").replace(Regex("\\s{2,}"), " ").trim()
}
