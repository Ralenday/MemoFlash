package com.ralenday.memoflash.mobile.datos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import com.ralenday.memoflash.core.BaseDatos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalBaseDatos = staticCompositionLocalOf<BaseDatos> { error("Falta BaseDatos") }

/** Lee de SQLite en segundo plano y vuelve a leer cada vez que la base cambia. */
@Composable
fun <T> rememberConsulta(vararg claves: Any?, leer: BaseDatos.() -> T): T? {
    val db = LocalBaseDatos.current
    val version by db.cambios.collectAsState()
    val resultado by produceState<T?>(null, version, *claves) {
        value = withContext(Dispatchers.IO) { db.leer() }
    }
    return resultado
}
