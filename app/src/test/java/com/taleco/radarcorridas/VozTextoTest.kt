package com.taleco.radarcorridas

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VozTextoTest {
    private fun ok(entrada: String, esperado: String) {
        val r = Voz.porExtenso(entrada)
        println("VOZTEXTO $entrada -> $r")
        assertEquals(esperado, r)
    }
    @Test fun abreviacoes() {
        ok("R. Augusta", "Rua Augusta")
        ok("R Augusta", "Rua Augusta")
        ok("Rua Augusta", "Rua Augusta")
        ok("Av. Brg. Faria Lima", "Avenida Brigadeiro Faria Lima")
        ok("Al. Gabriel Monteiro da Silva", "Alameda Gabriel Monteiro da Silva")
        ok("R. Dr. Renato Paes de Barros", "Rua Doutor Renato Paes de Barros")
        ok("Av Prof. Francisco Morato", "Avenida Professor Francisco Morato")
        ok("Pç. da Sé", "Praça da Sé")
        ok("Rodovia Raposo Tavares", "Rodovia Raposo Tavares")
        ok("Estação Júlio Prestes", "Estação Júlio Prestes")
        ok("R. Cel. Oscar Porto", "Rua Coronel Oscar Porto")
    }
    @Test fun frase() {
        val t = Voz.montar("Oi, {nome}! Nosso destino é {rua}.", "Ana Paula", "R. Pamplona, 83 - Bela Vista")
        println("VOZTEXTO $t")
        assertEquals("Oi, Ana! Nosso destino é Rua Pamplona.", t)
    }
}
