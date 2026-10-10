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
    // Números podem vir com letras parecidas (leitura por imagem): "l.l km" = "1.1 km", "1l minutos" = "11 minutos".
    private val LEG = Regex(
        """(?:(?<![A-Za-z\d])(\d[\dlI]?)\s*h(?:oras?)?\s*)?(?<![A-Za-z\d])(\d[\dlI]{0,2})\s*m[ií]n(?:utos?|s)?\.?\s*[(\[{]\s*([\dlIoO.,]{1,6})\s*(km|m)\b\s*[)\]}]?""",
        RegexOption.IGNORE_CASE
    )
    /** "R$3,51/km aprox." — a Uber informa quanto paga por km somando busca e viagem. */
    private val RATE_KM = Regex("""R\$\s*([\dlI]{1,2}[.,][\dlIoO]{2})\s*/\s*km""", RegexOption.IGNORE_CASE)
    /** Tolerância na conferência entre os km lidos e o "R$/km aprox." da Uber. */
    private const val RATE_TOLERANCE = 0.06
    private val RATING = Regex("""(?<![\d.,])([1-5][.,]\d{1,2})\s*\(\s*[\d.]+\s*\)""")

    /** Categorias reconhecidas mesmo no meio de uma linha ("UberX Exclusivo"). */
    private val CATEGORY_WORDS = Regex("""\b(UberX|Comfort|Black|Priority|Prioridade|Flash|Juntos|UberXL)\b""", RegexOption.IGNORE_CASE)
    /** Categorias curtas, aceitas só quando a linha tem apenas o nome. */
    private val CATEGORY_EXACT = listOf("Moto", "Pet", "XL", "Pop", "Top", "Plus", "Entrega", "Taxi", "Táxi", "Black Bag")

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
                val hours = if (m.groupValues[1].isEmpty()) 0.0 else (parseOcrNumber(m.groupValues[1]) ?: continue)
                val mins = parseOcrNumber(m.groupValues[2]) ?: continue
                var dist = parseOcrNumber(m.groupValues[3]) ?: continue
                if (m.groupValues[4].equals("m", ignoreCase = true)) dist /= 1000.0
                val rest = t.removeRange(m.range).trim(' ', ',', '-', '·', '•')
                legs.add(Leg(hours * 60 + mins, dist, i, rest))
            }
        }
        if (legs.isEmpty()) return null

        val rate = texts.firstNotNullOfOrNull { t ->
            RATE_KM.find(t)?.groupValues?.get(1)?.let { parseOcrNumber(it) }
        }?.takeIf { it > 0.0 }

        val chosen = chooseLegs(legs, price, rate) ?: return null
        val pickup: Leg? = chosen.first
        val trip: Leg = chosen.second

        val rating = texts.firstNotNullOfOrNull { t ->
            RATING.find(t)?.groupValues?.get(1)?.let { parseNumber(it) }
        }

        val category = texts.firstNotNullOfOrNull { t -> CATEGORY_WORDS.find(t)?.value }
            ?: texts.firstOrNull { t -> CATEGORY_EXACT.any { c -> t.equals(c, ignoreCase = true) } }

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

    /**
     * Escolhe qual trecho é a busca e qual é a viagem.
     * Quando a Uber mostra "R$ X/km aprox.", os km de busca + viagem precisam bater com
     * valor / (R$ por km). Se nenhuma combinação bater, a leitura é descartada
     * (geralmente a tela foi capturada no meio da animação) e o Radar tenta de novo.
     */
    private fun chooseLegs(legs: List<Leg>, price: Double, rate: Double?): Pair<Leg?, Leg>? {
        if (rate == null) {
            return if (legs.size >= 2) Pair(legs[0], legs[1]) else Pair(null, legs[0])
        }
        val expectedKm = price / rate
        fun matches(km: Double) = kotlin.math.abs(km - expectedKm) <= expectedKm * RATE_TOLERANCE + 0.05
        val candidates = legs.take(6)
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                if (matches(candidates[i].km + candidates[j].km)) return Pair(candidates[i], candidates[j])
            }
        }
        // Oferta sem trecho de busca (raro): um trecho só que já bate com o total.
        candidates.firstOrNull { matches(it.km) }?.let { return Pair(null, it) }
        return null
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

    /** Corrige letras lidas no lugar de números: "l.l" -> 1.1, "1l" -> 11, ".6" -> 0.6, "O,5" -> 0.5 */
    private fun parseOcrNumber(s: String): Double? {
        val fixed = s.map { c ->
            when (c) {
                'l', 'I', '|' -> '1'
                'o', 'O' -> '0'
                else -> c
            }
        }.joinToString("")
        return parseNumber(fixed)
    }

    /** "1.1" ou "1,1" -> 1.1 */
    private fun parseNumber(s: String): Double? =
        s.replace(",", ".").trimEnd('.').toDoubleOrNull()
}
