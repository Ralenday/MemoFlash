package com.ralenday.memoflash.mobile

import android.app.Application
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.core.Mazo
import com.ralenday.memoflash.core.Tarjeta
import com.ralenday.memoflash.core.sync.Sincronizador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MemoFlashApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        scope.launch(Dispatchers.IO) { crearMazoDeEjemplo() }
        Sincronizador.get(this).iniciar(scope)
    }

    /** La primera vez se crea un mazo para poder probar la app y el reloj de inmediato. */
    private fun crearMazoDeEjemplo() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("ejemplo_creado", false)) return
        val db = BaseDatos.get(this)
        val mazo = Mazo(nombre = "Inglés básico", descripcion = "Vocabulario de ejemplo")
        db.guardarMazo(mazo)
        db.insertarTarjetas(
            listOf(
                "Apple" to "Manzana", "Dog" to "Perro", "House" to "Casa",
                "Book" to "Libro", "Water" to "Agua", "Friend" to "Amigo",
                "To learn" to "Aprender", "Tomorrow" to "Mañana",
            ).map { (frente, reverso) -> Tarjeta(mazoUuid = mazo.uuid, frente = frente, reverso = reverso) },
        )
        prefs.edit().putBoolean("ejemplo_creado", true).apply()
    }
}
