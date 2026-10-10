package com.taleco.radarcorridas

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object Colors {
    val RED = Color.parseColor("#E5484D")
    val YELLOW = Color.parseColor("#F5C518")
    val GREEN = Color.parseColor("#3DD68C")
    val GREEN_DARK = Color.parseColor("#1F7A4D")
    val ACCENT = Color.parseColor("#2FD3A6")
    val BG = Color.parseColor("#0D1117")
    val SURFACE = Color.parseColor("#161B22")
    val SURFACE_2 = Color.parseColor("#21262D")
    val TEXT = Color.parseColor("#E6EDF3")
    val MUTED = Color.parseColor("#8B949E")

    fun of(v: Verdict): Int = when (v) {
        Verdict.RUIM -> RED
        Verdict.MEDIO -> YELLOW
        Verdict.BOM -> GREEN
    }
}

val PT_BR: Locale = Locale("pt", "BR")

fun Context.dp(v: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

fun Context.dp(v: Int): Int = dp(v.toFloat())

fun formatMetric(m: Metric, v: Double): String =
    String.format(PT_BR, "%.2f", v)

fun formatThreshold(m: Metric, v: Float): String =
    if (m.money) String.format(PT_BR, "R$ %.2f", v) else String.format(PT_BR, "%.2f", v)

/** Monta o cartão que aparece sobre o app de corrida. */
object CardView {

    fun build(ctx: Context, eval: Evaluation): View {
        val offer = eval.offer
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2000000"))
                cornerRadius = ctx.dp(18).toFloat()
                setStroke(ctx.dp(4), Colors.of(eval.overall))
            }
        }

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        if (eval.results.isEmpty()) {
            row.addView(TextView(ctx).apply {
                text = "Ative ao menos uma métrica"
                setTextColor(Colors.TEXT)
                textSize = 16f
            })
        }
        eval.results.forEachIndexed { i, r ->
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                if (i > 0) setPadding(ctx.dp(18), 0, 0, 0)
            }
            col.addView(TextView(ctx).apply {
                text = r.metric.label
                setTextColor(Colors.MUTED)
                textSize = 13f
            })
            val valueRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            valueRow.addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    setColor(Colors.of(r.verdict))
                    cornerRadius = ctx.dp(3).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(ctx.dp(5), ctx.dp(28)).apply {
                    rightMargin = ctx.dp(6)
                }
            })
            valueRow.addView(TextView(ctx).apply {
                text = formatMetric(r.metric, r.value)
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
            })
            col.addView(valueRow)
            row.addView(col)
        }
        root.addView(row)

        val hours = (offer.totalMin / 60).toInt()
        val mins = Math.round(offer.totalMin - hours * 60).toInt()
        val footerParts = mutableListOf(
            offer.app,
            String.format(PT_BR, "%dh%02dm", hours, mins),
            String.format(PT_BR, "%.1f km", offer.totalKm)
        )
        offer.category?.let { footerParts.add(it) }
        root.addView(TextView(ctx).apply {
            text = footerParts.joinToString("  ·  ")
            setTextColor(Colors.TEXT)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, ctx.dp(6), 0, 0)
        })
        eval.spotNote?.let { note ->
            root.addView(TextView(ctx).apply {
                text = note
                setTextColor(Color.parseColor("#C4B5FD"))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, ctx.dp(6), 0, 0)
            })
        }
        return root
    }
}
