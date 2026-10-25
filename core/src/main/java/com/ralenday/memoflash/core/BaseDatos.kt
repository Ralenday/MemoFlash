package com.ralenday.memoflash.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Base de datos SQLite local. Cada dispositivo (teléfono y reloj) tiene la suya y funciona
 * sin conexión; [com.ralenday.memoflash.core.sync.Sincronizador] las mantiene iguales.
 */
class BaseDatos private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "memoflash.db", null, VERSION) {

    /** Cambia con cada escritura: la UI lo observa para volver a leer. */
    private val _cambios = MutableStateFlow(0L)
    val cambios: StateFlow<Long> = _cambios

    /** Solo cambios hechos por el usuario en este dispositivo (no los que llegan sincronizados). */
    private val _cambiosLocales = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cambiosLocales: SharedFlow<Unit> = _cambiosLocales

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE mazos (
                uuid        TEXT PRIMARY KEY,
                nombre      TEXT NOT NULL,
                descripcion TEXT NOT NULL DEFAULT '',
                en_reloj    INTEGER NOT NULL DEFAULT 1,
                actualizado INTEGER NOT NULL
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE tarjetas (
                uuid             TEXT PRIMARY KEY,
                mazo_uuid        TEXT NOT NULL REFERENCES mazos(uuid) ON DELETE CASCADE,
                frente           TEXT NOT NULL,
                reverso          TEXT NOT NULL,
                imagen_frente    TEXT,
                imagen_reverso   TEXT,
                nivel_dificultad REAL NOT NULL DEFAULT ${RepeticionEspaciada.FACILIDAD_INICIAL},
                intervalo_dias   REAL NOT NULL DEFAULT 0,
                repeticiones     INTEGER NOT NULL DEFAULT 0,
                proximo_repaso   INTEGER NOT NULL DEFAULT 0,
                actualizado      INTEGER NOT NULL
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE historial_repasos (
                uuid                 TEXT PRIMARY KEY,
                tarjeta_uuid         TEXT NOT NULL REFERENCES tarjetas(uuid) ON DELETE CASCADE,
                fecha                INTEGER NOT NULL,
                calificacion         INTEGER NOT NULL,
                intervalo_resultante REAL NOT NULL,
                origen               TEXT NOT NULL,
                sincronizado         INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        db.execSQL("CREATE INDEX idx_tarjetas_mazo ON tarjetas(mazo_uuid, proximo_repaso)")
        db.execSQL("CREATE INDEX idx_historial_tarjeta ON historial_repasos(tarjeta_uuid)")
        db.execSQL("CREATE INDEX idx_historial_fecha ON historial_repasos(fecha)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------------------------------------------------------------- Mazos

    fun mazos(ahora: Long = System.currentTimeMillis()): List<ResumenMazo> =
        readableDatabase.rawQuery(
            """
            SELECT m.*,
                   COUNT(t.uuid) AS total,
                   COALESCE(SUM(t.proximo_repaso <= ?), 0) AS pendientes
            FROM mazos m LEFT JOIN tarjetas t ON t.mazo_uuid = m.uuid
            GROUP BY m.uuid
            ORDER BY m.nombre COLLATE NOCASE
            """,
            arrayOf(ahora.toString()),
        ).lista { ResumenMazo(leerMazo(), getInt(col("total")), getInt(col("pendientes"))) }

    fun mazo(uuid: String): Mazo? =
        readableDatabase.rawQuery("SELECT * FROM mazos WHERE uuid = ?", arrayOf(uuid))
            .lista { leerMazo() }.firstOrNull()

    fun guardarMazo(mazo: Mazo) {
        writableDatabase.insertWithOnConflict("mazos", null, mazo.valores(), SQLiteDatabase.CONFLICT_REPLACE)
        notificar(local = true)
    }

    fun eliminarMazo(uuid: String) {
        writableDatabase.delete("mazos", "uuid = ?", arrayOf(uuid))
        notificar(local = true)
    }

    // ---------------------------------------------------------------- Tarjetas

    fun tarjetas(mazoUuid: String): List<Tarjeta> =
        readableDatabase.rawQuery(
            "SELECT * FROM tarjetas WHERE mazo_uuid = ? ORDER BY frente COLLATE NOCASE",
            arrayOf(mazoUuid),
        ).lista { leerTarjeta() }

    fun todasLasTarjetas(): List<Tarjeta> =
        readableDatabase.rawQuery("SELECT * FROM tarjetas", null).lista { leerTarjeta() }

    fun tarjeta(uuid: String): Tarjeta? =
        readableDatabase.rawQuery("SELECT * FROM tarjetas WHERE uuid = ?", arrayOf(uuid))
            .lista { leerTarjeta() }.firstOrNull()

    /** Tarjetas que ya toca repasar, la más atrasada primero. */
    fun pendientes(mazoUuid: String, ahora: Long = System.currentTimeMillis(), limite: Int = 200): List<Tarjeta> =
        readableDatabase.rawQuery(
            "SELECT * FROM tarjetas WHERE mazo_uuid = ? AND proximo_repaso <= ? ORDER BY proximo_repaso LIMIT $limite",
            arrayOf(mazoUuid, ahora.toString()),
        ).lista { leerTarjeta() }

    /** Crea o edita una tarjeta. Al editar se conserva su progreso de estudio. */
    fun guardarTarjeta(tarjeta: Tarjeta) {
        val db = writableDatabase
        val valores = tarjeta.valores()
        val actualizadas = db.update(
            "tarjetas",
            ContentValues(valores).apply {
                remove("nivel_dificultad"); remove("intervalo_dias")
                remove("repeticiones"); remove("proximo_repaso")
            },
            "uuid = ?",
            arrayOf(tarjeta.uuid),
        )
        if (actualizadas == 0) db.insert("tarjetas", null, valores)
        tocarMazo(tarjeta.mazoUuid)
        notificar(local = true)
    }

    fun insertarTarjetas(tarjetas: List<Tarjeta>) {
        if (tarjetas.isEmpty()) return
        transaccion {
            tarjetas.forEach { insert("tarjetas", null, it.valores()) }
            tarjetas.map { it.mazoUuid }.distinct().forEach { tocarMazo(it) }
        }
        notificar(local = true)
    }

    fun eliminarTarjeta(uuid: String) {
        writableDatabase.delete("tarjetas", "uuid = ?", arrayOf(uuid))
        notificar(local = true)
    }

    // ---------------------------------------------------------------- Repasos

    /** Guarda la respuesta del usuario y reprograma la tarjeta. */
    fun registrarRepaso(
        tarjeta: Tarjeta,
        calificacion: Calificacion,
        origen: Origen,
        ahora: Long = System.currentTimeMillis(),
    ): Tarjeta {
        val nuevoEstado = RepeticionEspaciada.calificar(tarjeta.estado, calificacion, ahora)
        val repaso = Repaso(
            tarjetaUuid = tarjeta.uuid,
            fecha = ahora,
            calificacion = calificacion,
            intervaloResultante = nuevoEstado.intervaloDias,
            origen = origen,
        )
        transaccion {
            insert("historial_repasos", null, repaso.valores(sincronizado = false))
            update("tarjetas", nuevoEstado.valores(), "uuid = ?", arrayOf(tarjeta.uuid))
        }
        notificar(local = true)
        return tarjeta.copy(estado = nuevoEstado)
    }

    fun historial(desde: Long = 0): List<Repaso> =
        readableDatabase.rawQuery(
            "SELECT * FROM historial_repasos WHERE fecha >= ? ORDER BY fecha",
            arrayOf(desde.toString()),
        ).lista { leerRepaso() }

    // ---------------------------------------------------------------- Sincronización

    fun mazosParaReloj(): List<Mazo> =
        readableDatabase.rawQuery("SELECT * FROM mazos WHERE en_reloj = 1", null).lista { leerMazo() }

    fun tarjetasDeMazos(mazos: Collection<String>): List<Tarjeta> =
        mazos.flatMap { tarjetas(it) }

    fun historialDeTarjetas(tarjetas: Collection<String>): List<Repaso> {
        val ids = tarjetas.toSet()
        return historial().filter { it.tarjetaUuid in ids }
    }

    /** Repasos hechos aquí que el otro dispositivo aún no confirma haber recibido. */
    fun repasosSinConfirmar(origen: Origen): List<Repaso> =
        readableDatabase.rawQuery(
            "SELECT * FROM historial_repasos WHERE sincronizado = 0 AND origen = ? ORDER BY fecha",
            arrayOf(origen.name),
        ).lista { leerRepaso() }

    /**
     * Une repasos que llegan del otro dispositivo. Ignora los que ya existen y los de tarjetas
     * que ya no existen. Devuelve las tarjetas afectadas, ya recalculadas.
     */
    fun unirRepasos(repasos: List<Repaso>): Set<String> {
        if (repasos.isEmpty()) return emptySet()
        val afectadas = mutableSetOf<String>()
        transaccion {
            for (repaso in repasos) {
                if (!existe("tarjetas", repaso.tarjetaUuid)) continue
                val id = insertWithOnConflict(
                    "historial_repasos", null, repaso.valores(sincronizado = true),
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                if (id != -1L) afectadas += repaso.tarjetaUuid
            }
            afectadas.forEach { recalcular(it) }
        }
        if (afectadas.isNotEmpty()) notificar(local = false)
        return afectadas
    }

    fun marcarSincronizados(uuids: Collection<String>) {
        if (uuids.isEmpty()) return
        transaccion {
            uuids.forEach {
                update("historial_repasos", ContentValues().apply { put("sincronizado", 1) }, "uuid = ?", arrayOf(it))
            }
        }
    }

    /**
     * En el reloj: reemplaza mazos y tarjetas por la copia que manda el teléfono (el teléfono
     * es el dueño del contenido). Lo que ya no viene se borra junto con su historial.
     */
    fun reemplazarContenido(mazos: List<Mazo>, tarjetas: List<Tarjeta>) {
        transaccion {
            val idsMazos = mazos.map { it.uuid }.toSet()
            val idsTarjetas = tarjetas.map { it.uuid }.toSet()
            ids("mazos").filterNot { it in idsMazos }.forEach { delete("mazos", "uuid = ?", arrayOf(it)) }
            ids("tarjetas").filterNot { it in idsTarjetas }.forEach { delete("tarjetas", "uuid = ?", arrayOf(it)) }
            mazos.forEach { insertWithOnConflict("mazos", null, it.valores(), SQLiteDatabase.CONFLICT_REPLACE) }
            for (tarjeta in tarjetas) {
                // UPDATE en vez de REPLACE para no borrar en cascada el historial de la tarjeta
                if (update("tarjetas", tarjeta.valores(), "uuid = ?", arrayOf(tarjeta.uuid)) == 0) {
                    insert("tarjetas", null, tarjeta.valores())
                }
            }
        }
        notificar(local = false)
    }

    /** Recalcula todas las tarjetas a partir de su historial (lo usa el reloj tras sincronizar). */
    fun recalcularTodo() {
        transaccion { ids("tarjetas").forEach { recalcular(it) } }
        notificar(local = false)
    }

    // ---------------------------------------------------------------- Utilidades internas

    private fun SQLiteDatabase.recalcular(tarjetaUuid: String) {
        val historial = rawQuery(
            "SELECT * FROM historial_repasos WHERE tarjeta_uuid = ?", arrayOf(tarjetaUuid),
        ).lista { leerRepaso() }
        val estado = RepeticionEspaciada.reconstruir(historial)
        update("tarjetas", estado.valores(), "uuid = ?", arrayOf(tarjetaUuid))
    }

    private fun SQLiteDatabase.existe(tabla: String, uuid: String): Boolean =
        rawQuery("SELECT 1 FROM $tabla WHERE uuid = ?", arrayOf(uuid)).use { it.moveToFirst() }

    private fun SQLiteDatabase.ids(tabla: String): List<String> =
        rawQuery("SELECT uuid FROM $tabla", null).lista { getString(0) }

    private fun tocarMazo(uuid: String) {
        writableDatabase.update(
            "mazos", ContentValues().apply { put("actualizado", System.currentTimeMillis()) },
            "uuid = ?", arrayOf(uuid),
        )
    }

    private inline fun transaccion(bloque: SQLiteDatabase.() -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.bloque()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun notificar(local: Boolean) {
        _cambios.update { it + 1 }
        if (local) _cambiosLocales.tryEmit(Unit)
    }

    companion object {
        private const val VERSION = 1

        @Volatile
        private var instancia: BaseDatos? = null

        fun get(context: Context): BaseDatos =
            instancia ?: synchronized(this) { instancia ?: BaseDatos(context).also { instancia = it } }
    }
}

// ---------------------------------------------------------------- Conversión filas <-> objetos

private fun <T> Cursor.lista(leer: Cursor.() -> T): List<T> = use {
    buildList { while (moveToNext()) add(leer()) }
}

private fun Cursor.col(nombre: String) = getColumnIndexOrThrow(nombre)
private fun Cursor.texto(nombre: String): String? = col(nombre).let { if (isNull(it)) null else getString(it) }

private fun Cursor.leerMazo() = Mazo(
    uuid = getString(col("uuid")),
    nombre = getString(col("nombre")),
    descripcion = getString(col("descripcion")),
    enReloj = getInt(col("en_reloj")) == 1,
    actualizado = getLong(col("actualizado")),
)

private fun Cursor.leerTarjeta() = Tarjeta(
    uuid = getString(col("uuid")),
    mazoUuid = getString(col("mazo_uuid")),
    frente = getString(col("frente")),
    reverso = getString(col("reverso")),
    imagenFrente = texto("imagen_frente"),
    imagenReverso = texto("imagen_reverso"),
    estado = EstadoTarjeta(
        nivelDificultad = getDouble(col("nivel_dificultad")),
        intervaloDias = getDouble(col("intervalo_dias")),
        repeticiones = getInt(col("repeticiones")),
        proximoRepaso = getLong(col("proximo_repaso")),
    ),
    actualizado = getLong(col("actualizado")),
)

private fun Cursor.leerRepaso() = Repaso(
    uuid = getString(col("uuid")),
    tarjetaUuid = getString(col("tarjeta_uuid")),
    fecha = getLong(col("fecha")),
    calificacion = Calificacion.de(getInt(col("calificacion"))),
    intervaloResultante = getDouble(col("intervalo_resultante")),
    origen = Origen.valueOf(getString(col("origen"))),
)

private fun Mazo.valores() = ContentValues().apply {
    put("uuid", uuid)
    put("nombre", nombre)
    put("descripcion", descripcion)
    put("en_reloj", if (enReloj) 1 else 0)
    put("actualizado", actualizado)
}

private fun EstadoTarjeta.valores() = ContentValues().apply {
    put("nivel_dificultad", nivelDificultad)
    put("intervalo_dias", intervaloDias)
    put("repeticiones", repeticiones)
    put("proximo_repaso", proximoRepaso)
}

private fun Tarjeta.valores() = ContentValues().apply {
    put("uuid", uuid)
    put("mazo_uuid", mazoUuid)
    put("frente", frente)
    put("reverso", reverso)
    put("imagen_frente", imagenFrente)
    put("imagen_reverso", imagenReverso)
    putAll(estado.valores())
    put("actualizado", actualizado)
}

private fun Repaso.valores(sincronizado: Boolean) = ContentValues().apply {
    put("uuid", uuid)
    put("tarjeta_uuid", tarjetaUuid)
    put("fecha", fecha)
    put("calificacion", calificacion.valor)
    put("intervalo_resultante", intervaloResultante)
    put("origen", origen.name)
    put("sincronizado", if (sincronizado) 1 else 0)
}
