package com.ralenday.memoflash.mobile.nube

import com.ralenday.memoflash.core.Instantanea
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

/** Baja y sube la copia de la cuenta. La base local sigue siendo la que usa la app sin conexión. */
internal object Nube {
    suspend fun descargar(cliente: SupabaseClient): Instantanea = InstantaneaRemota(
        mazos = cliente.from("mazos").select().decodeList(),
        tarjetas = cliente.from("tarjetas").select().decodeList(),
        repasos = cliente.from("historial_repasos").select().decodeList(),
        borrados = cliente.from("borrados").select().decodeList(),
    ).aLocal()

    suspend fun subir(cliente: SupabaseClient, datos: Instantanea, userId: String) {
        val remoto = datos.aRemota(userId)
        subirTabla("mazos", cliente, remoto.mazos)
        subirTabla("tarjetas", cliente, remoto.tarjetas)
        subirTabla("historial_repasos", cliente, remoto.repasos)
        subirTabla("borrados", cliente, remoto.borrados) { onConflict = "user_id,tabla, uuid" }
        borrar("tarjetas", cliente, datos.borrados.filter { it.tabla == "tarjetas" }.map { it.uuid })
        borrar("mazos", cliente, datos.borrados.filter { it.tabla == "mazos" }.map { it.uuid })
    }

    private suspend inline fun <reified T : Any> subirTabla(
        tabla: String,
        cliente: SupabaseClient,
        filas: List<T>,
        crossinline extra: io.github.jan.supabase.postgrest.query.request.UpsertRequestBuilder.() -> Unit = {},
    ) {
        filas.chunked(100).forEach { grupo ->
            cliente.from(tabla).upsert(grupo) {
                onConflict = "uuid"
                extra()
            }
        }
    }

    private suspend fun borrar(tabla: String, cliente: SupabaseClient, ids: List<String>) {
        ids.chunked(80).forEach { grupo ->
            cliente.from(tabla).delete {
                filter { isIn("uuid", grupo.map { it as Any }) }
            }
        }
    }
}
