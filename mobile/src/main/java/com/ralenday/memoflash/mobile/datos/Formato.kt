package com.ralenday.memoflash.mobile.datos

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatearFecha(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("es-MX")).format(Date(ms))

/** "Pendiente", "en 20 min", "en 5 h" o "en 3 d" según cuándo toca repasar. */
fun cuandoToca(proximo: Long, ahora: Long = System.currentTimeMillis()): String {
    if (proximo <= ahora) return "Pendiente"
    val horas = (proximo - ahora) / 3_600_000.0
    return when {
        horas < 1 -> "en ${(horas * 60).toInt().coerceAtLeast(1)} min"
        horas < 24 -> "en ${horas.toInt()} h"
        else -> "en ${(horas / 24).toInt()} d"
    }
}

/** "1 tarjeta", "3 tarjetas". */
fun plural(n: Int, singular: String, plural: String = singular + "s") = "$n ${if (n == 1) singular else plural}"
