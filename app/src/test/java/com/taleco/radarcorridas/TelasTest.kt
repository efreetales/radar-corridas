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
        val w = if (v.width > 0) v.width else 1080
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = v.measuredHeight
        v.layout(0, 0, w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).also { it.drawColor(Ui.BG); v.draw(it) }
        val d = File("build/previas"); d.mkdirs()
        File(d, "$nome.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("TELA $nome ${w}x$h")
    }
    private fun telas(tema: String) {
        Prefs(RuntimeEnvironment.getApplication()).tema = tema
        // simula versão nova no GitHub (sem internet no teste)
        RuntimeEnvironment.getApplication().getSharedPreferences("radar", android.content.Context.MODE_PRIVATE).edit()
            .putLong("atualiz_conferiu", System.currentTimeMillis()).putInt("atualiz_num", 83).putString("atualiz_url", "https://x/RadarCorridas.apk").commit()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = c.get()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val scroll = a.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView
        shot(scroll.getChildAt(0), "inicio_$tema")
        for ((k, n) in listOf("Leitura e permissões" to "leitura", "Voz com o passageiro" to "voz", "Aparência" to "aparencia", "Cartão e ganhos" to "card", "Custos e gastos" to "custos", "Radares de velocidade" to "radares")) {
            val m = MainActivity::class.java.getDeclaredMethod("showPage", String::class.java); m.isAccessible = true
            m.invoke(a, k)
            shot(scroll.getChildAt(0), "${n}_$tema")
        }
        try { c.pause().stop().destroy() } catch (_: Throwable) { }
    }
    @Test fun aviso() {
        val svc = Robolectric.buildService(RadarService::class.java).get()
        val o = OverlayManager(svc)
        for ((nome, b) in listOf(
            "aviso_evento" to SpeedBanner(null, 34, "Allianz Parque · saída ~23:10 · a 1,2 km", 0xFFC2255C.toInt(), null, 0.4f, sign = "{music_note}", title = "Show de rock no estádio", action = "Waze"),
            "aviso_ponto" to SpeedBanner(null, 28, "Bom até 19:00 · a 800 m", 0xFF5232B8.toInt(), null, 0.6f, sign = "{star}", title = "Shopping Eldorado", action = "Waze"),
        )) {
            o.showSpeed(b)
            val f = OverlayManager::class.java.getDeclaredField("speedView"); f.isAccessible = true
            val v = f.get(o) as View
            shot(v, nome)
            o.hideSpeed()
        }
    }
    @Test fun escuro() = telas("escuro")
    @Test fun claro() = telas("claro")
}
