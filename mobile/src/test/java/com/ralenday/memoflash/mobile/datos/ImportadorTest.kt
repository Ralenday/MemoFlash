package com.ralenday.memoflash.mobile.datos

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportadorTest {

    @Test
    fun separaPorTabuladorComoAlPegarDeExcel() {
        assertEquals(listOf("Dog" to "Perro", "Cat" to "Gato"), Importador.analizar("Dog\tPerro\nCat\tGato"))
    }

    @Test
    fun aceptaPuntoYComaGuionIgualYComa() {
        val texto = "H2O; Agua\nNaCl - Sal\nCO2 = Dióxido de carbono\nO2, Oxígeno"
        assertEquals(
            listOf("H2O" to "Agua", "NaCl" to "Sal", "CO2" to "Dióxido de carbono", "O2" to "Oxígeno"),
            Importador.analizar(texto),
        )
    }

    @Test
    fun elTabuladorTienePrioridadSobreLaComa() {
        assertEquals(listOf("Hola, mundo" to "Hello, world"), Importador.analizar("Hola, mundo\tHello, world"))
    }

    @Test
    fun ignoraVaciasComentariosYLineasIncompletas() {
        val texto = "# encabezado\n\n   \nsolo frente\nFrente;\n;Reverso\nA;B"
        assertEquals(listOf("A" to "B"), Importador.analizar(texto))
    }

    @Test
    fun quitaComillasDeUnCsv() {
        assertEquals(listOf("Uno" to "One"), Importador.analizar("\"Uno\";\"One\""))
    }
}
