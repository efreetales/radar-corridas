package com.taleco.radarcorridas

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Mapa: calor das ofertas, percursos, radares, valetas e pontos bons, com filtros por dia e horário. */
class MapActivity : AppCompatActivity() {

    /** Entrega ao mapa os arquivos de dados guardados no celular. */
    inner class Bridge {
        private val allowed = setOf("ofertas.csv", "corridas.csv", "rotas.csv", "passagens_radar.csv", "valetas.csv", "pontos.json")

        @JavascriptInterface
        fun file(name: String): String {
            if (name !in allowed) return ""
            val f = File(filesDir, name)
            return try { if (f.exists()) f.readText() else "" } catch (_: Exception) { "" }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Mapa"
        val web = WebView(this)
        web.setBackgroundColor(Colors.BG)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Radar")
        setContentView(web)
        web.loadUrl("file:///android_asset/mapa.html")
    }
}
