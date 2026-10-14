package com.taleco.radarcorridas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconesRenderTest {
    private fun count(v: View, w: Int, h: Int): Int {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(Color.BLACK)
        v.draw(Canvas(b))
        var n = 0
        for (x in 0 until w) for (y in 0 until h) if (Color.red(b.getPixel(x, y)) > 128) n++
        return n
    }
    @Test fun render() {
        val ctx = RuntimeEnvironment.getApplication()
        val d = ContextCompat.getDrawable(ctx, R.drawable.ic_lock)!!
        println("RENDER drawable intrinsic=${d.intrinsicWidth}x${d.intrinsicHeight} class=${d.javaClass.simpleName}")
        val b0 = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888); b0.eraseColor(Color.BLACK)
        d.setBounds(0, 0, 60, 60); d.setTint(Color.WHITE); d.draw(Canvas(b0))
        var n0 = 0; for (x in 0 until 60) for (y in 0 until 60) if (Color.red(b0.getPixel(x, y)) > 128) n0++
        println("RENDER drawable direto: $n0 px")
        val tv = TextView(ctx).apply { textSize = 22f; setTextColor(Color.WHITE); text = Icones.texto(ctx, "{lock}", textSize, Color.WHITE) }
        println("RENDER span só ícone: ${count(tv, 120, 120)} px  texto='${tv.text}' len=${tv.text.length}")
        val tv2 = TextView(ctx).apply { textSize = 16f; setTextColor(Color.WHITE); text = Icones.texto(ctx, "{check} Valeta", textSize) }
        val tv2b = TextView(ctx).apply { textSize = 16f; setTextColor(Color.WHITE); text = " Valeta" }
        println("RENDER span+texto: ${count(tv2, 300, 80)} px vs só texto ${count(tv2b, 300, 80)} px")
        val d2 = ContextCompat.getDrawable(ctx, R.drawable.ic_lock)!!.mutate(); d2.setTint(Color.WHITE); d2.setBounds(0, 0, 60, 60)
        val ss = SpannableString("￼"); ss.setSpan(ImageSpan(d2, ImageSpan.ALIGN_BASELINE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val tv3 = TextView(ctx).apply { textSize = 22f; text = ss }
        println("RENDER ImageSpan: ${count(tv3, 120, 120)} px")
    }
}
