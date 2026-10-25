package com.ralenday.memoflash.core

import java.util.UUID

/** Los tres botones que ve el usuario al repasar (iguales en teléfono y reloj). */
enum class Calificacion(val valor: Int, val etiqueta: String) {
    DIFICIL(1, "Difícil"),
    REGULAR(2, "Regular"),
    FACIL(3, "Fácil");

    companion object {
        fun de(valor: Int) = entries.first { it.valor == valor }
    }
}

/** Dispositivo donde se hizo un repaso. Sirve para estadísticas y para la sincronización. */
enum class Origen { TELEFONO, RELOJ }

data class Mazo(
    val uuid: String = nuevoId(),
    val nombre: String,
    val descripcion: String = "",
    /** Si es true, el mazo se copia al reloj. */
    val enReloj: Boolean = true,
    val actualizado: Long = System.currentTimeMillis(),
)

/** Un mazo junto con sus contadores, para mostrarlo en listas. */
data class ResumenMazo(
    val mazo: Mazo,
    val total: Int,
    val pendientes: Int,
)

data class Tarjeta(
    val uuid: String = nuevoId(),
    val mazoUuid: String,
    val frente: String,
    val reverso: String,
    /** Ruta local de la imagen. En el reloj solo se guarda una marca de que existe. */
    val imagenFrente: String? = null,
    val imagenReverso: String? = null,
    val estado: EstadoTarjeta = EstadoTarjeta(),
    val actualizado: Long = System.currentTimeMillis(),
)

/** Datos de programación de una tarjeta que calcula el algoritmo de repetición espaciada. */
data class EstadoTarjeta(
    /** "nivel_dificultad": factor de facilidad de SM-2 (1.3 = muy difícil, 2.5 = normal). */
    val nivelDificultad: Double = RepeticionEspaciada.FACILIDAD_INICIAL,
    val intervaloDias: Double = 0.0,
    val repeticiones: Int = 0,
    /** Momento (epoch ms) en que toca volver a ver la tarjeta. 0 = nueva, se muestra ya. */
    val proximoRepaso: Long = 0,
)

data class Repaso(
    val uuid: String = nuevoId(),
    val tarjetaUuid: String,
    val fecha: Long,
    val calificacion: Calificacion,
    val intervaloResultante: Double,
    val origen: Origen,
)

fun nuevoId(): String = UUID.randomUUID().toString()
