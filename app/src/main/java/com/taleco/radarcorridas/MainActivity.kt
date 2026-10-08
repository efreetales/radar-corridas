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

    private lateinit var prefs: Prefs
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var activateBtn: MaterialButton
    private lateinit var previewBox: FrameLayout
    private lateinit var costSummary: TextView
    private lateinit var countText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Colors.BG)
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(40))
        }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(text("Radar Corridas", 28f, Colors.TEXT, bold = true))
        col.addView(text(
            "Mostra na hora se a corrida compensa. Só lê a oferta: nunca aceita nem recusa por você.",
            14f, Colors.MUTED
        ).apply { setPadding(0, dp(4), 0, dp(8)) })

        buildStatus(col)
        buildPreview(col)
        buildMetrics(col)
        buildCosts(col)
        buildAppearance(col)
        buildTrips(col)
        buildData(col)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        col.addView(text("Versão $version", 12f, Colors.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateCount()
        updateTripChecklist()
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
        statusText = text("", 16f, Colors.TEXT, bold = true)
        row.addView(statusDot)
        row.addView(statusText)
        card.addView(row)

        activateBtn = MaterialButton(this).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        card.addView(activateBtn, matchWrap(top = 12))

        card.addView(text(
            "Em Acessibilidade, procure \"Radar Corridas\" (às vezes dentro de \"Apps instalados\") e ative.\n\n" +
                "Se a opção aparecer bloqueada: abra Configurações → Apps → Radar Corridas → menu ⋮ → " +
                "\"Permitir configurações restritas\" e tente de novo.",
            13f, Colors.MUTED
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
            13f, Colors.MUTED
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
            13f, Colors.MUTED
        ))

        for (m in Metric.values()) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(8))
                background = rounded(Colors.SURFACE_2, 12f)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(
                text(m.label, 17f, Colors.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            val show = SwitchMaterial(this).apply {
                text = "No cartão"
                setTextColor(Colors.MUTED)
                isChecked = prefs.isShown(m)
                setOnCheckedChangeListener { _, checked ->
                    prefs.setShown(m, checked)
                    refreshPreview()
                }
            }
            header.addView(show)
            box.addView(header)
            box.addView(text(m.help, 12f, Colors.MUTED))

            val rangeLabel = text("", 14f, Colors.TEXT).apply { setPadding(0, dp(8), 0, 0) }
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
            13f, Colors.MUTED
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
        costSummary = text("", 14f, Colors.TEXT, bold = true).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(costSummary)
        updateCostSummary()
    }

    private fun buildAppearance(col: LinearLayout) {
        val card = section(col, "Aparência")

        card.addView(text("Posição do cartão", 14f, Colors.TEXT))
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

        val opLabel = text("", 14f, Colors.TEXT).apply { setPadding(0, dp(14), 0, 0) }
        card.addView(opLabel)
        fun setOpLabel(v: Float) { opLabel.text = "Opacidade: ${(v * 100).roundToInt()}%" }
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
            setTextColor(Colors.TEXT)
            isChecked = prefs.showBubble
            setOnCheckedChangeListener { _, checked ->
                prefs.showBubble = checked
                RadarService.instance?.refreshBubble()
            }
        }
        card.addView(bubble, matchWrap(top = 6))
        card.addView(text(
            "O botão fica sobre a tela, mostra que o radar está ligado e abre esta tela com um toque. Arraste para mudar de lugar.",
            12f, Colors.MUTED
        ))
    }

    private fun buildData(col: LinearLayout) {
        val card = section(col, "Dados")

        val save = SwitchMaterial(this).apply {
            text = "Salvar as ofertas vistas"
            setTextColor(Colors.TEXT)
            isChecked = prefs.logOffers
            setOnCheckedChangeListener { _, checked -> prefs.logOffers = checked }
        }
        card.addView(save, matchWrap())
        card.addView(text(
            "Guarda valor, distância, tempo, endereços e o veredito de cada oferta, inclusive as que você recusa. " +
                "Serve para descobrir onde e quando aparecem as melhores corridas.",
            12f, Colors.MUTED
        ))
        countText = text("", 14f, Colors.TEXT).apply { setPadding(0, dp(8), 0, 0) }
        card.addView(countText)
        card.addView(outlinedButton("Exportar dados (ofertas, corridas e rotas)").apply {
            setOnClickListener {
                val ctx = this@MainActivity
                val files = listOf(OfferLog.offersFile(ctx), TripLog.tripsFile(ctx), TripLog.routesFile(ctx))
                if (!OfferLog.shareAll(ctx, files, "Dados — Radar Corridas")) toast("Nenhum dado salvo ainda")
            }
        }, matchWrap(top = 6))

        val cloud = SwitchMaterial(this).apply {
            text = "Enviar para a nuvem"
            setTextColor(Colors.TEXT)
            isChecked = CloudSync.enabled(this@MainActivity)
        }
        card.addView(cloud, matchWrap(top = 16))
        card.addView(text(
            "Manda sozinho, a cada 3 minutos, as ofertas, corridas, rotas e o diagnóstico para o seu banco na nuvem " +
                "(Supabase). Fica todo o histórico guardado, sem precisar exportar. Sem internet, envia depois.",
            12f, Colors.MUTED
        ))
        val cloudStatus = text(CloudSync.status(this), 13f, Colors.TEXT).apply { setPadding(0, dp(6), 0, 0) }
        card.addView(cloudStatus)
        cloud.setOnCheckedChangeListener { _, checked ->
            CloudSync.setEnabled(this@MainActivity, checked)
            cloudStatus.text = CloudSync.status(this@MainActivity)
        }
        card.addView(outlinedButton("Enviar agora").apply {
            setOnClickListener {
                cloudStatus.text = "Enviando…"
                CloudSync.syncNow(this@MainActivity) { cloudStatus.text = CloudSync.status(this@MainActivity) }
            }
        }, matchWrap(top = 6))

        val diag = SwitchMaterial(this).apply {
            text = "Modo diagnóstico"
            setTextColor(Colors.TEXT)
            isChecked = prefs.diagnostic
            setOnCheckedChangeListener { _, checked -> prefs.diagnostic = checked }
        }
        card.addView(diag, matchWrap(top = 16))
        card.addView(text(
            "Registra os textos que o radar lê na tela da Uber/99. Se o cartão não aparecer, ligue, receba algumas ofertas e envie o arquivo para ajustar a leitura.",
            12f, Colors.MUTED
        ))
        card.addView(outlinedButton("Enviar diagnóstico").apply {
            setOnClickListener {
                if (!OfferLog.shareAll(this@MainActivity, OfferLog.diagFiles(this@MainActivity), "Diagnóstico — Radar Corridas", "text/plain")) {
                    toast("O diagnóstico está vazio")
                }
            }
        }, matchWrap(top = 6))
        card.addView(outlinedButton("Limpar diagnóstico").apply {
            setOnClickListener {
                OfferLog.diagFiles(this@MainActivity).forEach { it.delete() }
                toast("Diagnóstico apagado")
            }
        }, matchWrap(top = 4))
    }

    // ---------- Atualizações ----------

    private fun updateStatus() {
        val active = RadarService.instance != null
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
        val card = section(col, "Corridas e percurso")
        val sw = SwitchMaterial(this).apply {
            text = "Registrar as corridas aceitas"
            setTextColor(Colors.TEXT)
            isChecked = prefs.trackTrips
            setOnCheckedChangeListener { _, checked -> prefs.trackTrips = checked }
        }
        card.addView(sw, matchWrap())
        val route = SwitchMaterial(this).apply {
            text = "Registrar o trajeto do turno"
            setTextColor(Colors.TEXT)
            isChecked = prefs.trackRoute
            setOnCheckedChangeListener { _, checked ->
                prefs.trackRoute = checked
                TrackingService.sync(this@MainActivity, prefs)
            }
        }
        card.addView(route, matchWrap(top = 8))
        card.addView(text(
            "Enquanto você está online, grava por onde passou, onde ficou parado e a velocidade, " +
                "também quando está sem passageiro. Usa as posições que a Uber já pede ao GPS, sem gastar bateria a mais. " +
                "É o que permite o mapa de calor de onde vale a pena esperar.",
            12f, Colors.MUTED
        ))
        card.addView(text(
            "Quando você aceita uma corrida, o Radar liga o GPS e grava o percurso e os tempos: " +
                "do aceite até chegar ao passageiro, a espera, a viagem até o destino. " +
                "O GPS desliga sozinho quando a Uber volta para a tela inicial.",
            12f, Colors.MUTED
        ))
        tripChecklist = text("", 14f, Colors.TEXT).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(tripChecklist)
        tripPermBtn = MaterialButton(this).apply {
            isAllCaps = false
            setOnClickListener { nextPermissionStep() }
        }
        card.addView(tripPermBtn, matchWrap(top = 8))
        updateTripChecklist()
    }

    private fun updateTripChecklist() {
        if (!::tripChecklist.isInitialized) return
        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        tripChecklist.text = listOf(
            "${mark(hasLocation())} Localização precisa",
            "${mark(hasBackgroundLocation())} Localização \"o tempo todo\"",
            "${mark(hasNotifications())} Notificações (aviso de GPS ligado)",
            "${mark(batteryFree())} Sem restrição de bateria"
        ).joinToString("\n")
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
        box.addView(text(label, 13f, Colors.MUTED))
        val input = EditText(this).apply {
            setText(String.format(PT_BR, "%.2f", initial))
            keyListener = DigitsKeyListener.getInstance("0123456789,.")
            setTextColor(Colors.TEXT)
            textSize = 17f
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(Colors.ACCENT)
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

    private fun section(parent: LinearLayout, title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Colors.SURFACE, 16f)
        }
        card.addView(text(title, 19f, Colors.TEXT, bold = true).apply { setPadding(0, 0, 0, dp(8)) })
        parent.addView(card, matchWrap(top = 16))
        return card
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun outlinedButton(label: String) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            setTextColor(Colors.ACCENT)
            strokeColor = ColorStateList.valueOf(Colors.ACCENT)
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
