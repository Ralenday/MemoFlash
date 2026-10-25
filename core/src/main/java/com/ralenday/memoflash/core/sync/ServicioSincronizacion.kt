package com.ralenday.memoflash.core.sync

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Android despierta este servicio cuando llega un paquete del otro dispositivo, aunque la app
 * esté cerrada. Así el reloj ya tiene las tarjetas nuevas al abrirlo.
 */
class ServicioSincronizacion : WearableListenerService() {

    override fun onDataChanged(eventos: DataEventBuffer) {
        val sincronizador = Sincronizador.get(this)
        // Se copian los assets antes de liberar el buffer
        val assets = eventos
            .filter { it.type == DataEvent.TYPE_CHANGED && sincronizador.esRutaRemota(it.dataItem.uri.path) }
            .mapNotNull { DataMapItem.fromDataItem(it.dataItem).dataMap.getAsset(Sincronizador.CLAVE_DATOS) }
        // onDataChanged ya corre en un hilo secundario, se puede bloquear
        runBlocking {
            for (asset in assets) {
                try {
                    sincronizador.recibir(asset)
                } catch (e: Exception) {
                    Log.w("MemoFlashSync", "Paquete inválido", e)
                }
            }
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        runBlocking { Sincronizador.get(this@ServicioSincronizacion).actualizarConexion() }
    }
}
