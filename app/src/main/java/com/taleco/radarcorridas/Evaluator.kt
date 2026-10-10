package com.taleco.radarcorridas

enum class Verdict { RUIM, MEDIO, BOM }

data class MetricResult(val metric: Metric, val value: Double, val verdict: Verdict)

data class Evaluation(
    val offer: Offer,
    val results: List<MetricResult>,
    val overall: Verdict,
    val profit: Double,
    val spotNote: String? = null   // "★ perto de um ponto bom", quando o destino cai perto de um
)

object Evaluator {

    fun profit(offer: Offer, prefs: Prefs): Double =
        offer.price - offer.totalKm * prefs.costPerKm

    fun value(metric: Metric, offer: Offer, prefs: Prefs): Double? {
        val hours = offer.totalMin / 60.0
        val profit = profit(offer, prefs)
        return when (metric) {
            Metric.HORA -> if (offer.totalMin > 0) offer.price / hours else null
            Metric.MIN -> if (offer.totalMin > 0) offer.price / offer.totalMin else null
            Metric.KM -> if (offer.totalKm > 0) offer.price / offer.totalKm else null
            Metric.NOTA -> offer.rating
            Metric.LUCRO -> profit
            Metric.LUCRO_HORA -> if (offer.totalMin > 0) profit / hours else null
        }
    }

    fun verdict(value: Double, bad: Float, good: Float): Verdict = when {
        value <= bad -> Verdict.RUIM
        value >= good -> Verdict.BOM
        else -> Verdict.MEDIO
    }

    /** Avalia as métricas escolhidas para o cartão e dá um veredito geral pela média. */
    fun evaluate(offer: Offer, prefs: Prefs): Evaluation {
        val results = Metric.values()
            .filter { prefs.isShown(it) }
            .mapNotNull { m ->
                value(m, offer, prefs)?.let { v -> MetricResult(m, v, verdict(v, prefs.bad(m), prefs.good(m))) }
            }

        val overall = if (results.isEmpty()) {
            Verdict.MEDIO
        } else {
            val avg = results.map {
                when (it.verdict) {
                    Verdict.RUIM -> 0.0
                    Verdict.MEDIO -> 1.0
                    Verdict.BOM -> 2.0
                }
            }.average()
            when {
                avg >= 1.5 -> Verdict.BOM
                avg < 0.75 -> Verdict.RUIM
                else -> Verdict.MEDIO
            }
        }
        return Evaluation(offer, results, overall, profit(offer, prefs))
    }
}
