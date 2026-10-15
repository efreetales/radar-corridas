package com.taleco.radarcorridas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Gera prints das telas (claro e escuro) para conferir antes de publicar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TelasTest {
    private fun shot(v: View, nome: String) {
        val w = v.width
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = v.measuredHeight
        v.layout(0, 0, w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        val d = File("build/previas"); d.mkdirs()
        File(d, "$nome.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("TELA $nome ${w}x$h")
    }
    private fun telas(tema: String) {
        Prefs(RuntimeEnvironment.getApplication()).tema = tema
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = c.get()
        val scroll = a.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView
        shot(scroll.getChildAt(0), "inicio_$tema")
        for ((k, n) in listOf("Leitura e permissões" to "leitura", "Voz com o passageiro" to "voz", "Aparência" to "aparencia", "Cartão e ganhos" to "card", "Custos e gastos" to "custos")) {
            val m = MainActivity::class.java.getDeclaredMethod("showPage", String::class.java); m.isAccessible = true
            m.invoke(a, k)
            shot(scroll.getChildAt(0), "${n}_$tema")
        }
        c.pause().stop().destroy()
    }
    @Test fun escuro() = telas("escuro")
    @Test fun claro() = telas("claro")
}
