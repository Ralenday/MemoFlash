package com.ralenday.memoflash.mobile

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ralenday.memoflash.core.BaseDatos

/** Pantallas de la app. Se navega con una pila simple; el botón atrás saca la última. */
sealed interface Pantalla {
    data object Mazos : Pantalla
    data object Estadisticas : Pantalla
    data class DetalleMazo(val mazoId: String) : Pantalla
    data class Editor(val mazoId: String, val tarjetaId: String? = null) : Pantalla
    data class Importar(val mazoId: String) : Pantalla
    data class Estudio(val mazoId: String) : Pantalla
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val db = BaseDatos.get(this)
        setContent {
            TemaMemoFlash {
                CompositionLocalProvider(LocalBaseDatos provides db) { AppMemoFlash() }
            }
        }
    }
}

@Composable
fun AppMemoFlash() {
    val pila = remember { mutableStateListOf<Pantalla>(Pantalla.Mazos) }
    val actual = pila.last()
    fun ir(p: Pantalla) { pila.add(p) }
    fun atras() { if (pila.size > 1) pila.removeAt(pila.lastIndex) }
    fun pestana(p: Pantalla) { pila.clear(); pila.add(p) }

    BackHandler(enabled = pila.size > 1) { atras() }

    when (actual) {
        Pantalla.Mazos -> PantallaMazos(
            abrirMazo = { ir(Pantalla.DetalleMazo(it)) },
            irAEstadisticas = { pestana(Pantalla.Estadisticas) },
        )
        Pantalla.Estadisticas -> PantallaEstadisticas(irAMazos = { pestana(Pantalla.Mazos) })
        is Pantalla.DetalleMazo -> PantallaDetalleMazo(
            mazoId = actual.mazoId,
            atras = ::atras,
            estudiar = { ir(Pantalla.Estudio(actual.mazoId)) },
            editar = { ir(Pantalla.Editor(actual.mazoId, it)) },
            importar = { ir(Pantalla.Importar(actual.mazoId)) },
        )
        is Pantalla.Editor -> PantallaEditor(actual.mazoId, actual.tarjetaId, cerrar = ::atras)
        is Pantalla.Importar -> PantallaImportar(actual.mazoId, cerrar = ::atras)
        is Pantalla.Estudio -> PantallaEstudio(actual.mazoId, cerrar = ::atras)
    }
}

@Composable
fun TemaMemoFlash(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    val context = LocalContext.current
    val colores = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        oscuro -> darkColorScheme(primary = Color(0xFF7FB8E6))
        else -> lightColorScheme(primary = Color(0xFF0B5C8A))
    }
    MaterialTheme(colorScheme = colores, content = contenido)
}
