package com.taleco.radarcorridas

import java.util.Locale

/** Uma oferta de corrida lida da tela. Tempos em minutos, distâncias em km. */
data class Offer(
    val app: String,
    val category: String?,
    val price: Double,
    val pickupMin: Double,
    val pickupKm: Double,
    val tripMin: Double,
    val tripKm: Double,
    val rating: Double?,
    val origin: String?,
    val destination: String?
) {
    val totalMin: Double get() = pickupMin + tripMin
    val totalKm: Double get() = pickupKm + tripKm

    /** Identifica a mesma oferta entre leituras repetidas da tela. */
    fun signature(): String =
        String.format(Locale.US, "%s|%.2f|%.1f|%.0f", app, price, totalKm, totalMin)

    companion object {
        /** Oferta de exemplo, igual ao print enviado (Black, R$ 22,02). */
        fun sample() = Offer(
            app = "UBER",
            category = "Black",
            price = 22.02,
            pickupMin = 4.0,
            pickupKm = 1.1,
            tripMin = 12.0,
            tripKm = 5.9,
            rating = 4.92,
            origin = "Rua Frei Caneca, São Paulo",
            destination = "Rua Hannemann, 84, Pari, São Paulo"
        )
    }
}
