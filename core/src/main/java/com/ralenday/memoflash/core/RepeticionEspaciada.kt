package com.ralenday.memoflash.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Algoritmo SM-2 (el de SuperMemo/Anki) adaptado a tres botones.
 *
 * Es una función pura: el mismo historial siempre produce el mismo estado. Gracias a eso la
 * sincronización no necesita resolver conflictos: cada dispositivo une los historiales y
 * vuelve a calcular la tarjeta con [reconstruir].
 */
object RepeticionEspaciada {
    const val FACILIDAD_INICIAL = 2.5
    const val FACILIDAD_MINIMA = 1.3
    const val INTERVALO_MAXIMO_DIAS = 3650.0
    const val DIA_MS = 24L * 60 * 60 * 1000

    /** Una tarjeta marcada como Difícil vuelve a salir en 10 minutos. */
    const val REINTENTO_DIAS = 10.0 / (24 * 60)

    fun calificar(estado: EstadoTarjeta, calificacion: Calificacion, ahora: Long): EstadoTarjeta {
        val facilidad = estado.nivelDificultad
        val (nuevaFacilidad, repeticiones, intervalo) = when (calificacion) {
            Calificacion.DIFICIL -> Triple(
                max(FACILIDAD_MINIMA, facilidad - 0.20),
                0,
                REINTENTO_DIAS,
            )
            Calificacion.REGULAR -> {
                val reps = estado.repeticiones + 1
                val dias = when (reps) {
                    1 -> 1.0
                    2 -> 3.0
                    else -> max(estado.intervaloDias + 1, estado.intervaloDias * facilidad)
                }
                Triple(max(FACILIDAD_MINIMA, facilidad - 0.05), reps, dias)
            }
            Calificacion.FACIL -> {
                val reps = estado.repeticiones + 1
                val dias = when (reps) {
                    1 -> 3.0
                    2 -> 7.0
                    else -> max(estado.intervaloDias + 2, estado.intervaloDias * facilidad * 1.3)
                }
                Triple(facilidad + 0.15, reps, dias)
            }
        }
        val dias = min(intervalo, INTERVALO_MAXIMO_DIAS)
        return EstadoTarjeta(
            nivelDificultad = nuevaFacilidad,
            intervaloDias = dias,
            repeticiones = repeticiones,
            proximoRepaso = ahora + (dias * DIA_MS).roundToLong(),
        )
    }

    /** Recalcula el estado de una tarjeta a partir de todo su historial (en cualquier orden). */
    fun reconstruir(historial: List<Repaso>): EstadoTarjeta =
        historial
            .sortedWith(compareBy({ it.fecha }, { it.uuid }))
            .fold(EstadoTarjeta()) { estado, repaso -> calificar(estado, repaso.calificacion, repaso.fecha) }

    /** Texto corto para mostrar en cada botón cuánto tardará en volver la tarjeta. */
    fun vistaPrevia(estado: EstadoTarjeta, calificacion: Calificacion, ahora: Long): String =
        formatearIntervalo(calificar(estado, calificacion, ahora).intervaloDias)

    fun formatearIntervalo(dias: Double): String = when {
        dias < 1.0 / 24 -> "${(dias * 24 * 60).roundToLong()} min"
        dias < 1 -> "${(dias * 24).roundToLong()} h"
        dias < 30 -> "${dias.roundToLong()} d"
        dias < 365 -> "${(dias / 30).roundToLong()} mes"
        else -> "%.1f años".format(dias / 365)
    }
}
