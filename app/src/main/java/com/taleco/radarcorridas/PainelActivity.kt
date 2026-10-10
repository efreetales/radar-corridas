package com.taleco.radarcorridas

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Telas de análise em página web (assets/painel.html): Histórico, Melhores horários,
 * Jornada, Custos e Estilo do cartão. Abre com EXTRA_PAGE = "historico" | "horarios" | ...
 */
class PainelActivity : AppCompatActivity() {

    companion object { const val EXTRA_PAGE = "pagina" }

    private lateinit var prefs: Prefs

    inner class Bridge {
        private val allowed = setOf("ofertas.csv", "corridas.csv", "rotas.csv", "sessoes_uber.csv")
        private val sp get() = getSharedPreferences("radar", Context.MODE_PRIVATE)

        @JavascriptInterface
        fun file(name: String): String {
            if (name !in allowed) return ""
            val f = File(filesDir, name)
            return try { if (f.exists()) f.readText() else "" } catch (_: Exception) { "" }
        }

        /** Lê uma preferência. Faixas do semáforo (bad_/good_) e os custos já existentes vêm do Prefs. */
        @JavascriptInterface
        fun pref(key: String): String {
            return try {
                when {
                    key.startsWith("bad_") || key.startsWith("good_") -> {
                        val m = Metric.values().firstOrNull { it.key == key.substringAfter('_') } ?: return ""
                        (if (key.startsWith("bad_")) prefs.bad(m) else prefs.good(m)).toString()
                    }
                    key == "c_kml" && !sp.contains("c_kml") -> prefs.kmPerLiter.toString()
                    key == "c_fuel" && !sp.contains("c_fuel") -> prefs.fuelPrice.toString()
                    key == "c_maint" && !sp.contains("c_maint") -> prefs.extraCostKm.toString()
                    key == "card_bottom" && !sp.contains("card_bottom") ->
                        Metric.values().filter { prefs.isShown(it) }.joinToString(",") { it.key }
                    else -> sp.getString(key, "") ?: ""
                }
            } catch (_: Exception) { "" }
        }

        @JavascriptInterface
        fun setPref(key: String, value: String) {
            try {
                when {
                    key.startsWith("bad_") || key.startsWith("good_") -> {
                        val m = Metric.values().firstOrNull { it.key == key.substringAfter('_') } ?: return
                        val v = value.toFloat()
                        if (key.startsWith("bad_")) prefs.setRange(m, v, prefs.good(m).coerceAtLeast(v)) else prefs.setRange(m, prefs.bad(m).coerceAtMost(v), v)
                    }
                    else -> {
                        sp.edit().putString(key, value).apply()
                        if (key.startsWith("c_")) syncCosts()
                        if (key == "card_bottom") {
                            val keys = value.split(",")
                            Metric.values().forEach { prefs.setShown(it, it.key in keys) }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        @JavascriptInterface
        fun close() { runOnUiThread { finish() } }
    }

    /** Leva os custos da tela nova para o cálculo de lucro do cartão. */
    private fun syncCosts() {
        val sp = getSharedPreferences("radar", Context.MODE_PRIVATE)
        fun f(k: String, d: Float) = sp.getString(k, null)?.replace(",", ".")?.toFloatOrNull() ?: d
        prefs.kmPerLiter = f("c_kml", prefs.kmPerLiter)
        prefs.fuelPrice = f("c_fuel", prefs.fuelPrice)
        prefs.extraCostKm = f("c_maint", prefs.extraCostKm)
        val fixedMonth = f("c_rent", 0f) + f("c_ins", 0f) + f("c_ipva", 0f) / 12f + f("c_other", 0f)
        prefs.rentPerWeek = fixedMonth / 4.33f
        prefs.kmPerWeek = f("c_kmmes", prefs.kmPerWeek * 4.33f) / 4.33f
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: "historico"
        val web = WebView(this)
        web.setBackgroundColor(Colors.BG)
        web.settings.javaScriptEnabled = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Radar")
        setContentView(web)
        val html = assets.open("painel.html").bufferedReader().use { it.readText() }
        web.loadDataWithBaseURL("https://radarcorridas.app/painel?p=$page", html, "text/html", "utf-8", null)
    }
}
