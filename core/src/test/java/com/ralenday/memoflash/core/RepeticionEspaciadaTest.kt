package com.ralenday.memoflash.core

import com.ralenday.memoflash.core.RepeticionEspaciada.DIA_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeticionEspaciadaTest {

    private val inicio = 1_700_000_000_000L

    @Test
    fun facilAlargaElIntervaloCadaVez() {
        var estado = EstadoTarjeta()
        val intervalos = (0 until 4).map {
            estado = RepeticionEspaciada.calificar(estado, Calificacion.FACIL, inicio)
            estado.intervaloDias
        }
        assertEquals(3.0, intervalos[0], 0.0)
        assertEquals(7.0, intervalos[1], 0.0)
        assertTrue(intervalos[2] > intervalos[1] && intervalos[3] > intervalos[2])
    }

    @Test
    fun dificilReiniciaYBajaLaFacilidad() {
        val aprendida = EstadoTarjeta(nivelDificultad = 2.5, intervaloDias = 20.0, repeticiones = 4)
        val estado = RepeticionEspaciada.calificar(aprendida, Calificacion.DIFICIL, inicio)
        assertEquals(0, estado.repeticiones)
        assertEquals(2.3, estado.nivelDificultad, 1e-9)
        assertEquals(inicio + 10 * 60 * 1000, estado.proximoRepaso)
    }

    @Test
    fun laFacilidadNuncaBajaDelMinimo() {
        var estado = EstadoTarjeta()
        repeat(20) { estado = RepeticionEspaciada.calificar(estado, Calificacion.DIFICIL, inicio) }
        assertEquals(RepeticionEspaciada.FACILIDAD_MINIMA, estado.nivelDificultad, 1e-9)
    }

    @Test
    fun reconstruirNoDependeDelOrdenEnQueLleganLosRepasos() {
        // Repasos mezclados de teléfono y reloj, como llegan al sincronizar
        val historial = listOf(
            Repaso("a", "t", inicio, Calificacion.FACIL, 0.0, Origen.TELEFONO),
            Repaso("b", "t", inicio + 3 * DIA_MS, Calificacion.DIFICIL, 0.0, Origen.RELOJ),
            Repaso("c", "t", inicio + 3 * DIA_MS + 600_000, Calificacion.REGULAR, 0.0, Origen.RELOJ),
            Repaso("d", "t", inicio + 4 * DIA_MS, Calificacion.FACIL, 0.0, Origen.TELEFONO),
        )
        val esperado = RepeticionEspaciada.reconstruir(historial)
        assertEquals(esperado, RepeticionEspaciada.reconstruir(historial.reversed()))
        assertEquals(esperado, RepeticionEspaciada.reconstruir(historial.shuffled()))
        assertEquals(2, esperado.repeticiones)
    }
}
