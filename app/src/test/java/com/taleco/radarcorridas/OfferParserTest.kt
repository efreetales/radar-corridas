package com.taleco.radarcorridas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Casos reais da leitura por imagem (endereços trocados por genéricos). */
class OfferParserTest {
    private fun p(vararg t: String) = OfferParser.parse("UBER", t.toList())

    @Test fun valorSemCentavos() {
        val o = p("2 Comfort", "X", "R$ 31", "R$3,83/km aprox.", "* 4,77 (2135)", "Verificado", "10 min (2.4 km)", "Rua A, Centro", "h 35 minutos (5.7 km)", "Rua B, 100")
        assertNotNull(o); o!!
        assertEquals(31.0, o.price, 0.01); assertEquals(2.4, o.pickupKm, 0.01); assertEquals(5.7, o.tripKm, 0.01); assertEquals(35.0, o.tripMin, 0.01)
        println("PARSER ok valorSemCentavos ${o.price} ${o.pickupKm}+${o.tripKm} km")
    }
    @Test fun viagemEmHorasEMenosDeUmMinuto() {
        val o = p("2 Black Exclusivo", "X", "R$ 74,53", "R$3,36/km aprox.", "★ 4,93 (392)", "Verificado", "<l min (O km)", "Rua A, Centro", "lh (22.2 km)", "Rua B, 225")
        assertNotNull(o); o!!
        assertEquals(22.2, o.tripKm, 0.01); assertEquals(60.0, o.tripMin, 0.01); assertEquals(1.0, o.pickupMin, 0.01)
        println("PARSER ok horas ${o.tripMin} min ${o.tripKm} km")
    }
    @Test fun kmSemVirgula() {
        val o = p("Comfort", "X", "R$ 18,60", "R$2,11/km aprox.", "* 4,94 (663)", "Verificado", "4 min (1.4 km)", "Av A, Centro", "14 minutos (74 km)", "Av B, 1310")
        assertNotNull(o); o!!
        assertEquals(7.4, o.tripKm, 0.01); assertEquals(1.4, o.pickupKm, 0.01)
        println("PARSER ok kmSemVirgula ${o.tripKm}")
    }
    @Test fun casoQueJaFuncionava() {
        val o = p("8 Comfort", "X", "R$ 40,04", "a", "R$2,37/km aprox.", "* 4,96 (330)", "4 min (1.3 km)", "R. A, Centro", "30 minutos (15.6 km)", "R. B, 124")!!
        assertEquals(40.04, o.price, 0.01); assertEquals(16.9, o.totalKm, 0.05); assertEquals(34.0, o.totalMin, 0.01)
        val o2 = p("2 Black", "X", "R$ 22,06", "R$3,24/km aprox.", "★ 4,86 (489)", "Verificado", "4 min (1.3 km)", "Hospital X", "13 minutos (5.5 km)", "Rua Y, 200")!!
        assertEquals(6.8, o2.totalKm, 0.05)
        val o3 = p("Comfort", "X", "R$ 184,22", "* 4,89 (351)", "Verificado", "3 min (0.7 km)", "Hospital X", "lhe 25 min (113.5 km)", "R. Y, 2136")!!
        assertEquals(85.0, o3.tripMin, 0.01); assertEquals(113.5, o3.tripKm, 0.01)
        println("PARSER ok regressao")
    }
    @Test fun naoInventaOferta() {
        assertNull(p("2 Comfort", "X", "R$ 31", "10 min (2.4 km)", "35 minutos (5.7 km)"))   // sem centavos e sem R$/km
        assertNull(p("Alta demanda aqui", "+R$ 2", "1-2 min"))
        println("PARSER ok negativos")
    }
}
