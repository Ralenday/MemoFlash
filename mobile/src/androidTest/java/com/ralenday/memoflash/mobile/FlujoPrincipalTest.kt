package com.ralenday.memoflash.mobile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorre la app como lo haría una persona: crea un mazo, le agrega una tarjeta, la estudia,
 * revisa las estadísticas y al final borra el mazo para dejar el emulador como estaba.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FlujoPrincipalTest {

    init {
        AccesoDepuracion.omitirCuenta = true
    }

    @get:Rule
    val regla = createAndroidComposeRule<MainActivity>()

    private fun esperarTexto(texto: String) {
        regla.waitUntil(10_000) { regla.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun crearEstudiarYBorrarUnMazo() {
        val nombre = "Prueba ${System.currentTimeMillis()}"

        // Crear el mazo desde la pantalla principal
        regla.onNodeWithContentDescription("Nuevo mazo").performClick()
        regla.onAllNodes(hasSetTextAction())[0].performTextInput(nombre)
        regla.onNodeWithText("Guardar").performClick()
        regla.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(nombre))
        esperarTexto(nombre)
        regla.onNodeWithText(nombre).performClick()

        // Mazo vacío: no se puede estudiar todavía
        esperarTexto("Este mazo está vacío")
        regla.onNodeWithText("Todo al día").assertExists()

        // Agregar una tarjeta
        regla.onNodeWithContentDescription("Nueva tarjeta").performClick()
        val campos = regla.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("Gato")
        campos[1].performTextInput("Cat")
        // Con el teclado abierto el botón queda abajo: hay que desplazarse hasta él
        regla.onNodeWithText("Guardar").performScrollTo().performClick()
        esperarTexto("Estudiar")

        // Estudiarla
        regla.onNodeWithText("Estudiar").performClick()
        esperarTexto("Mostrar respuesta")
        regla.onNodeWithText("Gato").assertExists()
        regla.onNodeWithText("Mostrar respuesta").performClick()
        esperarTexto("Cat")
        regla.onNodeWithText("Fácil").performClick()
        esperarTexto("Sesión terminada")
        regla.onNodeWithText("Repasaste 1 tarjeta · 100% sin dificultad").assertExists()
        regla.onNodeWithText("Volver al mazo").performClick()
        esperarTexto("Todo al día")

        // Borrar el mazo
        regla.onNodeWithContentDescription("Más opciones").performClick()
        regla.onNodeWithText("Eliminar mazo").performClick()
        regla.onNodeWithText("Eliminar").performClick()
        regla.waitUntil(10_000) { regla.onAllNodesWithText(nombre).fetchSemanticsNodes().isEmpty() }
        regla.onNodeWithText("MemoFlash").assertExists()
    }

    @Test
    fun cambiarEntrePestanas() {
        regla.onNodeWithText("Estadísticas").performClick()
        esperarTexto("Retención")
        regla.onNodeWithText("Repasos por día").assertExists()
        regla.onNodeWithText("Mazos").performClick()
        esperarTexto("Para hoy")
    }
}
