package com.ralenday.memoflash.core.sync

import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.EstadoTarjeta
import com.ralenday.memoflash.core.Mazo
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.Repaso
import com.ralenday.memoflash.core.Tarjeta
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lo que un dispositivo le manda al otro.
 * - Teléfono -> reloj: mazos marcados "en reloj", sus tarjetas (solo texto) y su historial.
 * - Reloj -> teléfono: solo los repasos hechos en el reloj que el teléfono aún no confirma.
 */
data class Paquete(
    val mazos: List<Mazo> = emptyList(),
    val tarjetas: List<Tarjeta> = emptyList(),
    val historial: List<Repaso> = emptyList(),
) {
    fun aBytes(): ByteArray = JSONObject().apply {
        put("version", 1)
        put("mazos", JSONArray(mazos.map { it.aJson() }))
        put("tarjetas", JSONArray(tarjetas.map { it.aJson() }))
        put("historial", JSONArray(historial.map { it.aJson() }))
    }.toString().toByteArray()

    companion object {
        /** En el reloj no se guardan imágenes; solo esta marca para avisar que existe una. */
        const val IMAGEN_EN_TELEFONO = "telefono"

        fun deBytes(bytes: ByteArray): Paquete {
            val json = JSONObject(String(bytes))
            return Paquete(
                mazos = json.getJSONArray("mazos").objetos().map { it.aMazo() },
                tarjetas = json.getJSONArray("tarjetas").objetos().map { it.aTarjeta() },
                historial = json.getJSONArray("historial").objetos().map { it.aRepaso() },
            )
        }
    }
}

private fun JSONArray.objetos() = (0 until length()).map { getJSONObject(it) }

private fun JSONObject.textoOpcional(clave: String): String? = if (isNull(clave)) null else optString(clave)

private fun Mazo.aJson() = JSONObject().apply {
    put("uuid", uuid)
    put("nombre", nombre)
    put("descripcion", descripcion)
    put("actualizado", actualizado)
}

private fun JSONObject.aMazo() = Mazo(
    uuid = getString("uuid"),
    nombre = getString("nombre"),
    descripcion = getString("descripcion"),
    enReloj = true,
    actualizado = getLong("actualizado"),
)

private fun Tarjeta.aJson() = JSONObject().apply {
    put("uuid", uuid)
    put("mazo", mazoUuid)
    put("frente", frente)
    put("reverso", reverso)
    put("imgFrente", imagenFrente?.let { Paquete.IMAGEN_EN_TELEFONO } ?: JSONObject.NULL)
    put("imgReverso", imagenReverso?.let { Paquete.IMAGEN_EN_TELEFONO } ?: JSONObject.NULL)
    put("dificultad", estado.nivelDificultad)
    put("intervalo", estado.intervaloDias)
    put("repeticiones", estado.repeticiones)
    put("proximo", estado.proximoRepaso)
    put("actualizado", actualizado)
}

private fun JSONObject.aTarjeta() = Tarjeta(
    uuid = getString("uuid"),
    mazoUuid = getString("mazo"),
    frente = getString("frente"),
    reverso = getString("reverso"),
    imagenFrente = textoOpcional("imgFrente"),
    imagenReverso = textoOpcional("imgReverso"),
    estado = EstadoTarjeta(
        nivelDificultad = getDouble("dificultad"),
        intervaloDias = getDouble("intervalo"),
        repeticiones = getInt("repeticiones"),
        proximoRepaso = getLong("proximo"),
    ),
    actualizado = getLong("actualizado"),
)

private fun Repaso.aJson() = JSONObject().apply {
    put("uuid", uuid)
    put("tarjeta", tarjetaUuid)
    put("fecha", fecha)
    put("cal", calificacion.valor)
    put("intervalo", intervaloResultante)
    put("origen", origen.name)
}

private fun JSONObject.aRepaso() = Repaso(
    uuid = getString("uuid"),
    tarjetaUuid = getString("tarjeta"),
    fecha = getLong("fecha"),
    calificacion = Calificacion.de(getInt("cal")),
    intervaloResultante = getDouble("intervalo"),
    origen = Origen.valueOf(getString("origen")),
)
