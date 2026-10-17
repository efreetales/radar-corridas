package com.taleco.radarcorridas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.DigitsKeyListener
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

/** Tela de configuração: status, prévia do cartão, faixas de ruim/bom, custos, aparência e dados. */
class MainActivity : AppCompatActivity() {

    // Importar correção de corridas (.json preparado a partir das ofertas, do GPS e do resumo da Uber)
    private val importLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val msg = try { Correcao.importar(this, uri) } catch (e: Exception) { "Não deu para importar: ${e.message}" }
        android.app.AlertDialog.Builder(this).setTitle("Importar correção").setMessage(msg).setPositiveButton("OK", null).show()
    }


    private lateinit var prefs: Prefs
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var activateBtn: MaterialButton
    private lateinit var previewBox: FrameLayout
    private lateinit var costSummary: TextView
    private lateinit var countText: TextView

    // ---------- Navegação: tela inicial com cards → página de cada seção ----------

    private class Page(val key: String, val icon: Int, val hint: String, val web: String? = null, val grupo: String? = null) {
        lateinit var body: LinearLayout
    }

    /** Páginas do app. grupo = seção da tela inicial (trabalho, rua, ganhos, ajustes); null = só por dentro de outra página. */
    private val pages = linkedMapOf(
        // Seu trabalho
        "Histórico de ofertas" to Page("Corridas", R.drawable.ic_car, "Ofertas, corridas e replay", web = "historico", grupo = "trabalho"),
        "Mapa" to Page("Mapa", R.drawable.ic_map, "Calor das ofertas e percursos", grupo = "trabalho"),
        "Jornada" to Page("Tempo", R.drawable.ic_timer, "Online, buscando e em viagem", web = "jornada", grupo = "trabalho"),
        "Melhores horários" to Page("Melhores horários", R.drawable.ic_clock, "Quando mais paga", web = "horarios", grupo = "trabalho"),
        // Na rua
        "Radares de velocidade" to Page("Radares", R.drawable.ic_speed, "Aviso, bipe e distância", grupo = "rua"),
        "Valetas e buracos" to Page("Valetas", R.drawable.ic_warning, "Marcar e avisar", grupo = "rua"),
        "Meus pontos bons" to Page("Pontos bons", R.drawable.ic_star, "Lugares e horários", grupo = "rua"),
        "Eventos de hoje" to Page("Eventos", R.drawable.ic_event, "Saídas de shows e jogos", grupo = "rua"),
        // Ganhos e custos
        "Cartão e ganhos" to Page("Card da oferta", R.drawable.ic_tune, "Faixas de cor e campos", grupo = "ganhos"),
        "Custos e gastos" to Page("Custos do carro", R.drawable.ic_fuel, "Gastos e custo por km", grupo = "ganhos"),
        "Cálculo de ganhos" to Page("Faixas de cor", R.drawable.ic_tune, "Quando cada número fica vermelho, amarelo ou verde"),
        "Estilo do cartão" to Page("Campos do card", R.drawable.ic_tune, "O que aparece no card e em que ordem", web = "cartao"),
        "Custos do carro" to Page("Custo por km", R.drawable.ic_money, "Seu custo por km e o mínimo para aceitar", web = "custos"),
        "Gastos do carro" to Page("Gastos do carro", R.drawable.ic_fuel, "Lidos das notificações do banco: posto, pedágio, oficina", web = "gastos"),
        // Ajustes
        "Voz com o passageiro" to Page("Voz com o passageiro", R.drawable.ic_record_voice_over, "Textos, voz, volume e canal do áudio", grupo = "ajustes"),
        "Leitura e permissões" to Page("Leitura e permissões", R.drawable.ic_verified_user, "Ofertas, GPS, notificações e bateria", grupo = "ajustes"),
        "Aparência" to Page("Aparência", R.drawable.ic_palette, "Tema, bolinha R e posição do card", grupo = "ajustes"),
        "Dados" to Page("Dados e backup", R.drawable.ic_storage, "Exportar, conferir com a Uber, diagnóstico", grupo = "ajustes")
    )
    /** Páginas que são uma lista de subpáginas. */
    private val hubs = mapOf(
        "Cartão e ganhos" to listOf("Cálculo de ganhos", "Estilo do cartão"),
        "Custos e gastos" to listOf("Custos do carro", "Gastos do carro")
    )

    private lateinit var scroll: ScrollView
    private lateinit var home: LinearLayout
    private lateinit var statusCard: LinearLayout
    private var currentPage: String? = null
    private var previewBlock: View? = null
    /** Subpáginas: o "voltar" leva para a página-mãe, não para a tela inicial. */
    private val parentPage = mapOf("Cálculo de ganhos" to "Cartão e ganhos")
    private lateinit var homeStatusTitle: TextView
    private lateinit var homeStatusSub: TextView
    private lateinit var homePill: TextView

    private fun goBack() {
        val p = currentPage?.let { parentPage[it] }
        if (p != null) showPage(p) else showHome()
    }

    /** Linha de lista (ícone, título, descrição e seta): usada nos hubs e nos Ajustes da tela inicial. */
    private fun listRow(pg: Page, key: String, divider: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(12), dp(16))
            minimumHeight = dp(76)
        }
        ripple(row)
        row.addView(icon(pg.icon, 28, Ui.ACCENT).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(16) }
        })
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        t.addView(text(pg.key, 17f, Ui.TEXT, bold = true))
        t.addView(text(pg.hint, 13f, Ui.MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(icon(R.drawable.ic_chevron_right, 24, Ui.MUTED).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = dp(8) }
        })
        row.setOnClickListener { abrir(key) }
        if (!divider) return row
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row)
        wrap.addView(View(this).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { leftMargin = dp(60) })
        return wrap
    }

    /** Lista agrupada num cartão (fundo único, separadores entre as linhas). */
    private fun listCard(keys: List<String>): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, 20f)
        }
        keys.forEachIndexed { i, k -> pages[k]?.let { box.addView(listRow(it, k, i < keys.size - 1), matchWrap()) } }
        return box
    }

    /** Abre uma página: web, mapa ou página nativa. */
    private fun abrir(key: String) {
        val pg = pages[key] ?: return
        when {
            key == "Mapa" -> startActivity(Intent(this, MapActivity::class.java))
            pg.web != null -> startActivity(Intent(this, PainelActivity::class.java).putExtra(PainelActivity.EXTRA_PAGE, pg.web))
            else -> showPage(key)
        }
    }

    /** Páginas-hub (Card da oferta, Custos do carro): lista das subpáginas. */
    private fun buildHubs() {
        for ((hubKey, keys) in hubs) pages[hubKey]?.body?.addView(listCard(keys), matchWrap(top = 16))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(Ui.modo(this))
        super.onCreate(savedInstanceState)
        Ui.atualizar(this)
        prefs = Prefs(this)

        scroll = ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
        }
        setContentView(scroll)

        // Conteúdo de cada página (montado uma vez)
        for ((title, pg) in pages) {
            pg.body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        }
        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Ui.SURFACE, 20f)
        }

        val dummy = LinearLayout(this)
        buildStatus(dummy)
        buildPreview(dummy)
        buildMetrics(dummy)
        buildCosts(dummy)
        buildAppearance(dummy)
        buildTrips(dummy)
        buildSpeed(dummy)
        buildHazards(dummy)
        buildSpots(dummy)
        buildEvents(dummy)
        buildData(dummy)
        buildHubs()
        for (pg in pages.values) tallAll(pg.body)

        home = buildHome()
        showHome()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPage != null) {
                    goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun buildHome(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(40))
        }
        col.addView(text("Radar Corridas", 28f, Ui.TEXT, bold = true))
        col.addView(text(
            "Mostra na hora se a corrida compensa. Só lê a oferta: nunca aceita nem recusa por você.",
            14f, Ui.MUTED
        ).apply { setPadding(0, dp(4), 0, dp(16)) })

        // Status compacto: toca para abrir Leitura e permissões
        val st = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Ui.SURFACE, 20f)
            minimumHeight = dp(76)
        }
        ripple(st)
        st.addView(icon(R.drawable.ic_power, 30, Ui.ACCENT).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(14) }
        })
        val stt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeStatusTitle = text("", 17f, Ui.TEXT, bold = true)
        homeStatusSub = text("", 13f, Ui.MUTED)
        stt.addView(homeStatusTitle); stt.addView(homeStatusSub)
        st.addView(stt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        homePill = text("", 13f, Ui.ACCENT, bold = true).apply { setPadding(dp(12), dp(6), dp(12), dp(6)) }
        st.addView(homePill)
        st.setOnClickListener { showPage("Leitura e permissões") }
        col.addView(st, matchWrap())

        for ((grupo, titulo) in listOf("trabalho" to "Seu trabalho", "rua" to "Na rua", "ganhos" to "Ganhos e custos", "ajustes" to "Ajustes")) {
            col.addView(text(titulo.uppercase(), 13f, Ui.MUTED, bold = true).apply {
                letterSpacing = 0.06f
                setPadding(dp(4), dp(24), 0, dp(10))
            })
            val keys = pages.filter { it.value.grupo == grupo }.keys.toList()
            if (grupo == "ajustes") { col.addView(listCard(keys), matchWrap()); continue }
            // Grade de 2 colunas, todos os cards com a mesma altura
            keys.chunked(2).forEachIndexed { i, par ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (j in 0..1) {
                    val lp = LinearLayout.LayoutParams(0, dp(150), 1f).apply { if (j == 0) rightMargin = dp(6) else leftMargin = dp(6) }
                    val k = par.getOrNull(j)
                    row.addView(if (k == null) View(this) else tile(k, pages[k]!!), lp)
                }
                col.addView(row, matchWrap(top = if (i == 0) 0 else 12))
            }
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        col.addView(text("Versão $version", 12f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })
        return col
    }

    private fun ripple(v: View) {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        v.foreground = ContextCompat.getDrawable(this, tv.resourceId)
        v.isClickable = true
        v.isFocusable = true
    }

    private fun icon(res: Int, sizeDp: Int, color: Int) = android.widget.ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Card da tela inicial: ícone em cima, nome e descrição (até 2 linhas). Altura fixa definida pela grade. */
    private fun tile(key: String, pg: Page): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = rounded(Ui.SURFACE, 20f)
        }
        ripple(card)
        card.addView(icon(pg.icon, 30, Ui.ACCENT))
        card.addView(text(pg.key, 17f, Ui.TEXT, bold = true).apply { setPadding(0, dp(12), 0, 0); maxLines = 2 })
        card.addView(text(pg.hint, 13f, Ui.MUTED).apply {
            setPadding(0, dp(2), 0, 0); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        card.setOnClickListener { abrir(key) }
        return card
    }

    private fun showHome() {
        currentPage = null
        (home.parent as? android.view.ViewGroup)?.removeView(home)
        scroll.removeAllViews()
        scroll.addView(home)
        scroll.scrollTo(0, 0)
        updateStatus()
    }

    private fun showPage(title: String) {
        val pg = pages[title] ?: return
        currentPage = title
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = android.widget.FrameLayout(this).apply {
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setOnClickListener { goBack() }
        }
        ripple(back)
        back.addView(icon(R.drawable.ic_back, 26, Ui.TEXT), android.widget.FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(icon(pg.icon, 28, Ui.ACCENT).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { leftMargin = dp(4); rightMargin = dp(10) }
        })
        bar.addView(text(pg.key, 22f, Ui.TEXT, bold = true))
        col.addView(bar, matchWrap())

        // A prévia do cartão aparece no topo do "Cartão e ganhos" e das "Faixas de ganhos"
        val pv = previewBlock
        if (pv != null && (title == "Cartão e ganhos" || title == "Cálculo de ganhos")) {
            (pv.parent as? android.view.ViewGroup)?.removeView(pv)
            pg.body.addView(pv, 0, matchWrap())
        }
        (pg.body.parent as? android.view.ViewGroup)?.removeView(pg.body)
        col.addView(pg.body, matchWrap(top = 8))
        scroll.removeAllViews()
        scroll.addView(col)
        scroll.scrollTo(0, 0)
        onResume()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateCount()
        updateTripChecklist()
        updateSpeedInfo()
        updateHazardInfo()
        refreshSpots()
        refreshEvents()
        refreshPreview()
    }

    // ---------- Seções ----------

    private fun buildStatus(col: LinearLayout) {
        val card = section(col, "Status")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(10) }
        }
        statusText = text("", 16f, Ui.TEXT, bold = true)
        row.addView(statusDot)
        row.addView(statusText)
        card.addView(row)

        activateBtn = MaterialButton(this).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        card.addView(activateBtn, matchWrap(top = 12))

        card.addView(text(
            "Em Acessibilidade, procure \"Radar Corridas\" (às vezes dentro de \"Apps instalados\") e ative.\n\n" +
                "Se a opção aparecer bloqueada: abra Configurações {chevron} Apps {chevron} Radar Corridas {chevron} menu de três pontinhos {chevron} " +
                "\"Permitir configurações restritas\" e tente de novo.",
            13f, Ui.MUTED
        ).apply { setPadding(0, dp(8), 0, 0) })

        val testBtn = outlinedButton("Testar cartão na tela").apply {
            setOnClickListener {
                val s = RadarService.instance
                if (s == null) toast("Ative a leitura de ofertas primeiro") else s.showTestCard()
            }
        }
        card.addView(testBtn, matchWrap(top = 8))
    }

    private fun buildPreview(col: LinearLayout) {
        val card = section(col, "Prévia do cartão")
        card.addView(text(
            "Exemplo com a oferta do print: Black, R$ 22,02, 16 min e 7 km no total.",
            13f, Ui.MUTED
        ).apply { setPadding(0, 0, 0, dp(10)) })
        previewBox = FrameLayout(this).apply {
            setPadding(dp(8), dp(16), dp(8), dp(16))
            background = rounded(Color.parseColor("#2A3441"), 12f)
        }
        card.addView(previewBox, matchWrap())
    }

    private fun buildMetrics(col: LinearLayout) {
        val card = section(col, "Cálculo de ganhos")
        card.addView(text(
            "Arraste as bolinhas: até a da esquerda é ruim (vermelho), a partir da da direita é bom (verde). " +
                "Entre as duas, médio (amarelo). Busca e viagem entram juntas na conta.",
            13f, Ui.MUTED
        ))

        for (m in Metric.values()) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(8))
                background = rounded(Ui.SURFACE_2, 12f)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(
                text(m.label, 17f, Ui.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            val show = SwitchMaterial(this).apply {
                text = "No cartão"
                setTextColor(Ui.MUTED)
                isChecked = prefs.isShown(m)
                setOnCheckedChangeListener { _, checked ->
                    prefs.setShown(m, checked)
                    refreshPreview()
                }
            }
            header.addView(show)
            box.addView(header)
            box.addView(text(m.help, 12f, Ui.MUTED))

            val rangeLabel = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(8), 0, 0) }
            box.addView(rangeLabel)

            val bad = prefs.bad(m).coerceIn(m.sliderMin, m.sliderMax)
            val good = prefs.good(m).coerceIn(bad, m.sliderMax)
            val slider = RangeSlider(this)
            slider.valueFrom = m.sliderMin
            slider.valueTo = m.sliderMax
            slider.stepSize = 0f
            slider.setValues(bad, good)
            slider.setLabelFormatter { v -> formatThreshold(m, snap(m, v)) }
            slider.trackActiveTintList = ColorStateList.valueOf(Colors.YELLOW)
            slider.trackInactiveTintList = ColorStateList.valueOf(Color.parseColor("#3A4350"))
            slider.thumbTintList = ColorStateList.valueOf(Color.WHITE)
            slider.addOnChangeListener { s, _, _ ->
                val vals = s.values
                if (vals.size >= 2) {
                    val b = snap(m, vals[0])
                    val g = snap(m, vals[1])
                    prefs.setRange(m, b, g)
                    rangeLabel.text = rangeText(m, b, g)
                    refreshPreview()
                }
            }
            rangeLabel.text = rangeText(m, bad, good)
            box.addView(slider, matchWrap())

            card.addView(box, matchWrap(top = 12))
        }
    }

    private fun buildCosts(col: LinearLayout) {
        val card = section(col, "Custos do carro")
        card.addView(text(
            "Usados para calcular o Lucro: valor da corrida menos o custo dos km rodados.",
            13f, Ui.MUTED
        ))
        card.addView(numberField("Km por litro", prefs.kmPerLiter) { prefs.kmPerLiter = it }, matchWrap(top = 10))
        card.addView(numberField("Preço do litro (R$)", prefs.fuelPrice) { prefs.fuelPrice = it }, matchWrap(top = 8))
        card.addView(
            numberField("Aluguel ou parcela do carro por semana (R$) — 0 se não tiver", prefs.rentPerWeek) { prefs.rentPerWeek = it },
            matchWrap(top = 8)
        )
        card.addView(
            numberField("Km que você roda por semana (para dividir o aluguel)", prefs.kmPerWeek) { prefs.kmPerWeek = it },
            matchWrap(top = 8)
        )
        card.addView(
            numberField("Outros custos por km (R$) — manutenção, pneus, seguro", prefs.extraCostKm) { prefs.extraCostKm = it },
            matchWrap(top = 8)
        )
        costSummary = text("", 14f, Ui.TEXT, bold = true).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(costSummary)
        updateCostSummary()
    }

    private fun buildAppearance(col: LinearLayout) {
        val card = section(col, "Aparência")

        card.addView(text("Tema", 14f, Ui.TEXT))
        val tg = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val temas = listOf("auto" to "Celular", "claro" to "Claro", "escuro" to "Escuro")
        val tIds = mutableMapOf<Int, String>()
        for ((k, l) in temas) {
            val b = outlinedButton(l).apply { id = View.generateViewId() }
            tIds[b.id] = k
            tg.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (prefs.tema == k) tg.check(b.id)
        }
        tg.addOnButtonCheckedListener { _, id, on ->
            val k = tIds[id] ?: return@addOnButtonCheckedListener
            if (on && k != prefs.tema) { prefs.tema = k; androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(Ui.modo(this)); recreate() }
        }
        card.addView(tg, matchWrap(top = 6))
        card.addView(text("O card da oferta e os avisos sobre a Uber ficam sempre escuros, para ler bem em cima do mapa.", 12f, Ui.MUTED).apply { setPadding(0, 0, 0, dp(14)) })

        card.addView(text("Posição do card", 14f, Ui.TEXT))
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val ids = mutableMapOf<Int, CardPosition>()
        for (p in CardPosition.values()) {
            val b = outlinedButton(p.label).apply { id = View.generateViewId() }
            ids[b.id] = p
            group.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (p == prefs.position) group.check(b.id)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) ids[checkedId]?.let { prefs.position = it }
        }
        card.addView(group, matchWrap(top = 6))

        val opLabel = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(14), 0, 0) }
        card.addView(opLabel)
        fun setOpLabel(v: Float) { opLabel.text = "Opacidade do card: ${(v * 100).roundToInt()}%" }
        setOpLabel(prefs.opacity)
        val op = Slider(this)
        op.valueFrom = 0.4f
        op.valueTo = 1f
        op.stepSize = 0f
        op.value = prefs.opacity.coerceIn(0.4f, 1f)
        op.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        op.addOnChangeListener { _, v, _ ->
            prefs.opacity = v
            setOpLabel(v)
            refreshPreview()
        }
        card.addView(op, matchWrap())

        val bubble = SwitchMaterial(this).apply {
            text = "Mostrar botão flutuante \"R\""
            setTextColor(Ui.TEXT)
            isChecked = prefs.showBubble
            setOnCheckedChangeListener { _, checked ->
                prefs.showBubble = checked
                RadarService.instance?.refreshBubble()
            }
        }
        card.addView(bubble, matchWrap(top = 6))
        val bLabel = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(bLabel)
        fun setB(v: Float) { bLabel.text = "Opacidade da bolinha R: ${(v * 100).roundToInt()}%" }
        setB(prefs.bubbleOpacity)
        card.addView(Slider(this).apply {
            valueFrom = 0.2f; valueTo = 1f; stepSize = 0f
            value = prefs.bubbleOpacity.coerceIn(0.2f, 1f)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            addOnChangeListener { _, v, fromUser -> prefs.bubbleOpacity = v; setB(v); if (fromUser) RadarService.instance?.refreshBubble() }
        }, matchWrap())
        card.addView(text(
            "O botão fica sobre a tela, mostra que o radar está ligado e abre esta tela com um toque. Arraste para mudar de lugar.",
            12f, Ui.MUTED
        ))
    }

    private fun buildData(col: LinearLayout) {
        val card = section(col, "Dados")

        val save = SwitchMaterial(this).apply {
            text = "Salvar as ofertas vistas"
            setTextColor(Ui.TEXT)
            isChecked = prefs.logOffers
            setOnCheckedChangeListener { _, checked -> prefs.logOffers = checked }
        }
        card.addView(save, matchWrap())
        card.addView(text(
            "Guarda valor, distância, tempo, endereços e o veredito de cada oferta, inclusive as que você recusa. " +
                "Serve para descobrir onde e quando aparecem as melhores corridas.",
            12f, Ui.MUTED
        ))
        countText = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(8), 0, 0) }
        card.addView(countText)
        card.addView(outlinedButton("Exportar dados (ofertas, corridas, rotas, radares e valetas)").apply {
            setOnClickListener {
                val ctx = this@MainActivity
                val files = listOf(OfferLog.offersFile(ctx), TripLog.tripsFile(ctx), TripLog.routesFile(ctx), PassLog.file(ctx), Hazards.file(ctx), Spots.file(ctx), SpeedCams.reportFile(ctx), java.io.File(ctx.filesDir, "sessoes_uber.csv"), java.io.File(ctx.filesDir, "eventos_uber.csv"), java.io.File(ctx.filesDir, "ganhos_uber.csv"), java.io.File(ctx.filesDir, "ganhos_dia_uber.csv"))
                if (!OfferLog.shareAll(ctx, files, "Dados — Radar Corridas")) toast("Nenhum dado salvo ainda")
            }
        }, matchWrap(top = 6))
        card.addView(outlinedButton("Conferir corridas com a Uber").apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, PainelActivity::class.java).putExtra(PainelActivity.EXTRA_PAGE, "conferir")) }
        }, matchWrap(top = 6))
        card.addView(outlinedButton("Importar correção de corridas").apply {
            setOnClickListener { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
        }, matchWrap(top = 6))
        card.addView(text(
            "Para arquivos de correção preparados a partir das suas ofertas, do GPS e do resumo da Uber. Uma cópia dos dados anteriores fica guardada.",
            12f, Ui.MUTED
        ))

        val diag = SwitchMaterial(this).apply {
            text = "Modo diagnóstico"
            setTextColor(Ui.TEXT)
            isChecked = prefs.diagnostic
            setOnCheckedChangeListener { _, checked -> prefs.diagnostic = checked }
        }
        card.addView(diag, matchWrap(top = 16))
        card.addView(text(
            "Registra os textos que o radar lê na tela da Uber/99. Se o cartão não aparecer, ligue, receba algumas ofertas e envie o arquivo para ajustar a leitura.",
            12f, Ui.MUTED
        ))
        card.addView(outlinedButton("Enviar diagnóstico").apply {
            setOnClickListener {
                if (!OfferLog.share(this@MainActivity, OfferLog.diagFile(this@MainActivity), "text/plain", "Diagnóstico — Radar Corridas")) {
                    toast("O diagnóstico está vazio")
                }
            }
        }, matchWrap(top = 6))
        card.addView(outlinedButton("Limpar diagnóstico").apply {
            setOnClickListener {
                OfferLog.diagFile(this@MainActivity).delete()
                toast("Diagnóstico apagado")
            }
        }, matchWrap(top = 4))
    }

    // ---------- Atualizações ----------

    private fun updateStatus() {
        val active = RadarService.instance != null
        if (::homeStatusTitle.isInitialized) {
            homeStatusTitle.text = if (active) "Radar ligado" else "Radar desligado"
            homeStatusSub.text = if (active) "Leitura de ofertas ativa" else "Toque para ligar a leitura de ofertas"
            homePill.text = if (active) "Tudo certo" else "Ligar"
            val c = if (active) Ui.ACCENT else Colors.RED
            homePill.setTextColor(c)
            homePill.background = GradientDrawable().apply { setColor(Color.argb(30, Color.red(c), Color.green(c), Color.blue(c))); cornerRadius = dp(99).toFloat() }
        }
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Colors.GREEN else Colors.RED)
        }
        statusText.text = if (active) "Leitura de ofertas ativa" else "Leitura de ofertas desligada"
        activateBtn.text = if (active) "Abrir Acessibilidade" else "Ativar leitura de ofertas"
    }

    private fun updateCount() {
        val n = OfferLog.offerCount(this)
        val t = TripLog.count(this)
        countText.text = (if (n == 1) "1 oferta salva" else "$n ofertas salvas") +
            "  ·  " + (if (t == 1) "1 corrida registrada" else "$t corridas registradas")
    }

    // ---------- Corridas e percurso ----------

    private lateinit var tripChecklist: TextView
    private lateinit var tripPermBtn: MaterialButton

    private fun has(perm: String) = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun hasLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun hasBackgroundLocation() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    private fun hasNotifications() =
        Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
    private fun batteryFree(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun buildTrips(col: LinearLayout) {
        val permCard = section(col, "Leitura e permissões")
        var card = permCard
        val sw = SwitchMaterial(this).apply {
            text = "Registrar as corridas aceitas"
            setTextColor(Ui.TEXT)
            isChecked = prefs.trackTrips
            setOnCheckedChangeListener { _, checked -> prefs.trackTrips = checked }
        }
        card.addView(sw, matchWrap())
        card.addView(text(
            "Quando você aceita uma corrida, o Radar liga o GPS e grava o percurso e os tempos: " +
                "do aceite até chegar ao passageiro, a espera, a viagem até o destino. " +
                "O GPS desliga sozinho quando a Uber volta para a tela inicial.",
            12f, Ui.MUTED
        ))
        // Voz para o passageiro (página própria)
        card = section(col, "Voz com o passageiro")
        card.addView(SwitchMaterial(this).apply {
            text = "Falar com o passageiro"
            setTextColor(Ui.TEXT)
            isChecked = prefs.talkToPassenger
            setOnCheckedChangeListener { _, checked -> prefs.talkToPassenger = checked }
        }, matchWrap(top = 14))
        card.addView(text(
            "O Radar fala com o passageiro quando ele embarca e quando o carro chega perto do destino. " +
                "Sai pelo som do carro quando o celular está conectado por Bluetooth ou Android Auto, abaixando a música enquanto fala.",
            12f, Ui.MUTED
        ))
        // Textos da voz (editáveis)
        fun editor(titulo: String, atual: String, padrao: String, salvar: (String) -> Unit, ouvir: () -> Unit) {
            card.addView(text(titulo, 15f, Ui.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
            val ed = EditText(this).apply {
                setText(atual.ifBlank { padrao })
                setTextColor(Ui.TEXT)
                textSize = 16f
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                background = rounded(Ui.SURFACE_2, 14f)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun afterTextChanged(e: android.text.Editable?) { val t = e?.toString()?.trim() ?: ""; salvar(if (t == padrao) "" else t) }
                })
            }
            card.addView(ed, matchWrap(top = 6))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(MaterialButton(this).apply { isAllCaps = false; letterSpacing = 0f; text = "Ouvir"; setIconResource(R.drawable.ic_volume_up); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; setOnClickListener { ed.clearFocus(); ouvir() } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                isAllCaps = false; letterSpacing = 0f; text = "Voltar ao original"; setOnClickListener { ed.setText(padrao) } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
            card.addView(row, matchWrap(top = 6))
        }
        card.addView(text(
            "Edite como a voz fala: tire ou ponha vírgulas para mudar as pausas, use ! para mais animação. " +
                "{nome} vira o primeiro nome do passageiro e {rua} a rua do destino.",
            12f, Ui.MUTED
        ).apply { setPadding(0, dp(12), 0, 0) })
        editor("Quando o passageiro embarca", prefs.voiceStart, Voz.PADRAO_INICIO, { prefs.voiceStart = it }) {
            Voz.inicio(this, "Ana", "Avenida Paulista, 1000, Bela Vista")
        }
        editor("Quando chega ao destino", prefs.voiceEnd, Voz.PADRAO_CHEGADA, { prefs.voiceEnd = it }) {
            Voz.chegada(this, "Ana")
        }
        // Despedida: a quantos metros do destino
        val despLabel = text("", 15f, Ui.TEXT).apply { setPadding(0, dp(12), 0, 0) }
        fun despText(v: Int) { despLabel.text = "Despedida a $v m do destino" }
        despText(prefs.farewellDist)
        card.addView(despLabel)
        card.addView(Slider(this).apply {
            valueFrom = 50f; valueTo = 500f; stepSize = 10f
            value = prefs.farewellDist.toFloat().coerceIn(50f, 500f)
            addOnChangeListener { _, v, _ -> prefs.farewellDist = v.toInt(); despText(v.toInt()) }
        }, matchWrap(top = 2))
        card.addView(text("Toca quando o carro chega a essa distância do endereço de destino, andando ou parado. Se não der, toca quando você finaliza a viagem.", 12f, Ui.MUTED))

        // Altura da própria fala (vale no Android Auto, onde o volume do celular não muda nada)
        val volLabel = text("", 15f, Ui.TEXT).apply { setPadding(0, dp(14), 0, 0) }
        fun volText(v: Int) { volLabel.text = "Altura da voz: $v%" }
        volText(prefs.voiceGain)
        card.addView(volLabel)
        card.addView(Slider(this).apply {
            valueFrom = 50f; valueTo = 300f; stepSize = 10f
            value = prefs.voiceGain.toFloat().coerceIn(50f, 300f)
            addOnChangeListener { _, v, _ -> prefs.voiceGain = v.toInt(); volText(v.toInt()) }
        }, matchWrap(top = 2))
        card.addView(text("100% é a voz normal. Acima disso o Radar reforça o som da fala, mesmo com o volume do celular no máximo e no Android Auto. A música abaixa enquanto a voz fala.", 12f, Ui.MUTED))
        card.addView(text("Canal do áudio", 15f, Ui.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        val canal = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val canais = listOf("navegacao" to "Navegação", "assistente" to "Assistente", "midia" to "Mídia")
        val bCanal = canais.map { (k, nome) -> k to outlinedButton(nome).apply { id = View.generateViewId(); letterSpacing = 0f; setPadding(dp(4), paddingTop, dp(4), paddingBottom) } }
        bCanal.forEach { (_, b) -> canal.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        canal.check((bCanal.firstOrNull { it.first == prefs.voiceChannel } ?: bCanal[0]).second.id)
        canal.addOnButtonCheckedListener { _, id, on -> if (on) bCanal.firstOrNull { it.second.id == id }?.let { prefs.voiceChannel = it.first } }
        card.addView(canal, matchWrap(top = 6))
        card.addView(text("Navegação é o canal do Waze. Assistente é o canal em que o Android Auto lê as mensagens do WhatsApp. Mídia é o da música. Teste no carro com o botão abaixo.", 12f, Ui.MUTED))
        card.addView(MaterialButton(this).apply {
            isAllCaps = false
            letterSpacing = 0f
            text = "Ouvir exemplo"
            setIconResource(R.drawable.ic_volume_up); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { Voz.inicio(this@MainActivity, "Ana", "Avenida Paulista, 1000, Bela Vista") }
        }, matchWrap(top = 8))
        card.addView(MaterialButton(this).apply {
            isAllCaps = false
            letterSpacing = 0f
            text = "Escolher outra voz"
            setOnClickListener { escolherVoz() }
        }, matchWrap(top = 8))

        // Voz de estúdio (ElevenLabs): fala natural, com a chave do próprio motorista
        card.addView(SwitchMaterial(this).apply {
            text = "Voz de estúdio (ElevenLabs)"
            setTextColor(Ui.TEXT)
            isChecked = prefs.studioVoice
            setOnCheckedChangeListener { _, checked -> prefs.studioVoice = checked }
        }, matchWrap(top = 18))
        card.addView(text(
            "Fala como gente, sem as pausas da voz do celular. Usa a sua conta da ElevenLabs: cada corrida gasta cerca de 270 créditos " +
                "(as duas falas, geradas logo no aceite). O primeiro nome do passageiro e a rua do destino vão para a ElevenLabs para gerar a fala. " +
                "Sem internet ou sem créditos, o Radar usa a voz do celular.",
            12f, Ui.MUTED
        ))
        val keyIn = EditText(this).apply {
            hint = if (prefs.elevenKey.isBlank()) "Cole aqui a sua chave da ElevenLabs" else "Chave salva. Cole outra para trocar"
            setHintTextColor(Ui.MUTED)
            setTextColor(Ui.TEXT)
            textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            minHeight = dp(56)
            setOnFocusChangeListener { v, has ->
                val t = (v as EditText).text.toString().trim()
                if (!has && t.isNotBlank()) { prefs.elevenKey = t; v.setText(""); v.hint = "Chave salva. Cole outra para trocar" }
            }
            setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }
        }
        card.addView(keyIn, matchWrap(top = 8))
        val vozBtn = MaterialButton(this).apply { isAllCaps = false; letterSpacing = 0f
            setIconResource(R.drawable.ic_chevron_right); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END }
        fun nomeVoz() = VozEstudio.VOZES.firstOrNull { it.first == prefs.studioVoiceId } ?: VozEstudio.VOZES[0]
        vozBtn.text = "Voz de estúdio: ${nomeVoz().second}"
        vozBtn.setOnClickListener {
            keyIn.text.toString().trim().takeIf { it.isNotBlank() }?.let { prefs.elevenKey = it; keyIn.setText("") }
            val vs = VozEstudio.VOZES
            var sel = vs.indexOfFirst { it.first == nomeVoz().first }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Voz de estúdio")
                .setSingleChoiceItems(vs.map { "${it.second} · ${it.third}" }.toTypedArray(), sel) { _, w ->
                    sel = w; prefs.studioVoiceId = vs[w].first
                    if (prefs.elevenKey.isNotBlank()) VozEstudio.falar(this, Voz.textoInicio(this, "Ana", "Avenida Paulista")) {
                        Toast.makeText(this, "Não consegui gerar a voz: confira a chave e a internet", Toast.LENGTH_LONG).show()
                    }
                }
                .setPositiveButton("Usar esta") { _, _ -> prefs.studioVoiceId = vs[sel].first; vozBtn.text = "Voz de estúdio: ${vs[sel].second}" }
                .show()
        }
        card.addView(vozBtn, matchWrap(top = 8))
        card = permCard
        tripChecklist = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(tripChecklist)
        tripPermBtn = MaterialButton(this).apply {
            isAllCaps = false
            setOnClickListener { nextPermissionStep() }
        }
        card.addView(tripPermBtn, matchWrap(top = 8))
        updateTripChecklist()
    }

    /** Lista as vozes em português do celular; tocar numa delas fala o exemplo e guarda a escolha. */
    private fun escolherVoz() {
        Voz.preparar(this) {
            val vs = Voz.vozes()
            if (vs.isEmpty()) { abrirConfigVoz(); return@preparar }
            val atual = prefs.voiceName.ifBlank { vs.first().name }
            val nomes = vs.mapIndexed { i, v ->
                val q = when { v.quality >= 400 -> "muito natural"; v.quality >= 300 -> "natural"; else -> "simples" }
                "Voz ${i + 1} · $q" + (if (v.isNetworkConnectionRequired) " · usa internet" else "")
            }.toTypedArray()
            var sel = vs.indexOfFirst { it.name == atual }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Voz para o passageiro")
                .setSingleChoiceItems(nomes, sel) { _, w -> sel = w; Voz.usarVoz(this, vs[w].name) }
                .setPositiveButton("Usar esta") { _, _ -> prefs.voiceName = vs[sel].name }
                .setNeutralButton("Mais vozes") { _, _ -> abrirConfigVoz() }
                .show()
        }
    }

    /** Configurações de voz do Android (para baixar vozes melhores ou trocar o motor). */
    private fun abrirConfigVoz() {
        try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (_: Exception) {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
        }
    }

    private fun updateTripChecklist() {
        if (!::tripChecklist.isInitialized) return
        fun mark(ok: Boolean) = if (ok) "{check}" else "{x}"
        tripChecklist.text = Icones.texto(this, listOf(
            "${mark(hasLocation())} Localização precisa",
            "${mark(hasBackgroundLocation())} Localização \"o tempo todo\"",
            "${mark(hasNotifications())} Notificações (aviso de GPS ligado)",
            "${mark(batteryFree())} Sem restrição de bateria"
        ).joinToString("\n"), tripChecklist.textSize)
        val done = hasLocation() && hasBackgroundLocation() && hasNotifications() && batteryFree()
        tripPermBtn.text = if (done) "Tudo pronto" else "Liberar próxima permissão"
        tripPermBtn.isEnabled = !done
    }

    private fun nextPermissionStep() {
        when {
            !hasLocation() -> ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10
            )
            !hasBackgroundLocation() -> {
                toast("Escolha \"Permitir o tempo todo\"")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 11)
            }
            !hasNotifications() && Build.VERSION.SDK_INT >= 33 ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
            !batteryFree() -> try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateTripChecklist()
    }

    // ---------- Radares de velocidade ----------

    private lateinit var speedInfo: TextView
    private lateinit var finesText: TextView
    private lateinit var myCamsList: LinearLayout
    private lateinit var hiddenText: TextView

    private fun refreshMyCams() {
        if (!::myCamsList.isInitialized) return
        myCamsList.removeAllViews()
        val mine = SpeedCams.userList(this)
        if (mine.isEmpty()) {
            myCamsList.addView(text("Nenhum radar marcado por você.", 13f, Ui.MUTED))
            return
        }
        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", PT_BR)
        for ((i, c) in mine.sortedByDescending { it.id }.withIndex()) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Ui.SURFACE_2, 12f)
            }
            val title = "{camera} Radar ${mine.size - i} · marcado em ${fmt.format(java.util.Date(c.id))}" +
                (if (c.limit == null) " · sem limite definido" else " · ${c.limit} km/h")
            box.addView(text(title, 14f, if (c.limit == null) Colors.YELLOW else Ui.TEXT, bold = true))
            box.addView(text(String.format(java.util.Locale.US, "%.5f, %.5f", c.lat, c.lng), 11f, Ui.MUTED))
            val group = MaterialButtonToggleGroup(this).apply {
                isSingleSelection = true
            }
            val ids = mutableMapOf<Int, Int>()
            for (lim in listOf(30, 40, 50, 60, 70, 80, 90)) {
                val b = outlinedButton("$lim").apply {
                    id = View.generateViewId()
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                }
                ids[b.id] = lim
                group.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (c.limit == lim) group.check(b.id)
            }
            group.addOnButtonCheckedListener { _, id, checked ->
                if (checked) {
                    SpeedCams.setUserLimit(this, c, ids[id])
                    refreshMyCams()
                }
            }
            box.addView(group, matchWrap(top = 4))
            box.addView(outlinedButton("Abrir no mapa").apply {
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${c.lat},${c.lng}?q=${c.lat},${c.lng}(Radar)")))
                    } catch (_: Exception) {
                        toast("Nenhum app de mapa encontrado")
                    }
                }
            }, matchWrap(top = 2))
            box.addView(outlinedButton("Apagar").apply {
                setOnClickListener {
                    SpeedCams.removeUser(this@MainActivity, c)
                    refreshMyCams()
                    updateSpeedInfo()
                }
            }, matchWrap(top = 2))
            myCamsList.addView(box, matchWrap(top = 8))
        }
    }

    private fun buildSpeed(col: LinearLayout) {
        val card = section(col, "Radares de velocidade")
        card.addView(SwitchMaterial(this).apply {
            text = "Avisar radares e velocidade"
            setTextColor(Ui.TEXT)
            isChecked = prefs.speedAlerts
            setOnCheckedChangeListener { _, checked ->
                prefs.speedAlerts = checked
                if (!checked) SpeedWatch.stop(this@MainActivity)
                else if (SpeedCams.isStale(this@MainActivity)) updateCams()
            }
        }, matchWrap())
        card.addView(text(
            "Com a Uber ou a 99 aberta, o GPS fica ligado. Ao chegar perto de um radar, aparece no topo da tela " +
                "a placa com o limite e a sua velocidade: verde (ok), amarelo (acima do limite, mas dentro da tolerância) " +
                "ou vermelho (multa), com bipe. Depois do radar, se passou da tolerância, chega uma notificação de possível multa.",
            12f, Ui.MUTED
        ))
        card.addView(SwitchMaterial(this).apply {
            text = "Bipe sonoro"
            setTextColor(Ui.TEXT)
            isChecked = prefs.speedSound
            setOnCheckedChangeListener { _, checked -> prefs.speedSound = checked }
        }, matchWrap(top = 6))
        val bipeLabel = text("", 15f, Ui.TEXT).apply { setPadding(0, dp(6), 0, 0) }
        fun bipeText(v: Int) { bipeLabel.text = "Altura dos bipes: $v%" }
        bipeText(prefs.beepGain)
        card.addView(bipeLabel)
        card.addView(Slider(this).apply {
            valueFrom = 10f; valueTo = 100f; stepSize = 5f
            value = prefs.beepGain.toFloat().coerceIn(10f, 100f)
            addOnChangeListener { _, v, _ -> prefs.beepGain = v.toInt(); bipeText(v.toInt()) }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(sl: Slider) {}
                override fun onStopTrackingTouch(sl: Slider) { Beeper.play(this@MainActivity, Beeper.Kind.RADAR_A_FRENTE) }
            })
        }, matchWrap(top = 2))
        card.addView(text("Vale para radar, valeta e pontos bons. Sai no canal do Waze e abaixa a música por um instante. Solte o controle para ouvir.", 12f, Ui.MUTED))

        card.addView(text("Distância do aviso (radar e valeta)", 15f, Ui.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        card.addView(text(
            "O aviso aparece mais cedo quanto mais rápido você estiver.",
            12f, Ui.MUTED
        ))
        card.addView(distanceSlider("Até 50 km/h", prefs.alertDistSlow) { prefs.alertDistSlow = it }, matchWrap(top = 6))
        card.addView(distanceSlider("De 50 a 80 km/h", prefs.alertDistMid) { prefs.alertDistMid = it }, matchWrap(top = 2))
        card.addView(distanceSlider("Acima de 80 km/h", prefs.alertDistFast) { prefs.alertDistFast = it }, matchWrap(top = 2))

        speedInfo = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(speedInfo)
        card.addView(outlinedButton("Atualizar lista de radares").apply {
            setOnClickListener { updateCams() }
        }, matchWrap(top = 6))
        card.addView(outlinedButton("Testar aviso de radar").apply {
            setOnClickListener {
                if (RadarService.instance == null) toast("Ative a leitura de ofertas primeiro")
                else SpeedWatch.demo(this@MainActivity)
            }
        }, matchWrap(top = 4))

        card.addView(text("Radar errado?", 15f, Ui.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        card.addView(text(
            "Se aparecer um radar que não existe, ou de outra pista, segure o aviso por 1 segundo. " +
                "Ele some e não aparece mais, e fica anotado no arquivo de dados para corrigirmos a lista.",
            12f, Ui.MUTED
        ))
        hiddenText = text("", 13f, Ui.TEXT).apply { setPadding(0, dp(4), 0, 0) }
        card.addView(hiddenText)
        card.addView(outlinedButton("Mostrar de novo os radares removidos").apply {
            setOnClickListener {
                SpeedCams.unhideAll(this@MainActivity)
                toast("Radares removidos voltaram")
                updateSpeedInfo()
            }
        }, matchWrap(top = 4))

        card.addView(text("Faltou um radar no mapa?", 15f, Ui.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        card.addView(text(
            "Aperte o volume + duas vezes rápido logo depois de passar por ele (com a Uber/99 aberta). " +
                "Depois, aqui embaixo, escolha o limite de velocidade dele. Da próxima vez, o Radar avisa como os outros.",
            12f, Ui.MUTED
        ))
        myCamsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(myCamsList, matchWrap(top = 6))

        card.addView(text("Possíveis multas recentes", 15f, Ui.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        finesText = text("", 13f, Ui.TEXT).apply { setPadding(0, dp(4), 0, 0) }
        card.addView(finesText)

        card.addView(text(
            "A velocidade vem do GPS. Os radares da cidade de SP vêm da lista oficial da CET (atualizada todo mês); " +
                "os de fora da cidade, do OpenStreetMap (mapa colaborativo), onde pode faltar algum. Usa as permissões de localização da seção acima. " +
                "Para saber das multas oficiais, ative o SNE no app Carteira Digital de Trânsito.",
            12f, Ui.MUTED
        ).apply { setPadding(0, dp(10), 0, 0) })
        updateSpeedInfo()
    }

    // ---------- Eventos de hoje ----------

    private lateinit var eventsList: LinearLayout
    private lateinit var eventsInfo: TextView

    private fun buildEvents(col: LinearLayout) {
        val card = section(col, "Eventos de hoje")
        card.addView(text(
            "Todo dia à tarde eu pesquiso os eventos de São Paulo (teatros, shows, jogos, SESCs, formaturas) e calculo o horário " +
                "provável de saída do público. Com a Uber/99 aberta, quando você estiver perto de um evento perto da hora da saída, " +
                "aparece um aviso rosa. A oferta também ganha {star} se o destino for perto de uma saída.",
            12f, Ui.MUTED
        ))
        card.addView(SwitchMaterial(this).apply {
            text = "Avisar saídas de eventos"
            setTextColor(Ui.TEXT)
            isChecked = prefs.eventAlerts
            setOnCheckedChangeListener { _, c -> prefs.eventAlerts = c }
        }, matchWrap(top = 8))
        card.addView(SwitchMaterial(this).apply {
            text = "Mostrar eventos no card da oferta"
            setTextColor(Ui.TEXT)
            isChecked = prefs.eventsInOffer
            setOnCheckedChangeListener { _, c -> prefs.eventsInOffer = c }
        }, matchWrap(top = 4))
        card.addView(text("Ponto bom favorito perto do destino sempre aparece no card e vem antes de evento.", 12f, Ui.MUTED))
        val radLbl = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(10), 0, 0) }
        fun setRad(v: Int) { radLbl.text = String.format(PT_BR, "Avisar a até %.1f km do evento", v / 1000f) }
        setRad(prefs.eventRadiusM)
        card.addView(radLbl)
        val rad = Slider(this)
        rad.valueFrom = 500f
        rad.valueTo = 8000f
        rad.stepSize = 500f
        rad.value = (((prefs.eventRadiusM + 250) / 500) * 500).coerceIn(500, 8000).toFloat()
        rad.setLabelFormatter { v -> String.format(PT_BR, "%.1f km", v / 1000f) }
        rad.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        rad.addOnChangeListener { _, v, _ -> prefs.eventRadiusM = v.toInt(); setRad(v.toInt()) }
        card.addView(rad, matchWrap())

        eventsInfo = text("", 13f, Ui.MUTED).apply { setPadding(0, dp(8), 0, 0) }
        card.addView(eventsInfo)
        card.addView(outlinedButton("Atualizar agora").apply {
            setOnClickListener {
                eventsInfo.text = "Baixando a agenda…"
                Events.refresh(this@MainActivity, force = true) { n ->
                    toast(if (n == null) "Não consegui baixar a agenda" else "$n eventos na agenda")
                    refreshEvents()
                }
            }
        }, matchWrap(top = 6))
        eventsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(eventsList, matchWrap(top = 8))
        refreshEvents()
        // Baixa a agenda do dia ao abrir o app (se estiver velha)
        Events.refresh(this) { refreshEvents() }
    }

    private fun refreshEvents() {
        if (!::eventsList.isInitialized) return
        eventsList.removeAllViews()
        val evs = Events.today(this).sortedBy { it.endMin }
        val f = Events.file(this)
        eventsInfo.text = if (!f.exists()) "Agenda ainda não baixada." else
            "${evs.size} evento(s) hoje · atualizada às " + java.text.SimpleDateFormat("HH:mm", PT_BR).format(java.util.Date(f.lastModified()))
        for (e in evs) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Ui.SURFACE_2, 12f)
            }
            box.addView(text("{${e.icon}} ${e.name}", 15f, Ui.TEXT, bold = true))
            val start = e.startMin?.let { "começa ${Spot.hhmm(it)} · " } ?: ""
            val aud = e.audience?.let { " · ~$it pessoas" } ?: ""
            box.addView(text("${e.place}\n${start}saída ~${Spot.hhmm(e.endMin)}$aud", 13f, Ui.MUTED))
            if (e.lat != null && e.lng != null) {
                box.addView(MaterialButton(this).apply {
                    text = "Ir com Waze"
                    isAllCaps = false
                    makeTall(this)
                    setOnClickListener { Nav.waze(this@MainActivity, e.lat!!, e.lng!!) }
                }, matchWrap(top = 6))
            } else {
                box.addView(text("Endereço não localizado no mapa", 12f, Colors.YELLOW))
            }
            eventsList.addView(box, matchWrap(top = 8))
        }
    }

    // ---------- Pontos bons ----------

    private lateinit var spotsList: LinearLayout

    private fun buildSpots(col: LinearLayout) {
        val card = section(col, "Meus pontos bons")
        card.addView(text(
            "Marque os lugares que você sabe que rendem em certos dias e horários (teatro, bar, igreja, faculdade…). " +
                "Com a Uber/99 aberta, quando você estiver perto de um deles no horário, aparece um aviso roxo com a distância e um toque de sino.",
            12f, Ui.MUTED
        ))
        card.addView(SwitchMaterial(this).apply {
            text = "Avisar quando estiver perto"
            setTextColor(Ui.TEXT)
            isChecked = prefs.spotAlerts
            setOnCheckedChangeListener { _, checked -> prefs.spotAlerts = checked }
        }, matchWrap(top = 8))
        val starLbl = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(12), 0, 0) }
        fun setStar(v: Float) {
            starLbl.text = Icones.texto(this@MainActivity, String.format(PT_BR, "{star} na oferta quando o destino ficar a até %.0f km de um ponto no horário dele", v), starLbl.textSize)
        }
        setStar(prefs.spotStarKm)
        card.addView(starLbl)
        val star = Slider(this)
        star.valueFrom = 1f
        star.valueTo = 15f
        star.stepSize = 1f
        star.value = prefs.spotStarKm.coerceIn(1f, 15f)
        star.setLabelFormatter { v -> "${v.toInt()} km" }
        star.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        star.addOnChangeListener { _, v, _ ->
            prefs.spotStarKm = v
            setStar(v)
        }
        card.addView(star, matchWrap())

        card.addView(MaterialButton(this).apply {
            text = "Adicionar ponto onde estou agora"
            isAllCaps = false
            setOnClickListener { addSpotHere() }
        }, matchWrap(top = 8))
        card.addView(outlinedButton("Adicionar por endereço").apply {
            setOnClickListener { addSpotByAddress() }
        }, matchWrap(top = 4))
        card.addView(outlinedButton("Testar aviso de ponto").apply {
            setOnClickListener {
                if (RadarService.instance == null) toast("Ative a leitura de ofertas primeiro")
                else SpotWatch.demo(this@MainActivity)
            }
        }, matchWrap(top = 4))
        spotsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(spotsList, matchWrap(top = 10))
        refreshSpots()
    }

    private fun refreshSpots() {
        if (!::spotsList.isInitialized) return
        spotsList.removeAllViews()
        val all = Spots.all(this)
        if (all.isEmpty()) {
            spotsList.addView(text("Nenhum ponto cadastrado ainda.", 13f, Ui.MUTED))
            return
        }
        for (sp in all.sortedBy { it.startMin }) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Ui.SURFACE_2, 12f)
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(text("{star} " + sp.name, 16f, Ui.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(SwitchMaterial(this).apply {
                isChecked = sp.enabled
                setOnCheckedChangeListener { _, c ->
                    sp.enabled = c
                    Spots.save(this@MainActivity)
                }
            })
            box.addView(header)
            val radius = if (sp.radiusM >= 1000) String.format(PT_BR, "%.1f km", sp.radiusM / 1000f) else "${sp.radiusM} m"
            box.addView(text("${sp.daysText()} · ${sp.windowText()} · avisa a $radius", 13f, Ui.MUTED))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(outlinedButton("Editar").apply { setOnClickListener { editSpot(sp, isNew = false) } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            row.addView(outlinedButton("Apagar").apply {
                setOnClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Apagar \"${sp.name}\"?")
                        .setPositiveButton("Apagar") { _, _ ->
                            Spots.remove(this@MainActivity, sp)
                            refreshSpots()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(row, matchWrap(top = 4))
            val go = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            go.addView(MaterialButton(this).apply {
                text = "Ir com Waze"
                isAllCaps = false
                makeTall(this)
                setOnClickListener { Nav.waze(this@MainActivity, sp.lat, sp.lng) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            go.addView(MaterialButton(this).apply {
                text = "Ir com Uber"
                isAllCaps = false
                makeTall(this)
                setBackgroundColor(Color.parseColor("#2A3441"))
                setOnClickListener { Nav.uber(this@MainActivity, sp.lat, sp.lng, sp.name) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(go, matchWrap(top = 4))
            spotsList.addView(box, matchWrap(top = 8))
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun addSpotHere() {
        if (!hasLocation()) {
            toast("Libere a localização em Ajustes > Leitura e permissões")
            return
        }
        val recent = Geo.lastKnown(this)
        if (recent != null) {
            newSpotAt(recent.lat, recent.lng)
            return
        }
        toast("Buscando sua localização…")
        val lm = getSystemService(android.location.LocationManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null, mainExecutor) { loc ->
                    if (loc != null) newSpotAt(loc.latitude, loc.longitude) else toast("Não consegui pegar a localização. Tente perto da janela.")
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, { loc ->
                    newSpotAt(loc.latitude, loc.longitude)
                }, mainLooper)
            }
        } catch (e: Exception) {
            toast("Não consegui pegar a localização")
        }
    }

    private fun addSpotByAddress() {
        val input = EditText(this).apply {
            hint = "Ex.: Teatro Renault, Av. Brigadeiro Luís Antônio 411"
            setSingleLine(true)
        }
        val wrap = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Endereço do ponto")
            .setView(wrap)
            .setPositiveButton("Buscar") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isEmpty()) return@setPositiveButton
                toast("Buscando…")
                Geo.search(this, q) { places -> pickPlace(q, places) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Mostra o que a busca achou para o motorista confirmar antes de criar o ponto. */
    private fun pickPlace(q: String, places: List<Geo.Place>) {
        val name = q.substringBefore(",").trim().take(40)
        if (places.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Não encontrei \"$q\"")
                .setMessage("Digite o endereço com rua, número e bairro (ex.: Rua Augusta, 609, Consolação). Você pode pôr o nome do lugar antes: \"$name, Rua …\".")
                .setPositiveButton("Tentar de novo") { _, _ -> addSpotByAddress() }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val items = places.map { (if (it.exact) "" else "(só a região) ") + it.label }.toTypedArray()
        val anyExact = places.any { it.exact }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (anyExact) "Qual é o lugar?" else "Achei só a região, não o endereço")
            .setItems(items) { _, i -> newSpotAt(places[i].at.lat, places[i].at.lng, name) }
            .setNeutralButton("Digitar outro") { _, _ -> addSpotByAddress() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun newSpotAt(lat: Double, lng: Double, name: String = "") {
        val sp = Spot(System.currentTimeMillis(), name, lat, lng, setOf(6, 7), 21 * 60 + 30, 23 * 60, 2000, true)
        editSpot(sp, isNew = true)
    }

    private fun editSpot(sp: Spot, isNew: Boolean) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        box.addView(text("Nome", 13f, Ui.MUTED))
        val nameIn = EditText(this).apply {
            setText(sp.name)
            hint = "Ex.: Teatro Renault"
            setSingleLine(true)
        }
        box.addView(nameIn, matchWrap())

        box.addView(text("Dias", 13f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        val days = sp.days.toMutableSet()
        val dayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (d in Spot.DAY_ORDER) {
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = Spot.DAY_LETTER[d]
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                fun paint() {
                    val on = d in days
                    setBackgroundColor(if (on) Ui.ACCENT else Color.TRANSPARENT)
                    setTextColor(if (on) Color.parseColor("#06231B") else Ui.ACCENT)
                    strokeColor = ColorStateList.valueOf(Ui.ACCENT)
                }
                paint()
                setOnClickListener {
                    if (d in days) days.remove(d) else days.add(d)
                    paint()
                }
            }
            dayRow.addView(b, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(4) })
        }
        box.addView(dayRow, matchWrap())

        var start = sp.startMin
        var end = sp.endMin
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startBtn = outlinedButton("")
        val endBtn = outlinedButton("")
        fun paintTimes() {
            startBtn.text = "Das " + Spot.hhmm(start)
            endBtn.text = "Até " + Spot.hhmm(end)
        }
        paintTimes()
        startBtn.setOnClickListener {
            android.app.TimePickerDialog(this, { _, h, m -> start = h * 60 + m; paintTimes() }, start / 60, start % 60, true).show()
        }
        endBtn.setOnClickListener {
            android.app.TimePickerDialog(this, { _, h, m -> end = h * 60 + m; paintTimes() }, end / 60, end % 60, true).show()
        }
        timeRow.addView(startBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        timeRow.addView(endBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(text("Horário bom", 13f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        box.addView(timeRow, matchWrap())

        box.addView(text("Avisar quando estiver a", 13f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        val radii = listOf(500, 1000, 2000, 3000, 5000)
        val radiusGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val radiusIds = mutableMapOf<Int, Int>()
        for (r in radii) {
            val b = outlinedButton(if (r >= 1000) "${r / 1000} km" else "$r m").apply {
                id = View.generateViewId()
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
            }
            radiusIds[b.id] = r
            radiusGroup.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val selected = radii.minByOrNull { kotlin.math.abs(it - sp.radiusM) } ?: 2000
        radiusIds.entries.firstOrNull { it.value == selected }?.let { radiusGroup.check(it.key) }
        box.addView(radiusGroup, matchWrap())

        val scroll = ScrollView(this).apply { addView(box) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (isNew) "Novo ponto bom" else "Editar ponto")
            .setView(scroll)
            .setPositiveButton("Salvar") { _, _ ->
                val name = nameIn.text.toString().trim().ifEmpty { "Ponto bom" }
                if (days.isEmpty()) {
                    toast("Escolha pelo menos um dia")
                    return@setPositiveButton
                }
                if (start == end) {
                    toast("O horário de início e fim não pode ser igual")
                    return@setPositiveButton
                }
                sp.name = name
                sp.days = days.toSet()
                sp.startMin = start
                sp.endMin = end
                sp.radiusM = radiusIds[radiusGroup.checkedButtonId] ?: 2000
                if (isNew) Spots.add(this, sp) else Spots.save(this)
                toast("Ponto salvo")
                refreshSpots()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Valetas ----------

    private lateinit var hazardInfo: TextView

    private fun buildHazards(col: LinearLayout) {
        val card = section(col, "Valetas e buracos")
        card.addView(text(
            "Marque as valetas que o Waze não mostra. Da próxima vez que passar por ali, o Radar avisa com a distância, " +
                "no mesmo aviso dos radares (em laranja).",
            12f, Ui.MUTED
        ))
        card.addView(text(
            "Como marcar sem olhar para o celular:\n" +
                "1. Aperte o volume − duas vezes rápido logo depois de passar pela valeta\n" +
                "2. Ou segure o botão \"R\" por meio segundo\n" +
                "Aparece \"Valeta marcada\" com um bipe. Errou? Toque no aviso para desfazer.",
            13f, Ui.TEXT
        ).apply { setPadding(0, dp(10), 0, 0) })
        card.addView(text(
            "A valeta vale para os dois sentidos da rua. Apareceu uma valeta que não existe? Segure o aviso por 1 segundo: " +
                "ela some e não volta, nem pelo solavanco automático.",
            13f, Ui.TEXT
        ).apply { setPadding(0, dp(8), 0, 0) })
        card.addView(text(
            "O volume só vira atalho enquanto a Uber/99 está aberta (GPS ligado). Fora disso, funciona normal. " +
                "Depois de instalar esta versão, desligue e ligue de novo o Radar em Acessibilidade para liberar o atalho do volume.",
            12f, Ui.MUTED
        ).apply { setPadding(0, dp(6), 0, 0) })

        val delayLabel = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(12), 0, 0) }
        card.addView(delayLabel)
        fun setDelay(v: Float) {
            delayLabel.text = if (v == 0f) "Marcar no ponto exato do aperto"
            else String.format(PT_BR, "Marcar onde o carro estava %.1f s antes do aperto", v)
        }
        setDelay(prefs.valetaDelaySec)
        val delay = Slider(this)
        delay.valueFrom = 0f
        delay.valueTo = 5f
        delay.stepSize = 0.5f
        delay.value = (Math.round(prefs.valetaDelaySec * 2f) / 2f).coerceIn(0f, 5f)
        delay.setLabelFormatter { v -> String.format(PT_BR, "%.1f s", v) }
        delay.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        delay.addOnChangeListener { _, v, _ ->
            prefs.valetaDelaySec = v
            setDelay(v)
        }
        card.addView(delay, matchWrap())
        card.addView(text(
            "Vale para o volume − e para o botão \"R\". Se costuma apertar bem depois de passar, aumente. " +
                "Se aperta antes, ao ver a valeta chegando, use 0.",
            12f, Ui.MUTED
        ))

        card.addView(SwitchMaterial(this).apply {
            text = "Avisar valetas marcadas"
            setTextColor(Ui.TEXT)
            isChecked = prefs.valetaAlerts
            setOnCheckedChangeListener { _, checked -> prefs.valetaAlerts = checked }
        }, matchWrap(top = 12))
        card.addView(SwitchMaterial(this).apply {
            text = "Marcar sozinho quando o carro der um solavanco forte"
            setTextColor(Ui.TEXT)
            isChecked = prefs.valetaAuto
            setOnCheckedChangeListener { _, checked ->
                prefs.valetaAuto = checked
                if (checked && SpeedWatch.active) BumpDetector.start(this@MainActivity)
                if (!checked) BumpDetector.stop()
            }
        }, matchWrap(top = 4))
        card.addView(text(
            "Usa o sensor de movimento do celular. Precisa do celular firme no suporte. Também pode marcar lombadas e buracos: " +
                "se marcar algo errado, toque no aviso para desfazer.",
            12f, Ui.MUTED
        ))

        val sensLabel = text("", 14f, Ui.TEXT).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(sensLabel)
        fun setSens(v: Float) {
            sensLabel.text = "Sensibilidade do solavanco: " + when {
                v < 0.34f -> "baixa (só os fortes)"
                v < 0.67f -> "média"
                else -> "alta (qualquer tranco)"
            }
        }
        setSens(prefs.valetaSensitivity)
        val sens = Slider(this)
        sens.valueFrom = 0f
        sens.valueTo = 1f
        sens.stepSize = 0f
        sens.value = prefs.valetaSensitivity.coerceIn(0f, 1f)
        sens.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sens.addOnChangeListener { _, v, _ ->
            prefs.valetaSensitivity = v
            setSens(v)
        }
        card.addView(sens, matchWrap())

        hazardInfo = text("", 14f, Ui.TEXT, bold = true).apply { setPadding(0, dp(6), 0, 0) }
        card.addView(hazardInfo)
        card.addView(outlinedButton("Testar aviso de valeta").apply {
            setOnClickListener {
                if (RadarService.instance == null) toast("Ative a leitura de ofertas primeiro")
                else HazardWatch.demo(this@MainActivity)
            }
        }, matchWrap(top = 6))
        card.addView(outlinedButton("Apagar a última valeta marcada").apply {
            setOnClickListener {
                toast(if (Hazards.deleteLast(this@MainActivity)) "Última valeta apagada" else "Nenhuma valeta marcada")
                updateHazardInfo()
            }
        }, matchWrap(top = 4))
        updateHazardInfo()
    }

    private fun updateHazardInfo() {
        if (!::hazardInfo.isInitialized) return
        val n = Hazards.count(this)
        val auto = Hazards.autoCount(this)
        hazardInfo.text = when (n) {
            0 -> "Nenhuma valeta marcada ainda"
            1 -> "1 valeta marcada" + (if (auto == 1) " (automática)" else "")
            else -> "$n valetas marcadas" + (if (auto > 0) " ($auto automáticas)" else "")
        }
    }

    private fun distanceSlider(label: String, initial: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lbl = text("", 14f, Ui.TEXT)
        fun setLbl(v: Int) { lbl.text = "$label: avisar a $v m" }
        setLbl(initial)
        box.addView(lbl)
        val sl = Slider(this)
        sl.valueFrom = 50f
        sl.valueTo = 600f
        sl.stepSize = 25f
        sl.value = ((initial / 25) * 25).coerceIn(50, 600).toFloat()
        sl.setLabelFormatter { v -> "${v.toInt()} m" }
        sl.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sl.addOnChangeListener { _, v, _ ->
            onChange(v.toInt())
            setLbl(v.toInt())
        }
        box.addView(sl, matchWrap())
        return box
    }

    private fun updateCams() {
        if (SpeedCams.downloading) {
            toast("Já estou baixando a lista")
            return
        }
        speedInfo.text = "Baixando a lista de radares… (pode levar 1 ou 2 minutos)"
        SpeedCams.download(this) { n, err ->
            if (n != null) toast("$n radares carregados") else toast("Não consegui baixar agora. Tente com internet boa.")
            if (err != null && n == null) speedInfo.text = "Falha ao baixar a lista ($err)"
            else updateSpeedInfo()
        }
    }

    private fun updateSpeedInfo() {
        if (!::speedInfo.isInitialized) return
        if (SpeedCams.count == 0) SpeedCams.load(this)
        if (!SpeedCams.downloading) {
            val at = SpeedCams.updatedAt(this)
            speedInfo.text = if (SpeedCams.count == 0) {
                "Lista de radares ainda não baixada"
            } else {
                val date = java.text.SimpleDateFormat("dd/MM/yyyy", PT_BR).format(java.util.Date(at))
                "${SpeedCams.count} radares na Grande SP" +
                    (if (SpeedCams.cetCount > 0) " (${SpeedCams.cetCount} oficiais da CET)" else "") +
                    " · atualizada em $date" +
                    (if (SpeedWatch.active) "\nAlerta ativo agora" else "")
            }
        }
        refreshMyCams()
        if (::hiddenText.isInitialized) {
            val h = SpeedCams.hiddenCount(this)
            hiddenText.text = if (h == 0) "Nenhum radar removido." else "$h radar(es) removido(s) por você."
        }
        val fines = PassLog.recentFines(this)
        val total = PassLog.count(this)
        finesText.text = Icones.texto(this, if (fines.isEmpty()) {
            if (total == 0) "Nenhuma passagem por radar registrada ainda." else "Nenhuma nas $total passagens registradas. {like}"
        } else {
            fines.joinToString("\n") { "{alerta} $it" }
        }, finesText.textSize)
    }

    private fun updateCostSummary() {
        val fuel = prefs.fuelPrice / prefs.kmPerLiter.coerceAtLeast(1f)
        costSummary.text = String.format(
            PT_BR, "Custo por km rodado: R$ %.2f\n(combustível R$ %.2f + aluguel R$ %.2f + outros R$ %.2f)",
            prefs.costPerKm, fuel, prefs.rentPerKm, prefs.extraCostKm
        )
    }

    private fun refreshPreview() {
        if (!::previewBox.isInitialized) return
        previewBox.removeAllViews()
        val card = CardView.build(this, Evaluator.evaluate(Offer.sample(), prefs))
        card.alpha = prefs.opacity
        previewBox.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    // ---------- Ajudantes ----------

    /** Arredonda para um passo que faça sentido em cada métrica. */
    private fun snap(m: Metric, v: Float): Float {
        val step = when (m) {
            Metric.HORA, Metric.LUCRO_HORA -> 1f
            Metric.LUCRO -> 0.5f
            Metric.KM -> 0.05f
            Metric.MIN, Metric.NOTA -> 0.01f
        }
        return (v / step).roundToInt() * step
    }

    private fun rangeText(m: Metric, bad: Float, good: Float): CharSequence {
        val sb = SpannableStringBuilder()
        val a = "Ruim até ${formatThreshold(m, bad)}"
        sb.append(a)
        sb.setSpan(ForegroundColorSpan(Colors.RED), 0, a.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("   ·   ")
        val start = sb.length
        val b = "Bom a partir de ${formatThreshold(m, good)}"
        sb.append(b)
        sb.setSpan(ForegroundColorSpan(Colors.GREEN), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun numberField(label: String, initial: Float, onChange: (Float) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(label, 13f, Ui.MUTED))
        val input = EditText(this).apply {
            setText(String.format(PT_BR, "%.2f", initial))
            keyListener = DigitsKeyListener.getInstance("0123456789,.")
            setTextColor(Ui.TEXT)
            textSize = 17f
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(Ui.ACCENT)
        }
        input.doAfterTextChanged { e ->
            val v = e?.toString()?.trim()?.replace(",", ".")?.toFloatOrNull()
            if (v != null && v >= 0f) {
                onChange(v)
                updateCostSummary()
                refreshPreview()
            }
        }
        box.addView(input, matchWrap())
        return box
    }

    /**
     * Cada builder pede uma "seção": o Status vai para o card do topo da tela inicial;
     * as outras viram um bloco dentro da página do card correspondente.
     */
    private fun section(@Suppress("UNUSED_PARAMETER") parent: LinearLayout, title: String): LinearLayout {
        if (title == "Status") { pages["Leitura e permissões"]?.body?.addView(statusCard, matchWrap(top = 16)); return statusCard }
        val key = if (title == "Prévia do cartão") "Cartão e ganhos" else title
        val page = pages[key] ?: return LinearLayout(this)
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = rounded(Ui.SURFACE, 18f)
        }
        if (title == "Prévia do cartão") { block.addView(text(title, 16f, Ui.TEXT, bold = true).apply { setPadding(0, 0, 0, dp(8)) }); previewBlock = block }
        page.body.addView(block, matchWrap(top = 16))
        return block
    }

    /** Botões mais altos e fáceis de tocar. */
    private fun makeTall(b: MaterialButton) {
        b.minHeight = dp(56)
        b.minimumHeight = dp(56)
        b.insetTop = 0
        b.insetBottom = 0
        b.cornerRadius = dp(14)
        b.textSize = 17f
    }

    /** Aplica o estilo alto a todos os botões da tela (menos os pequenos de dias/limites). */
    private fun tallAll(v: View) {
        if (v is SwitchMaterial) {
            // Chave grande (o motorista pode estar no carro): área de toque de 64dp e trilho maior
            v.minHeight = dp(64)
            v.minimumHeight = dp(64)
            v.switchMinWidth = dp(64)
            val checked = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            // bolinha de 28dp com 5dp de folga em volta: não encosta na borda do trilho (38dp de altura)
            v.thumbDrawable = android.graphics.drawable.InsetDrawable(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(dp(28), dp(28))
                setColor(Color.WHITE)
                // no tema claro a bolinha branca some sobre o card branco: ganha um contorno
                if (!Ui.escuro) setStroke(dp(1), Color.parseColor("#C3CAD1"))
            }, dp(5))
            v.isUseMaterialThemeColors = false   // senão o Material pinta a bolinha com a cor do tema
            v.thumbTintList = if (Ui.escuro) ColorStateList(checked, intArrayOf(Color.WHITE, Color.parseColor("#C9D1D9"))) else null
            v.trackDrawable = GradientDrawable().apply {
                cornerRadius = dp(19).toFloat()
                setSize(dp(64), dp(38))
                setColor(Color.WHITE)
            }
            v.trackTintList = ColorStateList(checked, intArrayOf(Ui.ACCENT, if (Ui.escuro) Color.parseColor("#3A434D") else Color.parseColor("#BFC7CF")))
            v.textSize = maxOf(16f, v.textSize / resources.displayMetrics.scaledDensity)
            v.switchPadding = dp(12)
            return
        }
        if (v is MaterialButton) {
            val inGroup = v.parent is MaterialButtonToggleGroup
            val fixedSmall = (v.layoutParams?.height ?: 0) > 0
            if (!inGroup && !fixedSmall) makeTall(v)
            return
        }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) tallAll(v.getChildAt(i))
    }

    /**
     * Tamanho de leitura no carro: nada abaixo de 14sp, textos de conteúdo um pouco maiores.
     * Explicações (cor apagada) podem ficar menores; o resto cresce.
     */
    private fun readable(size: Float, color: Int): Float = when {
        size <= 11f -> 13f
        size < 14f -> if (color == Ui.MUTED) 14f else 15f
        size < 16f -> size + 2f
        size < 18f -> size + 1f
        else -> size
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = readable(size, color)
        text = Icones.texto(this@MainActivity, s, textSize)   // "{nome}" vira ícone
        setLineSpacing(0f, 1.12f)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun outlinedButton(label: String) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            setTextColor(Ui.ACCENT)
            strokeColor = ColorStateList.valueOf(Ui.ACCENT)
            makeTall(this)
        }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
