package com.ralenday.memoflash.mobile.navegacion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ralenday.memoflash.mobile.nube.EstadoSesion
import com.ralenday.memoflash.mobile.nube.LocalCuenta
import com.ralenday.memoflash.mobile.pantallas.PantallaAcceso
import com.ralenday.memoflash.mobile.pantallas.PantallaDetalleMazo
import com.ralenday.memoflash.mobile.pantallas.PantallaEditor
import com.ralenday.memoflash.mobile.pantallas.PantallaEstadisticas
import com.ralenday.memoflash.mobile.pantallas.PantallaEstudio
import com.ralenday.memoflash.mobile.pantallas.PantallaImportar
import com.ralenday.memoflash.mobile.pantallas.PantallaMazos

/** Pantallas de la app. Se navega con una pila simple; el botón atrás saca la última. */
sealed interface Pantalla {
    data object Mazos : Pantalla
    data object Estadisticas : Pantalla
    data class DetalleMazo(val mazoId: String) : Pantalla
    data class Editor(val mazoId: String, val tarjetaId: String? = null) : Pantalla
    data class Importar(val mazoId: String) : Pantalla
    data class Estudio(val mazoId: String) : Pantalla
}

/** Cómo se llegó a la pantalla actual; decide la animación de entrada. */
private enum class Movimiento { ADELANTE, ATRAS, PESTANA }

private const val DURACION = 320

@Composable
fun AppMemoFlash() {
    val sesion by LocalCuenta.current.sesion.collectAsState()
    when (sesion) {
        EstadoSesion.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        EstadoSesion.SinSesion -> PantallaAcceso()
        is EstadoSesion.Activa -> NavegacionPrincipal()
    }
}

@Composable
fun NavegacionPrincipal() {
    val pila = remember { mutableStateListOf<Pantalla>(Pantalla.Mazos) }
    var movimiento by remember { mutableStateOf(Movimiento.ADELANTE) }
    // Guarda el estado (scroll, textos) de cada pantalla mientras siga en la pila
    val estados = rememberSaveableStateHolder()

    fun ir(p: Pantalla) { movimiento = Movimiento.ADELANTE; pila.add(p) }
    fun atras() {
        if (pila.size > 1) {
            movimiento = Movimiento.ATRAS
            estados.removeState(pila.removeAt(pila.lastIndex).toString())
        }
    }
    fun pestana(p: Pantalla) {
        if (pila.singleOrNull() == p) return
        movimiento = Movimiento.PESTANA
        pila.forEach { estados.removeState(it.toString()) }
        pila.clear(); pila.add(p)
    }

    BackHandler(enabled = pila.size > 1) { atras() }

    AnimatedContent(
        targetState = pila.last(),
        transitionSpec = { transicion(movimiento) },
        label = "navegacion",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { actual ->
        estados.SaveableStateProvider(actual.toString()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
        }
    }
}

/**
 * Adelante: la nueva pantalla entra deslizándose desde la derecha.
 * Atrás: lo contrario. Entre pestañas: un desvanecido cruzado con un leve zoom.
 */
private fun transicion(movimiento: Movimiento): ContentTransform {
    val curva = tween<Float>(DURACION, easing = FastOutSlowInEasing)
    return when (movimiento) {
        Movimiento.ADELANTE ->
            (slideInHorizontally(tween(DURACION, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(curva)) togetherWith
                (slideOutHorizontally(tween(DURACION, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(DURACION / 2)))
        Movimiento.ATRAS ->
            (slideInHorizontally(tween(DURACION, easing = FastOutSlowInEasing)) { -it / 8 } + fadeIn(curva)) togetherWith
                (slideOutHorizontally(tween(DURACION, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(DURACION / 2)))
        Movimiento.PESTANA ->
            (fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing)) +
                scaleIn(tween(220, delayMillis = 90), initialScale = 0.96f)) togetherWith
                fadeOut(tween(90))
    }.apply { targetContentZIndex = if (movimiento == Movimiento.ATRAS) -1f else 1f }
}
