package com.ralenday.memoflash.mobile.datos

import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.Repaso
import com.ralenday.memoflash.core.RepeticionEspaciada.DIA_MS
import com.ralenday.memoflash.core.Tarjeta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class Barra(val etiqueta: String, val detalle: String, val valor: Int)

data class Estadisticas(
    val totalTarjetas: Int,
    val repasosHoy: Int,
    val racha: Int,
    /** % de repasos (30 días) sin "Difícil". */
    val acierto: Int?,
    /** % de tarjetas ya aprendidas (intervalo ≥ 1 día) que recordaste: la retención real. */
    val retencion: Int?,
    val porDia: List<Barra>,
    val pronostico: List<Barra>,
    val estados: List<Pair<String, Int>>,
    val desdeTelefono: Int,
    val desdeReloj: Int,
)

private val locale = Locale.forLanguageTag("es-MX")

fun calcularEstadisticas(db: BaseDatos, ahora: Long = System.currentTimeMillis()): Estadisticas =
    calcularEstadisticas(db.todasLasTarjetas(), db.historial(), ahora)

/** Función pura: con las mismas tarjetas e historial siempre da el mismo resultado. */
fun calcularEstadisticas(
    tarjetas: List<Tarjeta>,
    historial: List<Repaso>,
    ahora: Long,
    zona: ZoneId = ZoneId.systemDefault(),
): Estadisticas {
    val hoy = Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate()
    fun dia(ms: Long) = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
    fun nombreDia(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2)
    fun detalleDia(d: LocalDate) = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${d.dayOfMonth}"

    val hace30 = ahora - 30 * DIA_MS
    val recientes = historial.filter { it.fecha >= hace30 }
    val porFecha = historial.groupingBy { dia(it.fecha) }.eachCount()

    // Retención: de los repasos de tarjetas que ya estaban aprendidas, cuántos recordaste
    val deAprendidas = historial.groupBy { it.tarjetaUuid }.values.flatMap { repasos ->
        repasos.sortedBy { it.fecha }.zipWithNext().filter { (antes, _) -> antes.intervaloResultante >= 1.0 }.map { it.second }
    }.filter { it.fecha >= hace30 }

    var racha = 0
    var d = if (porFecha.containsKey(hoy)) hoy else hoy.minusDays(1)
    while (porFecha.containsKey(d)) { racha++; d = d.minusDays(1) }

    return Estadisticas(
        totalTarjetas = tarjetas.size,
        repasosHoy = porFecha[hoy] ?: 0,
        racha = racha,
        acierto = recientes.takeIf { it.isNotEmpty() }
            ?.let { r -> r.count { it.calificacion != Calificacion.DIFICIL } * 100 / r.size },
        retencion = deAprendidas.takeIf { it.isNotEmpty() }
            ?.let { r -> r.count { it.calificacion != Calificacion.DIFICIL } * 100 / r.size },
        porDia = (13 downTo 0).map { hoy.minusDays(it.toLong()) }.map { Barra(nombreDia(it), detalleDia(it), porFecha[it] ?: 0) },
        pronostico = (0..6).map { hoy.plusDays(it.toLong()) }.map { fecha ->
            val n = tarjetas.count { t ->
                val toca = dia(maxOf(t.estado.proximoRepaso, ahora))
                if (fecha == hoy) !toca.isAfter(hoy) else toca == fecha
            }
            Barra(if (fecha == hoy) "Hoy" else nombreDia(fecha), if (fecha == hoy) "Hoy" else detalleDia(fecha), n)
        },
        estados = listOf(
            "Nuevas" to tarjetas.count { it.estado.repeticiones == 0 && it.estado.proximoRepaso == 0L },
            "Aprendiendo" to tarjetas.count { it.estado.proximoRepaso != 0L && it.estado.intervaloDias < 1 },
            "Jóvenes" to tarjetas.count { it.estado.intervaloDias in 1.0..<21.0 },
            "Maduras" to tarjetas.count { it.estado.intervaloDias >= 21 },
        ),
        desdeTelefono = recientes.count { it.origen == Origen.TELEFONO },
        desdeReloj = recientes.count { it.origen == Origen.RELOJ },
    )
}
