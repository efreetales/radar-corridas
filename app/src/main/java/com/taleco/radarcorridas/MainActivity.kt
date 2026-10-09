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
        buildSpeed(col)
        buildHazards(col)
        buildSpots(col)
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
        updateSpeedInfo()
        updateHazardInfo()
        refreshSpots()
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
        card.addView(outlinedButton("Exportar dados (ofertas, corridas, rotas, radares e valetas)").apply {
            setOnClickListener {
                val ctx = this@MainActivity
                val files = listOf(OfferLog.offersFile(ctx), TripLog.tripsFile(ctx), TripLog.routesFile(ctx), PassLog.file(ctx), Hazards.file(ctx), Spots.file(ctx), SpeedCams.reportFile(ctx))
                if (!OfferLog.shareAll(ctx, files, "Dados — Radar Corridas")) toast("Nenhum dado salvo ainda")
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
            myCamsList.addView(text("Nenhum radar marcado por você.", 13f, Colors.MUTED))
            return
        }
        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", PT_BR)
        for ((i, c) in mine.sortedByDescending { it.id }.withIndex()) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Colors.SURFACE_2, 12f)
            }
            val title = "📷 Radar ${mine.size - i} · marcado em ${fmt.format(java.util.Date(c.id))}" +
                (if (c.limit == null) " · sem limite definido" else " · ${c.limit} km/h")
            box.addView(text(title, 14f, if (c.limit == null) Colors.YELLOW else Colors.TEXT, bold = true))
            box.addView(text(String.format(java.util.Locale.US, "%.5f, %.5f", c.lat, c.lng), 11f, Colors.MUTED))
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
            setTextColor(Colors.TEXT)
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
            12f, Colors.MUTED
        ))
        card.addView(SwitchMaterial(this).apply {
            text = "Bipe sonoro (volume de mídia; sai no som do carro se conectado)"
            setTextColor(Colors.TEXT)
            isChecked = prefs.speedSound
            setOnCheckedChangeListener { _, checked -> prefs.speedSound = checked }
        }, matchWrap(top = 6))

        speedInfo = text("", 14f, Colors.TEXT).apply { setPadding(0, dp(10), 0, 0) }
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

        card.addView(text("Radar errado?", 15f, Colors.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        card.addView(text(
            "Se aparecer um radar que não existe, ou de outra pista, segure o aviso por 1 segundo. " +
                "Ele some e não aparece mais, e fica anotado no arquivo de dados para corrigirmos a lista.",
            12f, Colors.MUTED
        ))
        hiddenText = text("", 13f, Colors.TEXT).apply { setPadding(0, dp(4), 0, 0) }
        card.addView(hiddenText)
        card.addView(outlinedButton("Mostrar de novo os radares removidos").apply {
            setOnClickListener {
                SpeedCams.unhideAll(this@MainActivity)
                toast("Radares removidos voltaram")
                updateSpeedInfo()
            }
        }, matchWrap(top = 4))

        card.addView(text("Faltou um radar no mapa?", 15f, Colors.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        card.addView(text(
            "Aperte o volume + duas vezes rápido logo depois de passar por ele (com a Uber/99 aberta). " +
                "Depois, aqui embaixo, escolha o limite de velocidade dele. Da próxima vez, o Radar avisa como os outros.",
            12f, Colors.MUTED
        ))
        myCamsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(myCamsList, matchWrap(top = 6))

        card.addView(text("Possíveis multas recentes", 15f, Colors.TEXT, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        finesText = text("", 13f, Colors.TEXT).apply { setPadding(0, dp(4), 0, 0) }
        card.addView(finesText)

        card.addView(text(
            "A velocidade vem do GPS. Os radares da cidade de SP vêm da lista oficial da CET (atualizada todo mês); " +
                "os de fora da cidade, do OpenStreetMap (mapa colaborativo), onde pode faltar algum. Usa as permissões de localização da seção acima. " +
                "Para saber das multas oficiais, ative o SNE no app Carteira Digital de Trânsito.",
            12f, Colors.MUTED
        ).apply { setPadding(0, dp(10), 0, 0) })
        updateSpeedInfo()
    }

    // ---------- Pontos bons ----------

    private lateinit var spotsList: LinearLayout

    private fun buildSpots(col: LinearLayout) {
        val card = section(col, "Meus pontos bons")
        card.addView(text(
            "Marque os lugares que você sabe que rendem em certos dias e horários (teatro, bar, igreja, faculdade…). " +
                "Com a Uber/99 aberta, quando você estiver perto de um deles no horário, aparece um aviso roxo com a distância e um toque de sino.",
            12f, Colors.MUTED
        ))
        card.addView(SwitchMaterial(this).apply {
            text = "Avisar quando estiver perto"
            setTextColor(Colors.TEXT)
            isChecked = prefs.spotAlerts
            setOnCheckedChangeListener { _, checked -> prefs.spotAlerts = checked }
        }, matchWrap(top = 8))
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
            spotsList.addView(text("Nenhum ponto cadastrado ainda.", 13f, Colors.MUTED))
            return
        }
        for (sp in all.sortedBy { it.startMin }) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Colors.SURFACE_2, 12f)
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(text("★ " + sp.name, 16f, Colors.TEXT, bold = true),
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
            box.addView(text("${sp.daysText()} · ${sp.windowText()} · avisa a $radius", 13f, Colors.MUTED))
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
            spotsList.addView(box, matchWrap(top = 8))
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun addSpotHere() {
        if (!hasLocation()) {
            toast("Libere a localização na seção \"Corridas e percurso\"")
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
                val query = if (q.contains("São Paulo", ignoreCase = true)) q else "$q, São Paulo"
                Geo.geocode(this, query) { at ->
                    if (at == null) toast("Endereço não encontrado. Tente com número e bairro.")
                    else newSpotAt(at.lat, at.lng, q.substringBefore(",").take(40))
                }
            }
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
        box.addView(text("Nome", 13f, Colors.MUTED))
        val nameIn = EditText(this).apply {
            setText(sp.name)
            hint = "Ex.: Teatro Renault"
            setSingleLine(true)
        }
        box.addView(nameIn, matchWrap())

        box.addView(text("Dias", 13f, Colors.MUTED).apply { setPadding(0, dp(10), 0, 0) })
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
                    setBackgroundColor(if (on) Colors.ACCENT else Color.TRANSPARENT)
                    setTextColor(if (on) Color.parseColor("#06231B") else Colors.ACCENT)
                    strokeColor = ColorStateList.valueOf(Colors.ACCENT)
                }
                paint()
                setOnClickListener {
                    if (d in days) days.remove(d) else days.add(d)
                    paint()
                }
            }
            dayRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(3) })
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
        box.addView(text("Horário bom", 13f, Colors.MUTED).apply { setPadding(0, dp(10), 0, 0) })
        box.addView(timeRow, matchWrap())

        box.addView(text("Avisar quando estiver a", 13f, Colors.MUTED).apply { setPadding(0, dp(10), 0, 0) })
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
            12f, Colors.MUTED
        ))
        card.addView(text(
            "Como marcar sem olhar para o celular:\n" +
                "• Aperte o volume − duas vezes rápido logo depois de passar pela valeta\n" +
                "• Ou segure o botão \"R\" por meio segundo\n" +
                "Aparece \"Valeta marcada\" com um bipe. Errou? Toque no aviso para desfazer.",
            13f, Colors.TEXT
        ).apply { setPadding(0, dp(10), 0, 0) })
        card.addView(text(
            "O volume só vira atalho enquanto a Uber/99 está aberta (GPS ligado). Fora disso, funciona normal. " +
                "Depois de instalar esta versão, desligue e ligue de novo o Radar em Acessibilidade para liberar o atalho do volume.",
            12f, Colors.MUTED
        ).apply { setPadding(0, dp(6), 0, 0) })

        val delayLabel = text("", 14f, Colors.TEXT).apply { setPadding(0, dp(12), 0, 0) }
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
            12f, Colors.MUTED
        ))

        card.addView(SwitchMaterial(this).apply {
            text = "Avisar valetas marcadas"
            setTextColor(Colors.TEXT)
            isChecked = prefs.valetaAlerts
            setOnCheckedChangeListener { _, checked -> prefs.valetaAlerts = checked }
        }, matchWrap(top = 12))
        card.addView(SwitchMaterial(this).apply {
            text = "Marcar sozinho quando o carro der um solavanco forte"
            setTextColor(Colors.TEXT)
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
            12f, Colors.MUTED
        ))

        val sensLabel = text("", 14f, Colors.TEXT).apply { setPadding(0, dp(10), 0, 0) }
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

        hazardInfo = text("", 14f, Colors.TEXT, bold = true).apply { setPadding(0, dp(6), 0, 0) }
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
        finesText.text = if (fines.isEmpty()) {
            if (total == 0) "Nenhuma passagem por radar registrada ainda." else "Nenhuma nas $total passagens registradas. 👍"
        } else {
            fines.joinToString("\n") { "⚠️ $it" }
        }
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
