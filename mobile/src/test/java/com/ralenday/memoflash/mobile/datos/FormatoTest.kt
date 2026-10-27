package com.ralenday.memoflash.mobile.datos

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoTest {
    private val ahora = 1_000_000_000_000L
    private val hora = 3_600_000L

    @Test
    fun cuandoTocaDescribeElTiempoRestante() {
        assertEquals("Pendiente", cuandoToca(ahora - 1, ahora))
        assertEquals("Pendiente", cuandoToca(ahora, ahora))
        assertEquals("en 10 min", cuandoToca(ahora + 10 * 60_000, ahora))
        assertEquals("en 1 min", cuandoToca(ahora + 5_000, ahora))
        assertEquals("en 5 h", cuandoToca(ahora + 5 * hora, ahora))
        assertEquals("en 3 d", cuandoToca(ahora + 72 * hora, ahora))
    }

    @Test
    fun pluralUsaSingularSoloConUno() {
        assertEquals("1 tarjeta", plural(1, "tarjeta"))
        assertEquals("0 tarjetas", plural(0, "tarjeta"))
        assertEquals("3 tarjetas", plural(3, "tarjeta"))
    }
}
