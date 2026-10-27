package com.ralenday.memoflash.core

/** Un mazo o una tarjeta que alguien borró. Si el contenido es más nuevo, la edición gana. */
data class Borrado(
    val tabla: String,
    val uuid: String,
    val fecha: Long,
)

/** Copia completa de lo que hay que unir entre el teléfono y la cuenta. */
data class Instantanea(
    val mazos: List<Mazo> = emptyList(),
    val tarjetas: List<Tarjeta> = emptyList(),
    val repasos: List<Repaso> = emptyList(),
    val borrados: List<Borrado> = emptyList(),
)

/**
 * Une dos copias del mismo usuario. El historial solo crece. En mazos y tarjetas gana la
 * versión con [Mazo.actualizado] más reciente, salvo que un borrado sea posterior.
 */
fun fusionar(local: Instantanea, remoto: Instantanea): Instantanea {
    val tumbas = (local.borrados + remoto.borrados)
        .groupBy { it.tabla to it.uuid }
        .mapValues { (_, items) -> items.maxBy { it.fecha } }

    fun vive(tabla: String, uuid: String, actualizado: Long): Boolean {
        val tumba = tumbas[tabla to uuid] ?: return true
        return actualizado > tumba.fecha
    }

    val mazos = unirPorFecha(local.mazos, remoto.mazos, Mazo::uuid, Mazo::actualizado)
        .filter { vive("mazos", it.uuid, it.actualizado) }
    val mazosVivos = mazos.map { it.uuid }.toSet()
    val tarjetas = unirPorFecha(local.tarjetas, remoto.tarjetas, Tarjeta::uuid, Tarjeta::actualizado)
        .filter { vive("tarjetas", it.uuid, it.actualizado) && it.mazoUuid in mazosVivos }
    val tarjetasVivas = tarjetas.map { it.uuid }.toSet()
    val repasos = (local.repasos + remoto.repasos)
        .associateBy { it.uuid }
        .values
        .filter { it.tarjetaUuid in tarjetasVivas }
        .sortedBy { it.fecha }
    val borrados = tumbas.values.filter { tumba ->
        when (tumba.tabla) {
            "mazos" -> mazos.none { it.uuid == tumba.uuid }
            "tarjetas" -> tarjetas.none { it.uuid == tumba.uuid }
            else -> true
        }
    }
    return Instantanea(mazos, tarjetas, repasos, borrados)
}

private fun <T> unirPorFecha(
    local: List<T>,
    remoto: List<T>,
    id: (T) -> String,
    actualizado: (T) -> Long,
): List<T> {
    val unidos = local.associateBy(id).toMutableMap()
    for (item in remoto) {
        val previo = unidos[id(item)]
        if (previo == null || actualizado(item) >= actualizado(previo)) unidos[id(item)] = item
    }
    return unidos.values.toList()
}
