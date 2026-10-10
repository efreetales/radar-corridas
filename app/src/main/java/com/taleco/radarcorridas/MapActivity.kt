package com.taleco.radarcorridas

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
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
        private val allowed = setOf("ofertas.csv", "corridas.csv", "rotas.csv", "passagens_radar.csv", "valetas.csv", "pontos.json", "eventos_hoje.json", "sessoes_uber.csv")

        /** Sua posição atual (a mais recente que o celular tem): "lat,lng,precisão" ou "". */
        @SuppressLint("MissingPermission")
        @JavascriptInterface
        fun here(): String {
            return try {
                val lm = getSystemService(LocationManager::class.java) ?: return ""
                var best: Location? = null
                for (p in listOf(LocationManager.GPS_PROVIDER, "fused", LocationManager.NETWORK_PROVIDER)) {
                    val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } ?: continue
                    if (best == null || l.time > best.time) best = l
                }
                val l = best ?: return ""
                if (System.currentTimeMillis() - l.time > 30 * 60_000L) return "" // velha demais
                "${l.latitude},${l.longitude},${l.accuracy}"
            } catch (_: Exception) { "" }
        }

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
        web.settings.userAgentString = web.settings.userAgentString + " RadarCorridas/1.0"
        web.webViewClient = object : WebViewClient() {
            // Links do mapa (ex.: "Ir com Waze") abrem fora, no app certo
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                if (url.host?.contains("waze.com") == true) {
                    val lat = url.getQueryParameter("ll")?.split(",")
                    if (lat != null && lat.size == 2) {
                        Nav.waze(this@MapActivity, lat[0].toDouble(), lat[1].toDouble())
                        return true
                    }
                }
                return false
            }
        }
        web.addJavascriptInterface(Bridge(), "Radar")
        setContentView(web)
        // Carrega com um endereço https "de mentira": os servidores de mapa recusam páginas sem origem (file://)
        val html = assets.open("mapa.html").bufferedReader().use { it.readText() }
        web.loadDataWithBaseURL("https://radarcorridas.app/", html, "text/html", "utf-8", null)

        // Agenda de eventos: baixa se ainda não tiver (ou estiver velha) e atualiza o mapa
        val f = Events.file(this)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 2 * 60 * 60 * 1000L) {
            Events.refresh(this, force = true) { n ->
                if (n != null) web.evaluateJavascript("window.reloadEvents && window.reloadEvents()", null)
            }
        }
    }
}
