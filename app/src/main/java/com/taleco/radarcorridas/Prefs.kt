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

    // Dados
    var logOffers: Boolean
        get() = sp.getBoolean("salvar_ofertas", true)
        set(v) = sp.edit().putBoolean("salvar_ofertas", v).apply()

    var diagnostic: Boolean
        get() = sp.getBoolean("diagnostico", false)
        set(v) = sp.edit().putBoolean("diagnostico", v).apply()
}
