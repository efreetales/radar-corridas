package com.taleco.radarcorridas

/**
 * Transforma os textos que estão na tela do app de corrida numa Offer.
 *
 * Exemplo de textos lidos na oferta da Uber:
 *   "Black", "R$ 22,02", "R$3,15/km aprox.", "4,92 (1093)",
 *   "4 min (1.1 km)", "Rua Frei Caneca, ...", "12 minutos (5.9 km)", "Rua Hannemann, ..."
 *
 * O primeiro trecho "X min (Y km)" é a busca do passageiro; o segundo é a viagem.
 */
object OfferParser {

    private val PRICE_FULL = Regex("""^R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})$""")
    private val PRICE_ANY = Regex("""(?<!\+)R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})(?!\s*/\s*km)""")
    private val LEG = Regex(
        """(?:(\d+)\s*h(?:oras?)?\s*)?(\d+)\s*m[ií]n(?:utos?|s)?\.?\s*[(\[{]\s*([\d.,]+)\s*(km|m)\s*[)\]}]?""",
        RegexOption.IGNORE_CASE
    )
    private val RATING = Regex("""(?<![\d.,])([1-5][.,]\d{1,2})\s*\(\s*[\d.]+\s*\)""")

    private val CATEGORIES = listOf(
        "UberX", "Comfort", "Black", "Black Bag", "Flash", "Moto", "Priority", "Prioridade",
        "Juntos", "Pet", "XL", "Pop", "Top", "Plus", "Entrega", "Taxi", "Táxi"
    )

    private data class Leg(val minutes: Double, val km: Double, val index: Int, val rest: String)

    // Erros comuns da leitura por imagem: "R$" lido como "RS", "R5" ou "R §".
    private val OCR_CURRENCY = Regex("""(?<![A-Za-z])R\s?[S5§]\s?(?=\d)""")
    private val ONLY_CURRENCY = Regex("^R\\s?[\$S5§]\$")
    private val ONLY_AMOUNT = Regex("""^\d{1,3}(?:\.\d{3})*,\d{2}$""")

    /** Limpa os textos e junta "R$" e "22,02" quando a leitura os separa em duas linhas. */
    private fun normalize(rawTexts: List<String>): List<String> {
        val cleaned = rawTexts
            .map { it.replace(' ', ' ').replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
            .map { OCR_CURRENCY.replace(it, "R\$ ") }
        val out = mutableListOf<String>()
        var i = 0
        while (i < cleaned.size) {
            val t = cleaned[i]
            val next = cleaned.getOrNull(i + 1)
            if (ONLY_CURRENCY.matches(t) && next != null && ONLY_AMOUNT.matches(next)) {
                out.add("R\$ $next")
                i += 2
            } else {
                out.add(t)
                i++
            }
        }
        return out
    }

    fun parse(app: String, rawTexts: List<String>): Offer? {
        val texts = normalize(rawTexts)
        if (texts.isEmpty()) return null

        val price = findPrice(texts) ?: return null

        val legs = mutableListOf<Leg>()
        for ((i, t) in texts.withIndex()) {
            for (m in LEG.findAll(t)) {
                val hours = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val mins = m.groupValues[2].toDoubleOrNull() ?: continue
                var dist = parseNumber(m.groupValues[3]) ?: continue
                if (m.groupValues[4].equals("m", ignoreCase = true)) dist /= 1000.0
                val rest = t.removeRange(m.range).trim(' ', ',', '-', '·', '•')
                legs.add(Leg(hours * 60 + mins, dist, i, rest))
            }
        }
        if (legs.isEmpty()) return null

        val pickup: Leg?
        val trip: Leg
        if (legs.size >= 2) {
            pickup = legs[0]
            trip = legs[1]
        } else {
            pickup = null
            trip = legs[0]
        }

        val rating = texts.firstNotNullOfOrNull { t ->
            RATING.find(t)?.groupValues?.get(1)?.let { parseNumber(it) }
        }

        val category = texts.firstOrNull { t ->
            CATEGORIES.any { c -> t.equals(c, ignoreCase = true) || t.equals("Uber$c", ignoreCase = true) }
        }

        return Offer(
            app = app,
            category = category,
            price = price,
            pickupMin = pickup?.minutes ?: 0.0,
            pickupKm = pickup?.km ?: 0.0,
            tripMin = trip.minutes,
            tripKm = trip.km,
            rating = rating,
            origin = pickup?.let { addressFor(it, texts) },
            destination = addressFor(trip, texts)
        )
    }

    private fun findPrice(texts: List<String>): Double? {
        for (t in texts) {
            val m = PRICE_FULL.find(t)
            if (m != null) return parseMoney(m.groupValues[1])
        }
        for (t in texts) {
            val m = PRICE_ANY.find(t)
            if (m != null) return parseMoney(m.groupValues[1])
        }
        return null
    }

    /** O endereço costuma ser o texto logo depois de "X min (Y km)". */
    private fun addressFor(leg: Leg, texts: List<String>): String? {
        if (leg.rest.length >= 6) return leg.rest
        val next = texts.getOrNull(leg.index + 1) ?: return null
        if (LEG.containsMatchIn(next) || next.contains("R$")) return null
        return next
    }

    /** "1.234,56" -> 1234.56 */
    private fun parseMoney(s: String): Double? =
        s.replace(".", "").replace(",", ".").toDoubleOrNull()

    /** "1.1" ou "1,1" -> 1.1 */
    private fun parseNumber(s: String): Double? =
        s.replace(",", ".").trimEnd('.').toDoubleOrNull()
}
