package com.ralenday.memoflash.core.sync

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

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
    private val esEmulador: Boolean = Build.FINGERPRINT.contains("generic") ||
        Build.FINGERPRINT.contains("emulator") ||
        Build.MODEL.contains("sdk_g") ||
        Build.HARDWARE.contains("ranchu")
    private val miRuta = if (soyReloj) RUTA_RELOJ else RUTA_TELEFONO
    private val rutaRemota = if (soyReloj) RUTA_TELEFONO else RUTA_RELOJ
    private val capacidadRemota = if (soyReloj) CAPACIDAD_TELEFONO else CAPACIDAD_RELOJ
    private val miPuertoLocal = if (soyReloj) PUERTO_RELOJ else PUERTO_TELEFONO
    private val puertoRemotoLocal = if (soyReloj) PUERTO_TELEFONO else PUERTO_RELOJ
    private val nombreEmuladorRemoto = if (soyReloj) "Teléfono (emulador)" else "Reloj (emulador)"

    private val _estado = MutableStateFlow(EstadoSync(ultimaSincronizacion = prefs.getLong("ultima", 0)))
    val estado: StateFlow<EstadoSync> = _estado

    /** Publica automáticamente cada vez que el usuario cambia algo en este dispositivo. */
    @OptIn(FlowPreview::class)
    fun iniciar(scope: CoroutineScope, publicarAlArrancar: Boolean = true) {
        if (esEmulador) iniciarServidorLocal(scope)
        scope.launch(Dispatchers.IO) {
            db.cambiosLocales.debounce(1_500).collect { publicar() }
        }
        // En el teléfono no se publica hasta haber entrado a la cuenta: si no, un arranque
        // sin mazos borraría lo que el reloj ya tiene.
        if (publicarAlArrancar) scope.launch(Dispatchers.IO) { sincronizarAhora() }
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
            if (!esEmulador || !_estado.value.conectado) {
                _estado.update { it.copy(error = e.message ?: "Error desconocido") }
            }
        } finally {
            _estado.update { it.copy(sincronizando = false) }
        }
    }

    suspend fun actualizarConexion() {
        val nodos = try {
            Wearable.getCapabilityClient(context)
                .getCapability(capacidadRemota, CapabilityClient.FILTER_REACHABLE).await().nodes
        } catch (_: Exception) {
            emptySet()
        }
        if (nodos.isNotEmpty()) {
            _estado.update { it.copy(conectado = true, nombreOtro = nodos.firstOrNull()?.displayName) }
            return
        }
        if (esEmulador && probarPuenteEmulador()) {
            _estado.update { it.copy(conectado = true, nombreOtro = nombreEmuladorRemoto) }
            return
        }
        _estado.update { it.copy(conectado = false, nombreOtro = null) }
    }

    /** Escribe el paquete de este dispositivo en la Data Layer. */
    suspend fun publicar() {
        val paquete = mutex.withLock { armarPaqueteLocal() }
        val solicitud = PutDataMapRequest.create(miRuta).apply {
            // El Asset no tiene el límite de 100 KB de un DataItem normal
            dataMap.putAsset(CLAVE_DATOS, Asset.createFromBytes(paquete.aBytes()))
            dataMap.putLong("generado", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        try {
            dataClient.putDataItem(solicitud).await()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo publicar", e)
            if (!esEmulador) {
                _estado.update { it.copy(error = e.message ?: "Sin Google Play Services") }
            }
        }
        if (esEmulador) {
            intercambiarPorPuenteEmulador(paquete)
        }
    }

    /** Une el paquete que llegó del otro dispositivo con la base local. */
    suspend fun recibir(asset: Asset) {
        val bytes = dataClient.getFdForAsset(asset).await().inputStream.use { it.readBytes() }
        val volverAPublicar = integrarPaqueteRemoto(Paquete.deBytes(bytes))
        if (volverAPublicar) publicar()
    }

    private fun armarPaqueteLocal(): Paquete = if (soyReloj) {
        Paquete(historial = db.repasosSinConfirmar(Origen.RELOJ))
    } else {
        val mazos = db.mazosParaReloj()
        val tarjetas = db.tarjetasDeMazos(mazos.map { it.uuid })
        Paquete(mazos, tarjetas, db.historialDeTarjetas(tarjetas.map { it.uuid }))
    }

    private suspend fun integrarPaqueteRemoto(paquete: Paquete): Boolean {
        val volverAPublicar = mutex.withLock {
            if (soyReloj) {
                db.reemplazarContenido(paquete.mazos, paquete.tarjetas)
                db.unirRepasos(paquete.historial)
                // Lo que el teléfono ya trae de vuelta está confirmado
                db.marcarSincronizados(paquete.historial.filter { it.origen == Origen.RELOJ }.map { it.uuid })
                db.recalcularTodo()
                db.repasosSinConfirmar(Origen.RELOJ).isNotEmpty()
            } else {
                // Si llegaron repasos nuevos, se le devuelve al reloj la versión actualizada
                db.unirRepasos(paquete.historial).isNotEmpty()
            }
        }
        marcarSincronizadoAhora()
        return volverAPublicar
    }

    private fun marcarSincronizadoAhora() {
        val ahora = System.currentTimeMillis()
        prefs.edit().putLong("ultima", ahora).apply()
        _estado.update { it.copy(ultimaSincronizacion = ahora, error = null) }
    }

    /**
     * Puente local para emuladores sin emparejamiento de Google Play Services:
     * cada emulador escucha en su puerto local y se conecta al otro a través del host (10.0.2.2)
     * usando `adb forward`.
     */
    private fun iniciarServidorLocal(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                ServerSocket(miPuertoLocal, 8, InetAddress.getByName("0.0.0.0")).use { servidor ->
                    while (isActive) {
                        val cliente = servidor.accept()
                        scope.launch(Dispatchers.IO) { atenderClienteLocal(cliente) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo abrir puerto local de emulador $miPuertoLocal", e)
            }
        }
    }

    private suspend fun atenderClienteLocal(cliente: Socket) {
        try {
            cliente.use { socket ->
                socket.soTimeout = TIMEOUT_EMULADOR_MS
                val entrada = DataInputStream(socket.getInputStream())
                val salida = DataOutputStream(socket.getOutputStream())
                when (entrada.readByte().toInt()) {
                    CMD_PING -> {
                        salida.writeByte(CMD_OK)
                        salida.flush()
                        _estado.update { it.copy(conectado = true, nombreOtro = nombreEmuladorRemoto) }
                    }
                    CMD_SYNC -> {
                        val tamano = entrada.readInt()
                        val bytesRemotos = ByteArray(tamano)
                        entrada.readFully(bytesRemotos)
                        integrarPaqueteRemoto(Paquete.deBytes(bytesRemotos))
                        val respuesta = mutex.withLock { armarPaqueteLocal().aBytes() }
                        salida.writeInt(respuesta.size)
                        salida.write(respuesta)
                        salida.flush()
                        _estado.update { it.copy(conectado = true, nombreOtro = nombreEmuladorRemoto) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Conexión local de emulador finalizada: ${e.message}")
        }
    }

    private fun probarPuenteEmulador(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(HOST_EMULADOR, puertoRemotoLocal), TIMEOUT_EMULADOR_MS)
            socket.soTimeout = TIMEOUT_EMULADOR_MS
            val salida = DataOutputStream(socket.getOutputStream())
            val entrada = DataInputStream(socket.getInputStream())
            salida.writeByte(CMD_PING)
            salida.flush()
            entrada.readByte().toInt() == CMD_OK
        }
    } catch (_: Exception) {
        false
    }

    private suspend fun intercambiarPorPuenteEmulador(paqueteInicial: Paquete) {
        try {
            val respuesta = enviarPaquetePorSocket(paqueteInicial.aBytes())
            val necesitaConfirmacion = integrarPaqueteRemoto(Paquete.deBytes(respuesta))
            if (necesitaConfirmacion) {
                val actualizado = mutex.withLock { armarPaqueteLocal().aBytes() }
                val eco = enviarPaquetePorSocket(actualizado)
                integrarPaqueteRemoto(Paquete.deBytes(eco))
            }
            _estado.update { it.copy(conectado = true, nombreOtro = nombreEmuladorRemoto, error = null) }
        } catch (e: Exception) {
            Log.d(TAG, "Puente de emulador no disponible en $puertoRemotoLocal: ${e.message}")
        }
    }

    private fun enviarPaquetePorSocket(bytes: ByteArray): ByteArray = Socket().use { socket ->
        socket.connect(InetSocketAddress(HOST_EMULADOR, puertoRemotoLocal), TIMEOUT_EMULADOR_MS)
        socket.soTimeout = TIMEOUT_EMULADOR_MS
        val salida = DataOutputStream(socket.getOutputStream())
        val entrada = DataInputStream(socket.getInputStream())
        salida.writeByte(CMD_SYNC)
        salida.writeInt(bytes.size)
        salida.write(bytes)
        salida.flush()
        val tamanoRespuesta = entrada.readInt()
        ByteArray(tamanoRespuesta).also { entrada.readFully(it) }
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

        private const val HOST_EMULADOR = "10.0.2.2"
        private const val PUERTO_TELEFONO = 19876
        private const val PUERTO_RELOJ = 19877
        private const val TIMEOUT_EMULADOR_MS = 2_000
        private const val CMD_PING = 1
        private const val CMD_SYNC = 2
        private const val CMD_OK = 3

        @Volatile
        private var instancia: Sincronizador? = null

        fun get(context: Context): Sincronizador = instancia ?: synchronized(this) {
            instancia ?: Sincronizador(context.applicationContext).also { instancia = it }
        }
    }
}
