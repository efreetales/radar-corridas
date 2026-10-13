package com.taleco.radarcorridas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gastos do carro lidos das notificações do banco ("Compra aprovada R$ 180,00 AUTO POSTO SHELL").
 * Só guarda o que parece gasto de carro (posto, pedágio, estacionamento, lava-rápido, oficina...).
 * Tudo fica no celular, em gastos.csv. O motorista confirma ou descarta cada um.
 */
object Gastos {
    const val FILE = "gastos.csv"
    private const val HEADER = "id;data_hora;valor;categoria;estabelecimento;banco;status;origem"
    private const val CHANNEL = "gastos_carro"

    /** Categoria -> palavras (sem acento, maiúsculas) que aparecem no nome do estabelecimento. */
    private val CATS = linkedMapOf(
        "combustivel" to listOf("AUTO POSTO", "AUTOPOSTO", "POSTO", "SHELL", "IPIRANGA", "PETROBRAS", "BR MANIA", "RAIZEN", "COMBUST", "GNV"),
        "pedagio" to listOf("SEM PARAR", "SEMPARAR", "CONECTCAR", "CONECT CAR", "VELOE", "MOVE MAIS", "TAGGY", "PEDAGIO", "AUTOBAN", "ECOVIAS", "ECOPISTAS", "ARTERIS", "VIAOESTE", "RODOANEL"),
        "estacionamento" to listOf("ESTACION", "ESTAPAR", "ZONA AZUL", "ZUL DIGITAL", "PARKING", "MULTIPARK", "INDIGO", "ALLPARK", "PARE BEM"),
        "lavagem" to listOf("LAVA RAPIDO", "LAVA-RAPIDO", "LAVARAPIDO", "LAVAGEM", "LAVA JATO", "LAVAJATO", "CAR WASH", "LAVACAR", "LAVA CAR", "ESTETICA AUTOMOTIVA"),
        "manutencao" to listOf("OFICINA", "AUTO CENTER", "AUTOCENTER", "AUTO PECAS", "AUTOPECAS", "PNEU", "BORRACHARIA", "MECANICA", "TROCA DE OLEO", "TROCA OLEO", "FUNILARIA", "RETIFICA", "ALINHAMENTO", "BALANCEAMENTO", "AUTOELETRICA", "AUTO ELETRICA", "BATERIA"),
        "seguro" to listOf("PORTO SEGURO", "AZUL SEGUROS", "YOUSE", "TOKIO MARINE", "HDI SEGUROS", "ALLIANZ", "SUHAI", "SEGURO AUTO", "MAPFRE", "LIBERTY SEG"),
        "documentos" to listOf("DETRAN", "IPVA", "LICENCIAMENTO", "MULTA", "DPVAT", "CRLV"),
        "aluguel" to listOf("LOCALIZA", "MOVIDA", "UNIDAS", "KOVI", "TURBI", "FOCO ALUGUEL", "ALUGUEL DE CARRO", "ALUGUEL DE VEICULO")
    )
    val NOMES = mapOf(
        "combustivel" to "Combustível", "pedagio" to "Pedágio", "estacionamento" to "Estacionamento", "lavagem" to "Lavagem",
        "manutencao" to "Manutenção", "seguro" to "Seguro", "documentos" to "IPVA e documentos", "aluguel" to "Aluguel"
    )
    /** Apps de banco conhecidos (o resto também é lido, se o texto parecer compra). */
    private val BANCOS = mapOf(
        "com.nu.production" to "Nubank", "com.itau" to "Itaú", "com.itau.iti" to "iti", "br.com.intermedium" to "Inter",
        "com.c6bank.app" to "C6 Bank", "com.bradesco" to "Bradesco", "br.com.bradesco.next" to "Next", "com.santander.app" to "Santander",
        "br.com.bb.android" to "Banco do Brasil", "br.com.gabba.Caixa" to "Caixa", "com.picpay" to "PicPay",
        "com.mercadopago.wallet" to "Mercado Pago", "br.com.uol.ps.myaccount" to "PagBank", "br.com.neon" to "Neon",
        "com.sicredi.sicredimobile" to "Sicredi", "br.com.sicoob.android" to "Sicoob", "br.com.willbank" to "Will Bank",
        "com.btgpactual.pf" to "BTG", "br.com.original.bank" to "Original", "com.xpi.xpcard" to "XP", "br.com.digio" to "Digio",
        "com.porto.cartaoporto" to "Porto Bank", "com.samsung.android.spay" to "Samsung Pay", "com.google.android.apps.walletnfcrel" to "Google Wallet"
    )
    private val COMPRA = Regex("COMPRA|APROVAD|PAGAMENTO|PAGO|DEBITO|DEBITADO|PIX ENVIADO|TRANSACAO|GASTO|CARTAO")
    private val NAO_E_GASTO = Regex("RECEB|ESTORN|CASHBACK|REEMBOLS|NEGAD|RECUSAD|CANCELAD|DEPOSITO|RENDIMENTO|FATURA FECHOU|LIMITE DISPON")
    private val VALOR = Regex("R\\$\\s?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2})")
    private val LOCAL = Regex("\\b(?:[Ee][Mm]|[Nn][OoAa]|[Ee]stabelecimento|ESTABELECIMENTO|[Pp]ara|PARA|[Ll]oja|LOJA)\\s*:?\\s+([A-Z0-9*][A-Z0-9 *&.\\-']{2,40})")
    /** Palavra-chave no começo de uma palavra (POSTO casa com "AUTO POSTO", não com "IMPOSTO"). */
    private val CAT_RX = CATS.mapValues { (_, ws) -> ws.map { Regex("(?<![A-Z0-9])" + Regex.escape(it)) } }

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)

    data class Gasto(val valor: Double, val cat: String, val local: String, val banco: String)

    fun categoria(texto: String): String? {
        val t = norm(texto)
        return CAT_RX.entries.firstOrNull { (_, rs) -> rs.any { it.containsMatchIn(t) } }?.key
    }

    /** Lê uma notificação; devolve o gasto se for compra ligada ao carro. */
    fun ler(pkg: String, titulo: String, texto: String, appNome: String?): Gasto? {
        val full = "$titulo $texto"
        val n = norm(full)
        if (!COMPRA.containsMatchIn(n) || NAO_E_GASTO.containsMatchIn(n)) return null
        val v = VALOR.find(full)?.groupValues?.get(1)?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: return null
        if (v <= 0.5 || v > 20000) return null
        val cat = categoria(full) ?: return null
        val local = LOCAL.findAll(full).map { it.groupValues[1].replace(Regex("\\s+\\d[\\d/:. ]*$"), "").trim().trimEnd('.', ',', '-') }
            .firstOrNull { it.length >= 3 && categoria(it) != null }
            ?: CATS[cat]!!.zip(CAT_RX[cat]!!).first { it.second.containsMatchIn(n) }.first.trim()
        return Gasto(v, cat, local.take(40), BANCOS[pkg] ?: appNome ?: pkg)
    }

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    private fun linhas(ctx: Context): MutableList<String> {
        val f = file(ctx)
        val l = if (f.exists()) f.readLines().filter { it.isNotBlank() }.toMutableList() else mutableListOf()
        if (l.isEmpty() || !l[0].startsWith("id;")) l.add(0, HEADER)
        return l
    }

    private fun limpa(s: String) = s.replace(";", " ").replace("\n", " ").trim()

    /** Guarda como pendente. Devolve o id, ou null se for repetido (bancos às vezes avisam duas vezes). */
    @Synchronized
    fun guardar(ctx: Context, g: Gasto, status: String = "pendente", origem: String = "notificacao", quando: Date = Date()): String? {
        val l = linhas(ctx)
        val agora = quando.time
        val dup = l.drop(1).any { r ->
            val c = r.split(';')
            c.size >= 4 && c[2].toDoubleOrNull() == g.valor && c[3] == g.cat &&
                (try { kotlin.math.abs(fmt.parse(c[1])!!.time - agora) < 10 * 60_000L } catch (_: Exception) { false })
        }
        if (dup && origem == "notificacao") return null
        val id = "g" + (agora / 1000)
        l.add(listOf(id, fmt.format(quando), String.format(Locale.US, "%.2f", g.valor), g.cat, limpa(g.local), limpa(g.banco), status, origem).joinToString(";"))
        file(ctx).writeText(l.joinToString("\n") + "\n")
        return id
    }

    /** status: confirmado | descartado | pendente | apagar; cat opcional para trocar a categoria. */
    @Synchronized
    fun mudar(ctx: Context, id: String, status: String, cat: String? = null) {
        val l = linhas(ctx)
        val out = l.mapNotNull { r ->
            val c = r.split(';').toMutableList()
            if (c[0] != id || c.size < 8) r
            else if (status == "apagar") null
            else { c[6] = status; if (cat != null && cat in CATS) c[3] = cat; c.joinToString(";") }
        }
        file(ctx).writeText(out.joinToString("\n") + "\n")
        NotificationManagerCompat.from(ctx).cancel(notifId(id))
    }

    private fun notifId(id: String) = 70000 + (id.drop(1).toLongOrNull() ?: 0L).toInt() % 20000

    fun avisar(ctx: Context, id: String, g: Gasto) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Gastos do carro", NotificationManager.IMPORTANCE_DEFAULT))
            fun acao(st: String, rc: Int) = PendingIntent.getBroadcast(
                ctx, notifId(id) * 3 + rc,
                Intent(ctx, GastoReceiver::class.java).putExtra("id", id).putExtra("status", st),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val abrir = PendingIntent.getActivity(
                ctx, notifId(id) * 3 + 2,
                Intent(ctx, PainelActivity::class.java).putExtra(PainelActivity.EXTRA_PAGE, "gastos").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val valor = "R$ " + String.format(Locale("pt", "BR"), "%,.2f", g.valor)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_radar)
                .setContentTitle("Gasto do carro? $valor")
                .setContentText("${NOMES[g.cat]} · ${g.local} · ${g.banco}")
                .setContentIntent(abrir)
                .addAction(0, "É do carro", acao("confirmado", 0))
                .addAction(0, "Não é", acao("descartado", 1))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(notifId(id), n)
        } catch (_: Exception) {
        }
    }

    /** O Radar tem permissão de ler notificações? */
    fun temAcesso(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(ComponentName(ctx, BankListener::class.java).flattenToString())
    }

    fun abrirPermissao(ctx: Context) {
        val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) { }
    }
}

/** Botões "É do carro" / "Não é" da notificação. */
class GastoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        Gastos.mudar(ctx, id, intent.getStringExtra("status") ?: "confirmado")
    }
}
