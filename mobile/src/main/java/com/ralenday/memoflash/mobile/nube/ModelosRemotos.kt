package com.ralenday.memoflash.mobile.nube

import com.ralenday.memoflash.core.Borrado
import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.EstadoTarjeta
import com.ralenday.memoflash.core.Instantanea
import com.ralenday.memoflash.core.Mazo
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.Repaso
import com.ralenday.memoflash.core.Tarjeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MazoRemoto(
    val uuid: String,
    @SerialName("user_id") val userId: String = "",
    val nombre: String,
    val descripcion: String = "",
    @SerialName("en_reloj") val enReloj: Boolean = true,
    val actualizado: Long,
)

@Serializable
internal data class TarjetaRemota(
    val uuid: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("mazo_uuid") val mazoUuid: String,
    val frente: String,
    val reverso: String,
    @SerialName("imagen_frente") val imagenFrente: String? = null,
    @SerialName("imagen_reverso") val imagenReverso: String? = null,
    @SerialName("nivel_dificultad") val nivelDificultad: Double = 2.5,
    @SerialName("intervalo_dias") val intervaloDias: Double = 0.0,
    val repeticiones: Int = 0,
    @SerialName("proximo_repaso") val proximoRepaso: Long = 0,
    val actualizado: Long,
)

@Serializable
internal data class RepasoRemoto(
    val uuid: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("tarjeta_uuid") val tarjetaUuid: String,
    val fecha: Long,
    val calificacion: Int,
    @SerialName("intervalo_resultante") val intervaloResultante: Double,
    val origen: String,
)

@Serializable
internal data class BorradoRemoto(
    @SerialName("user_id") val userId: String = "",
    val tabla: String,
    val uuid: String,
    val fecha: Long,
)

internal data class InstantaneaRemota(
    val mazos: List<MazoRemoto>,
    val tarjetas: List<TarjetaRemota>,
    val repasos: List<RepasoRemoto>,
    val borrados: List<BorradoRemoto>,
)

internal fun Mazo.aRemoto(userId: String) = MazoRemoto(uuid, userId, nombre, descripcion, enReloj, actualizado)

internal fun MazoRemoto.aLocal() = Mazo(uuid, nombre, descripcion, enReloj, actualizado)

internal fun Tarjeta.aRemoto(userId: String) = TarjetaRemota(
    uuid, userId, mazoUuid, frente, reverso, imagenFrente, imagenReverso,
    estado.nivelDificultad, estado.intervaloDias, estado.repeticiones, estado.proximoRepaso, actualizado,
)

internal fun TarjetaRemota.aLocal() = Tarjeta(
    uuid, mazoUuid, frente, reverso, imagenFrente, imagenReverso,
    EstadoTarjeta(nivelDificultad, intervaloDias, repeticiones, proximoRepaso),
    actualizado,
)

internal fun Repaso.aRemoto(userId: String) = RepasoRemoto(
    uuid, userId, tarjetaUuid, fecha, calificacion.valor, intervaloResultante, origen.name,
)

internal fun RepasoRemoto.aLocal() = Repaso(
    uuid, tarjetaUuid, fecha, Calificacion.de(calificacion), intervaloResultante, Origen.valueOf(origen),
)

internal fun Instantanea.aRemota(userId: String) = InstantaneaRemota(
    mazos.map { it.aRemoto(userId) },
    tarjetas.map { it.aRemoto(userId) },
    repasos.map { it.aRemoto(userId) },
    borrados.map { BorradoRemoto(userId, it.tabla, it.uuid, it.fecha) },
)

internal fun InstantaneaRemota.aLocal() = Instantanea(
    mazos.map { it.aLocal() },
    tarjetas.map { it.aLocal() },
    repasos.map { it.aLocal() },
    borrados.map { Borrado(it.tabla, it.uuid, it.fecha) },
)
