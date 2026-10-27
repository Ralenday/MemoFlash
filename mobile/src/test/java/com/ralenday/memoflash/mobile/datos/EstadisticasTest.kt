package com.ralenday.memoflash.mobile.datos

import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.EstadoTarjeta
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.Repaso
import com.ralenday.memoflash.core.RepeticionEspaciada.DIA_MS
import com.ralenday.memoflash.core.Tarjeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class EstadisticasTest {
    private val utc = ZoneOffset.UTC
    // 15 de marzo de 2026, 12:00 UTC
    private val ahora = 1_773_576_000_000L

    private fun tarjeta(estado: EstadoTarjeta = EstadoTarjeta()) = Tarjeta(mazoUuid = "m", frente = "f", reverso = "r", estado = estado)

    private fun repaso(diasAtras: Int, cal: Calificacion = Calificacion.FACIL, origen: Origen = Origen.TELEFONO, intervalo: Double = 1.0, tarjeta: String = "t") =
        Repaso(tarjetaUuid = tarjeta, fecha = ahora - diasAtras * DIA_MS, calificacion = cal, intervaloResultante = intervalo, origen = origen)

    @Test
    fun sinDatosNoHayPorcentajes() {
        val s = calcularEstadisticas(emptyList(), emptyList(), ahora, utc)
        assertEquals(0, s.totalTarjetas)
        assertEquals(0, s.racha)
        assertNull(s.acierto)
        assertNull(s.retencion)
        assertEquals(14, s.porDia.size)
        assertEquals(7, s.pronostico.size)
    }

    @Test
    fun rachaCuentaDiasSeguidosHastaHoy() {
        val historial = listOf(repaso(0), repaso(1), repaso(2), repaso(4))
        val s = calcularEstadisticas(emptyList(), historial, ahora, utc)
        assertEquals(3, s.racha)
        assertEquals(1, s.repasosHoy)
    }

    @Test
    fun laRachaNoSeRompeSiHoyAunNoEstudias() {
        val s = calcularEstadisticas(emptyList(), listOf(repaso(1), repaso(2)), ahora, utc)
        assertEquals(2, s.racha)
        assertEquals(0, s.repasosHoy)
    }

    @Test
    fun aciertoYOrigenDeLosUltimos30Dias() {
        val historial = listOf(
            repaso(0, Calificacion.FACIL, Origen.TELEFONO),
            repaso(1, Calificacion.REGULAR, Origen.RELOJ),
            repaso(2, Calificacion.DIFICIL, Origen.RELOJ),
            repaso(3, Calificacion.FACIL, Origen.TELEFONO),
            repaso(40, Calificacion.DIFICIL, Origen.RELOJ), // fuera de la ventana
        )
        val s = calcularEstadisticas(emptyList(), historial, ahora, utc)
        assertEquals(75, s.acierto)
        assertEquals(2, s.desdeTelefono)
        assertEquals(2, s.desdeReloj)
    }

    @Test
    fun clasificaLasTarjetasPorEstado() {
        val tarjetas = listOf(
            tarjeta(),
            tarjeta(EstadoTarjeta(intervaloDias = 0.01, proximoRepaso = ahora + 600_000)),
            tarjeta(EstadoTarjeta(intervaloDias = 5.0, repeticiones = 2, proximoRepaso = ahora + 5 * DIA_MS)),
            tarjeta(EstadoTarjeta(intervaloDias = 30.0, repeticiones = 5, proximoRepaso = ahora + 30 * DIA_MS)),
        )
        val s = calcularEstadisticas(tarjetas, emptyList(), ahora, utc)
        assertEquals(listOf("Nuevas" to 1, "Aprendiendo" to 1, "Jóvenes" to 1, "Maduras" to 1), s.estados)
        // Hoy tocan la nueva y la que está aprendiendo (vence en 10 minutos)
        assertEquals(2, s.pronostico.first().valor)
        assertEquals(1, s.pronostico[5].valor)
    }
}
