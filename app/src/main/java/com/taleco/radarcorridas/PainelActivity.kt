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

    companion object { const val EXTRA_PAGE = "pagina"; private const val HOST = "radarcorridas.app" }

    private lateinit var prefs: Prefs

    inner class Bridge {
        private val allowed = setOf("ofertas.csv", "corridas.csv", "rotas.csv", "sessoes_uber.csv", "gastos.csv", "eventos_uber.csv", "ganhos_uber.csv", "ganhos_dia_uber.csv")
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

        /** Gastos do carro: confirmar, descartar, trocar categoria ou apagar. */
        @JavascriptInterface
        fun gasto(id: String, status: String, cat: String) { Gastos.mudar(this@PainelActivity, id, status, cat.ifBlank { null }) }

        /** Lança um gasto à mão (valor com ponto decimal). */
        @JavascriptInterface
        fun gastoNovo(valor: String, cat: String, local: String) {
            val v = valor.replace(",", ".").toDoubleOrNull() ?: return
            Gastos.guardar(this@PainelActivity, Gastos.Gasto(v, cat, local.ifBlank { Gastos.NOMES[cat] ?: cat }, "manual"), "confirmado", "manual")
        }

        @JavascriptInterface
        fun notifAcesso(): Boolean = Gastos.temAcesso(this@PainelActivity)

        @JavascriptInterface
        fun abrirNotifAcesso() { Gastos.abrirPermissao(this@PainelActivity) }

        /** Conferir com a Uber: aplica a correção montada na tela (mesmo formato do arquivo de correção). */
        @JavascriptInterface
        fun aplicarCorrecao(json: String): String = try { Correcao.importarTexto(this@PainelActivity, json) } catch (e: Exception) { "Não deu para aplicar: ${e.message}" }

        @JavascriptInterface
        fun desfazerCorrecao(): String = try { Correcao.desfazer(this@PainelActivity) } catch (e: Exception) { "Não deu para desfazer: ${e.message}" }

        @JavascriptInterface
        fun podeDesfazer(): Boolean = Correcao.podeDesfazer(this@PainelActivity)

        /** Abre outra tela do painel (ex.: do Histórico para Conferir). */
        @JavascriptInterface
        fun abrir(pagina: String) {
            runOnUiThread { startActivity(android.content.Intent(this@PainelActivity, PainelActivity::class.java).putExtra(EXTRA_PAGE, pagina)) }
        }

        /** Abre o replay animado de uma corrida. */
        @JavascriptInterface
        fun replay(id: String) {
            runOnUiThread { startActivity(android.content.Intent(this@PainelActivity, PainelActivity::class.java).putExtra(EXTRA_PAGE, "replay:$id")) }
        }
    }

    /** Carrega a página do app (painel ou replay) com o endereço dado como origem. */
    private fun carregar(web: WebView, url: String) {
        val replay = android.net.Uri.parse(url).path?.startsWith("/replay") == true
        val html = assets.open(if (replay) "replay.html" else "painel.html").bufferedReader().use { it.readText() }
        // tema claro/escuro igual ao do app
        web.loadDataWithBaseURL(url, html.replaceFirst("<head>", "<head><script>document.documentElement.dataset.tema='${Ui.nomeTema}'</script>"), "text/html", "utf-8", null)
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
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(Ui.modo(this))
        super.onCreate(savedInstanceState)
        Ui.atualizar(this)
        prefs = Prefs(this)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: "historico"
        val web = WebView(this)
        web.setBackgroundColor(Ui.BG)
        web.settings.javaScriptEnabled = true
        web.webViewClient = object : WebViewClient() {
            // O endereço radarcorridas.app é "de mentira" (só dá origem às páginas). Se a página tentar
            // navegar ou recarregar, carrega de novo a página do próprio app em vez de ir à internet.
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                if (u.host == HOST) { view?.let { carregar(it, u.toString()) }; return true }
                return false
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                val u = request?.url ?: return
                if (request.isForMainFrame && u.host == HOST) view?.let { carregar(it, u.toString()) }
            }
        }
        web.addJavascriptInterface(Bridge(), "Radar")
        setContentView(web)
        carregar(web, if (page.startsWith("replay:")) "https://$HOST/replay?id=${page.removePrefix("replay:")}" else "https://$HOST/painel?p=$page")
    }
}
