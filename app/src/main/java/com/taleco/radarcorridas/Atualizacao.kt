package com.taleco.radarcorridas

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização pelo próprio app: confere no GitHub se saiu uma versão nova (build-N maior que a instalada),
 * baixa o RadarCorridas.apk e abre a tela de instalação do Android (o Android sempre pede para confirmar).
 */
object Atualizacao {
    private const val API = "https://api.github.com/repos/efreetales/radar-corridas/releases/latest"
    private const val CONFERIR_A_CADA_MS = 3 * 60 * 60 * 1000L
    private val main = Handler(Looper.getMainLooper())

    class Nova(val numero: Int, val url: String)

    /** Última versão nova encontrada (null = está em dia ou ainda não conferiu). */
    @Volatile var nova: Nova? = null
        private set
    @Volatile var baixando = false
        private set

    fun instalada(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        (if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()).toInt()
    } catch (_: Exception) { 0 }

    /** Confere no GitHub (no máximo a cada 3 h, ou já se forcar). Chama pronto() na thread principal. */
    fun conferir(ctx: Context, forcar: Boolean = false, pronto: () -> Unit = {}) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences("radar", Context.MODE_PRIVATE)
        val ult = sp.getLong("atualiz_conferiu", 0L)
        if (!forcar && System.currentTimeMillis() - ult < CONFERIR_A_CADA_MS) {
            val n = sp.getInt("atualiz_num", 0); val u = sp.getString("atualiz_url", "") ?: ""
            nova = if (n > instalada(app) && u.isNotBlank()) Nova(n, u) else null
            main.post(pronto); return
        }
        Thread {
            try {
                val c = (URL(API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val n = j.optString("tag_name").removePrefix("build-").toIntOrNull() ?: 0
                val assets = j.optJSONArray("assets")
                var url = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
                }
                sp.edit().putLong("atualiz_conferiu", System.currentTimeMillis()).putInt("atualiz_num", n).putString("atualiz_url", url).apply()
                nova = if (n > instalada(app) && url.isNotBlank()) Nova(n, url) else null
            } catch (_: Exception) {
                // sem internet: tenta de novo na próxima vez que abrir o app
            }
            main.post(pronto)
        }.start()
    }

    /** O Android só deixa o Radar instalar depois que o motorista libera "Instalar apps desconhecidos" para ele. */
    fun podeInstalar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun pedirPermissao(a: Activity) {
        try {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
        } catch (_: Exception) {
            a.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    /** Baixa o APK (progresso de 0 a 100) e abre a instalação. fim(erro) com null = deu certo. */
    fun baixarEInstalar(a: Activity, progresso: (Int) -> Unit, fim: (String?) -> Unit) {
        val n = nova ?: return fim("Nenhuma versão nova")
        if (baixando) return
        baixando = true
        val app = a.applicationContext
        Thread {
            var erro: String? = null
            val dir = File(app.filesDir, "atualizacao").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "RadarCorridas-${n.numero}.apk")
            try {
                var c = URL(n.url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                // o GitHub manda para outro endereço (de onde o arquivo é baixado)
                var r = 0
                while (c.responseCode in 300..399 && r++ < 5) {
                    val loc = c.getHeaderField("Location") ?: break
                    c.disconnect()
                    c = (URL(loc).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
                }
                if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                var lido = 0L; var ult = -1
                c.inputStream.use { inp ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val k = inp.read(buf); if (k < 0) break
                            out.write(buf, 0, k); lido += k
                            if (total > 0) { val p = (lido * 100 / total).toInt(); if (p != ult) { ult = p; main.post { progresso(p) } } }
                        }
                    }
                }
                if (lido < 100_000) throw Exception("arquivo incompleto")
            } catch (e: Exception) {
                erro = "Não consegui baixar (${e.message ?: e.javaClass.simpleName}). Confira a internet e tente de novo."
                f.delete()
            }
            main.post {
                baixando = false
                if (erro == null) {
                    try {
                        val uri = FileProvider.getUriForFile(app, app.packageName + ".files", f)
                        a.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (e: Exception) {
                        erro = "Não consegui abrir a instalação (${e.javaClass.simpleName})"
                    }
                }
                fim(erro)
            }
        }.start()
    }
}
