package com.taleco.radarcorridas

import android.content.Context
import android.content.SharedPreferences

/**
 * Cada métrica que pode aparecer no cartão.
 * bad = até esse valor a corrida é ruim (vermelho)
 * good = a partir desse valor a corrida é boa (verde)
 * entre os dois = médio (amarelo)
 */
enum class Metric(
    val key: String,
    val label: String,
    val defBad: Float,
    val defGood: Float,
    val sliderMin: Float,
    val sliderMax: Float,
    val money: Boolean,
    val defShown: Boolean,
    val help: String
) {
    HORA("hora", "R$/Hora", 55f, 75f, 0f, 150f, true, true,
        "Valor da corrida dividido pelo tempo total (busca + viagem)."),
    MIN("min", "R$/Min", 0.92f, 1.25f, 0f, 3f, true, true,
        "Valor da corrida por minuto, contando a busca."),
    KM("km", "R$/Km", 1.70f, 3.50f, 0f, 6f, true, true,
        "Valor da corrida dividido pelos km totais (busca + viagem)."),
    NOTA("nota", "Nota", 4.30f, 4.85f, 4f, 5f, false, false,
        "Nota do passageiro, quando o app mostra."),
    LUCRO("lucro", "Lucro", 8f, 15f, 0f, 60f, true, false,
        "Valor da corrida menos o custo dos km rodados (combustível e outros custos)."),
    LUCRO_HORA("lucrohora", "Lucro/Hora", 35f, 55f, 0f, 150f, true, false,
        "Lucro da corrida dividido pelo tempo total.");
}

enum class CardPosition(val label: String) { TOPO("Topo"), MEIO("Meio"), BAIXO("Baixo") }

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("radar", Context.MODE_PRIVATE)

    fun bad(m: Metric): Float = sp.getFloat("bad_${m.key}", m.defBad)
    fun good(m: Metric): Float = sp.getFloat("good_${m.key}", m.defGood)
    fun isShown(m: Metric): Boolean = sp.getBoolean("show_${m.key}", m.defShown)

    fun setRange(m: Metric, bad: Float, good: Float) {
        sp.edit().putFloat("bad_${m.key}", bad).putFloat("good_${m.key}", good).apply()
    }

    fun setShown(m: Metric, shown: Boolean) {
        sp.edit().putBoolean("show_${m.key}", shown).apply()
    }

    // Custos do carro
    var kmPerLiter: Float
        get() = sp.getFloat("km_por_litro", 10f)
        set(v) = sp.edit().putFloat("km_por_litro", v).apply()

    var fuelPrice: Float
        get() = sp.getFloat("preco_litro", 6.00f)
        set(v) = sp.edit().putFloat("preco_litro", v).apply()

    var extraCostKm: Float
        get() = sp.getFloat("custo_extra_km", 0f)
        set(v) = sp.edit().putFloat("custo_extra_km", v).apply()

    var rentPerWeek: Float
        get() = sp.getFloat("aluguel_semana", 0f)
        set(v) = sp.edit().putFloat("aluguel_semana", v).apply()

    var kmPerWeek: Float
        get() = sp.getFloat("km_semana", 1000f)
        set(v) = sp.edit().putFloat("km_semana", v).apply()

    /** Parte do aluguel (ou parcela) que cabe a cada km rodado. */
    val rentPerKm: Double
        get() = if (rentPerWeek <= 0f) 0.0 else rentPerWeek.toDouble() / kmPerWeek.coerceAtLeast(1f).toDouble()

    /** Custo por km rodado: combustível + aluguel diluído + outros custos. */
    val costPerKm: Double
        get() = fuelPrice.toDouble() / kmPerLiter.coerceAtLeast(1f).toDouble() + rentPerKm + extraCostKm.toDouble()

    // Corridas aceitas
    var trackTrips: Boolean
        get() = sp.getBoolean("registrar_corridas", true)
        set(v) = sp.edit().putBoolean("registrar_corridas", v).apply()

    // Distância dos avisos (radar e valeta), conforme a sua velocidade
    var alertDistSlow: Int   // até 50 km/h
        get() = sp.getInt("dist_aviso_lento", 150)
        set(v) = sp.edit().putInt("dist_aviso_lento", v).apply()

    var alertDistMid: Int    // de 50 a 80 km/h
        get() = sp.getInt("dist_aviso_medio", 200)
        set(v) = sp.edit().putInt("dist_aviso_medio", v).apply()

    var alertDistFast: Int   // 80 km/h ou mais
        get() = sp.getInt("dist_aviso_rapido", 300)
        set(v) = sp.edit().putInt("dist_aviso_rapido", v).apply()

    /** A que distância avisar, dada a velocidade atual. */
    fun alertDistance(kmh: Double): Double = when {
        kmh <= 50.0 -> alertDistSlow
        kmh < 80.0 -> alertDistMid
        else -> alertDistFast
    }.toDouble()

    // Radares de velocidade
    var speedAlerts: Boolean
        get() = sp.getBoolean("alerta_radares", true)
        set(v) = sp.edit().putBoolean("alerta_radares", v).apply()

    var speedSound: Boolean
        get() = sp.getBoolean("som_radares", true)
        set(v) = sp.edit().putBoolean("som_radares", v).apply()

    // Valetas
    var valetaAlerts: Boolean
        get() = sp.getBoolean("alerta_valetas", true)
        set(v) = sp.edit().putBoolean("alerta_valetas", v).apply()

    var valetaAuto: Boolean
        get() = sp.getBoolean("valetas_auto", true)
        set(v) = sp.edit().putBoolean("valetas_auto", v).apply()

    /** Segundos entre passar pela valeta e apertar o botão (a marcação volta esse tempo no percurso). */
    var valetaDelaySec: Float
        get() = sp.getFloat("valetas_atraso", 1.5f)
        set(v) = sp.edit().putFloat("valetas_atraso", v).apply()

    /** 0 = só solavancos muito fortes, 1 = qualquer solavanco. */
    var valetaSensitivity: Float
        get() = sp.getFloat("valetas_sensibilidade", 0.5f)
        set(v) = sp.edit().putFloat("valetas_sensibilidade", v).apply()

    // Eventos do dia
    var eventAlerts: Boolean
        get() = sp.getBoolean("alerta_eventos", true)
        set(v) = sp.edit().putBoolean("alerta_eventos", v).apply()

    /** Mostrar "destino perto de evento" (com estrela) no card da oferta (ponto bom sempre aparece e tem prioridade). */
    var eventsInOffer: Boolean
        get() = sp.getBoolean("eventos_no_card", true)
        set(v) = sp.edit().putBoolean("eventos_no_card", v).apply()

    var eventRadiusM: Int
        get() = sp.getInt("raio_eventos", 3000)
        set(v) = sp.edit().putInt("raio_eventos", v).apply()

    // Pontos bons
    /** Estrela no cartão da oferta quando o destino fica a até X km de um ponto bom no horário. */
    var spotStarKm: Float
        get() = sp.getFloat("estrela_km", 6f)
        set(v) = sp.edit().putFloat("estrela_km", v).apply()

    var spotAlerts: Boolean
        get() = sp.getBoolean("alerta_pontos", true)
        set(v) = sp.edit().putBoolean("alerta_pontos", v).apply()

    // Aparência
    var position: CardPosition
        get() = CardPosition.values().getOrElse(sp.getInt("posicao", 0)) { CardPosition.TOPO }
        set(v) = sp.edit().putInt("posicao", v.ordinal).apply()

    var opacity: Float
        get() = sp.getFloat("opacidade", 0.92f)
        set(v) = sp.edit().putFloat("opacidade", v).apply()

    var showBubble: Boolean
        get() = sp.getBoolean("mostrar_bolha", true)
        set(v) = sp.edit().putBoolean("mostrar_bolha", v).apply()

    var bubbleX: Int
        get() = sp.getInt("bolha_x", 0)
        set(v) = sp.edit().putInt("bolha_x", v).apply()

    var bubbleY: Int
        get() = sp.getInt("bolha_y", 400)
        set(v) = sp.edit().putInt("bolha_y", v).apply()

    /** Botão R travado: não sai do lugar ao arrastar (o carro balança). */
    /** Falar com o passageiro no começo e no fim da viagem (pelo som do carro). */
    var talkToPassenger: Boolean
        get() = sp.getBoolean("falar_passageiro", true)
        set(v) = sp.edit().putBoolean("falar_passageiro", v).apply()

    /** Nome da voz escolhida para falar com o passageiro ("" = a melhor disponível). */
    var voiceName: String
        get() = sp.getString("voz_nome", "") ?: ""
        set(v) = sp.edit().putString("voz_nome", v).apply()

    /** Textos que a voz fala (vazio = o texto padrão). {nome} e {rua} são trocados na hora. */
    var voiceStart: String
        get() = sp.getString("voz_texto_inicio", "") ?: ""
        set(v) = sp.edit().putString("voz_texto_inicio", v).apply()
    var voiceEnd: String
        get() = sp.getString("voz_texto_chegada", "") ?: ""
        set(v) = sp.edit().putString("voz_texto_chegada", v).apply()

    /** Volume da fala para o passageiro: sobe a mídia até X% enquanto fala (0 = não mexe). */
    var voiceVolume: Int
        get() = sp.getInt("voz_volume", 85)
        set(v) = sp.edit().putInt("voz_volume", v).apply()

    /** Voz de estúdio (ElevenLabs) para falar com o passageiro. */
    var studioVoice: Boolean
        get() = sp.getBoolean("voz_estudio", false)
        set(v) = sp.edit().putBoolean("voz_estudio", v).apply()

    /** Chave da ElevenLabs do motorista (fica só no celular). */
    var elevenKey: String
        get() = sp.getString("eleven_chave", "") ?: ""
        set(v) = sp.edit().putString("eleven_chave", v.trim()).apply()

    var studioVoiceId: String
        get() = sp.getString("voz_estudio_id", "") ?: ""
        set(v) = sp.edit().putString("voz_estudio_id", v).apply()

    /** Ler notificações de compra do banco para achar gastos do carro. */
    var readBankNotifs: Boolean
        get() = sp.getBoolean("ler_notif_banco", true)
        set(v) = sp.edit().putBoolean("ler_notif_banco", v).apply()

    /** Canal da voz com o passageiro: "midia" (mais alto, como o áudio do WhatsApp) ou "navegacao" (como o Waze). */
    var voiceChannel: String
        get() = sp.getString("voz_canal", "midia") ?: "midia"
        set(v) = sp.edit().putString("voz_canal", v).apply()

    /** Opacidade da bolinha "R" (0.2 a 1). */
    var bubbleOpacity: Float
        get() = sp.getFloat("bolha_opacidade", 0.92f)
        set(v) = sp.edit().putFloat("bolha_opacidade", v).apply()

    /** Tema das telas do Radar: "auto" (segue o celular), "claro" ou "escuro". */
    var tema: String
        get() = sp.getString("tema", "auto") ?: "auto"
        set(v) = sp.edit().putString("tema", v).apply()

    var bubbleLocked: Boolean
        get() = sp.getBoolean("bolha_travada", false)
        set(v) = sp.edit().putBoolean("bolha_travada", v).apply()

    // Dados
    var logOffers: Boolean
        get() = sp.getBoolean("salvar_ofertas", true)
        set(v) = sp.edit().putBoolean("salvar_ofertas", v).apply()

    var diagnostic: Boolean
        get() = sp.getBoolean("diagnostico", false)
        set(v) = sp.edit().putBoolean("diagnostico", v).apply()
}
