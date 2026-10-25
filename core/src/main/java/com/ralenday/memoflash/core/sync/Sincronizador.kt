package com.ralenday.memoflash.core.sync

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.core.Origen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class EstadoSync(
    /** El otro dispositivo está conectado y tiene MemoFlash instalada. */
    val conectado: Boolean = false,
    val nombreOtro: String? = null,
    val ultimaSincronizacion: Long = 0,
    val sincronizando: Boolean = false,
    val error: String? = null,
)

/**
 * Sincroniza la base de datos de este dispositivo con la del otro usando la Wearable Data
 * Layer API (Bluetooth o Wi-Fi, sin internet ni servidor).
 *
 * Cada dispositivo publica un DataItem con su [Paquete] en su propia ruta. Google Play Services
 * lo entrega al otro en cuanto se vuelven a conectar, aunque hayan estado separados días.
 *
 * Reglas para que nunca haya conflictos:
 * 1. El teléfono es el dueño del contenido (mazos y tarjetas). El reloj solo los copia.
 * 2. El historial de repasos solo crece: se unen los dos historiales por uuid.
 * 3. La programación de cada tarjeta se recalcula a partir del historial unido, así que
 *    ambos dispositivos llegan siempre al mismo resultado.
 */
class Sincronizador private constructor(private val context: Context) {

    private val db = BaseDatos.get(context)
    private val dataClient = Wearable.getDataClient(context)
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    val soyReloj: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    private val miRuta = if (soyReloj) RUTA_RELOJ else RUTA_TELEFONO
    private val rutaRemota = if (soyReloj) RUTA_TELEFONO else RUTA_RELOJ
    private val capacidadRemota = if (soyReloj) CAPACIDAD_TELEFONO else CAPACIDAD_RELOJ

    private val _estado = MutableStateFlow(EstadoSync(ultimaSincronizacion = prefs.getLong("ultima", 0)))
    val estado: StateFlow<EstadoSync> = _estado

    /** Publica automáticamente cada vez que el usuario cambia algo en este dispositivo. */
    @OptIn(FlowPreview::class)
    fun iniciar(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            db.cambiosLocales.debounce(1_500).collect { publicar() }
        }
        scope.launch(Dispatchers.IO) { sincronizarAhora() }
    }

    /** Publica lo local y lee lo último que haya dejado el otro dispositivo. */
    suspend fun sincronizarAhora() {
        _estado.update { it.copy(sincronizando = true, error = null) }
        try {
            actualizarConexion()
            publicar()
            val items = dataClient.getDataItems(Uri.parse("wear://*$rutaRemota")).await()
            try {
                for (item in items) {
                    val asset = DataMapItem.fromDataItem(item).dataMap.getAsset(CLAVE_DATOS) ?: continue
                    recibir(asset)
                }
            } finally {
                items.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo sincronizar", e)
            _estado.update { it.copy(error = e.message ?: "Error desconocido") }
        } finally {
            _estado.update { it.copy(sincronizando = false) }
        }
    }

    suspend fun actualizarConexion() {
        try {
            val nodos = Wearable.getCapabilityClient(context)
                .getCapability(capacidadRemota, CapabilityClient.FILTER_REACHABLE).await().nodes
            _estado.update { it.copy(conectado = nodos.isNotEmpty(), nombreOtro = nodos.firstOrNull()?.displayName) }
        } catch (e: Exception) {
            _estado.update { it.copy(conectado = false, nombreOtro = null) }
        }
    }

    /** Escribe el paquete de este dispositivo en la Data Layer. */
    suspend fun publicar() = mutex.withLock {
        val paquete = if (soyReloj) {
            Paquete(historial = db.repasosSinConfirmar(Origen.RELOJ))
        } else {
            val mazos = db.mazosParaReloj()
            val tarjetas = db.tarjetasDeMazos(mazos.map { it.uuid })
            Paquete(mazos, tarjetas, db.historialDeTarjetas(tarjetas.map { it.uuid }))
        }
        val solicitud = PutDataMapRequest.create(miRuta).apply {
            // El Asset no tiene el límite de 100 KB de un DataItem normal
            dataMap.putAsset(CLAVE_DATOS, Asset.createFromBytes(paquete.aBytes()))
            dataMap.putLong("generado", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        try {
            dataClient.putDataItem(solicitud).await()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo publicar", e)
            _estado.update { it.copy(error = e.message ?: "Sin Google Play Services") }
        }
    }

    /** Une el paquete que llegó del otro dispositivo con la base local. */
    suspend fun recibir(asset: Asset) {
        val bytes = dataClient.getFdForAsset(asset).await().inputStream.use { it.readBytes() }
        val paquete = Paquete.deBytes(bytes)
        var volverAPublicar: Boolean
        mutex.withLock {
            if (soyReloj) {
                db.reemplazarContenido(paquete.mazos, paquete.tarjetas)
                db.unirRepasos(paquete.historial)
                // Lo que el teléfono ya trae de vuelta está confirmado
                db.marcarSincronizados(paquete.historial.filter { it.origen == Origen.RELOJ }.map { it.uuid })
                db.recalcularTodo()
                volverAPublicar = db.repasosSinConfirmar(Origen.RELOJ).isNotEmpty()
            } else {
                // Si llegaron repasos nuevos, se le devuelve al reloj la versión actualizada
                volverAPublicar = db.unirRepasos(paquete.historial).isNotEmpty()
            }
        }
        if (volverAPublicar) publicar()
        val ahora = System.currentTimeMillis()
        prefs.edit().putLong("ultima", ahora).apply()
        _estado.update { it.copy(ultimaSincronizacion = ahora, error = null) }
    }

    /** Ruta que este dispositivo debe escuchar. */
    internal fun esRutaRemota(ruta: String?) = ruta == rutaRemota

    companion object {
        private const val TAG = "MemoFlashSync"
        const val RUTA_TELEFONO = "/memoflash/telefono"
        const val RUTA_RELOJ = "/memoflash/reloj"
        const val CLAVE_DATOS = "datos"

        // Declaradas en res/values/wear.xml de cada app
        const val CAPACIDAD_TELEFONO = "memoflash_telefono"
        const val CAPACIDAD_RELOJ = "memoflash_reloj"

        @Volatile
        private var instancia: Sincronizador? = null

        fun get(context: Context): Sincronizador = instancia ?: synchronized(this) {
            instancia ?: Sincronizador(context.applicationContext).also { instancia = it }
        }
    }
}
