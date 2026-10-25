package com.ralenday.memoflash.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.RepeticionEspaciada
import com.ralenday.memoflash.core.Tarjeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colores de los botones de respuesta (los mismos en el reloj). */
val ColorDificil = Color(0xFFE5484D)
val ColorRegular = Color(0xFFE59A00)
val ColorFacil = Color(0xFF2E9E5B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEstudio(mazoId: String, cerrar: () -> Unit) {
    val db = LocalBaseDatos.current
    val scope = rememberCoroutineScope()
    val cola = remember { mutableStateListOf<Tarjeta>() }
    var cargando by remember { mutableStateOf(true) }
    var mostrarRespuesta by remember { mutableStateOf(false) }
    var repasadas by remember { mutableIntStateOf(0) }
    var aciertos by remember { mutableIntStateOf(0) }
    val mazo = rememberConsulta(mazoId) { mazo(mazoId) }

    LaunchedEffect(mazoId) {
        cola.addAll(withContext(Dispatchers.IO) { db.pendientes(mazoId) })
        cargando = false
    }

    fun responder(calificacion: Calificacion) {
        val tarjeta = cola.first()
        val ahora = System.currentTimeMillis()
        mostrarRespuesta = false
        repasadas++
        if (calificacion == Calificacion.DIFICIL) {
            // Las difíciles vuelven a salir al final de la sesión
            cola.add(tarjeta.copy(estado = RepeticionEspaciada.calificar(tarjeta.estado, calificacion, ahora)))
        } else {
            aciertos++
        }
        cola.removeAt(0)
        scope.launch(Dispatchers.IO) { db.registrarRepaso(tarjeta, calificacion, Origen.TELEFONO, ahora) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mazo?.nombre ?: "Estudio") },
                navigationIcon = { IconButton(onClick = cerrar) { Text("✕", style = MaterialTheme.typography.titleLarge) } },
                actions = { Text("${cola.size} restantes  ", style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val actual = cola.firstOrNull()
            when {
                cargando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                actual == null -> FinDeSesion(repasadas, aciertos, cerrar, Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    LinearProgressIndicator(
                        progress = { repasadas.toFloat() / (repasadas + cola.size) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Card(
                        Modifier.fillMaxWidth().weight(1f).clickable(enabled = !mostrarRespuesta) { mostrarRespuesta = true },
                    ) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        ) {
                            LadoTarjeta(actual.frente, actual.imagenFrente, MaterialTheme.typography.headlineSmall)
                            AnimatedVisibility(mostrarRespuesta) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    HorizontalDivider()
                                    LadoTarjeta(actual.reverso, actual.imagenReverso, MaterialTheme.typography.titleLarge)
                                }
                            }
                            if (!mostrarRespuesta) {
                                Text("Toca para ver la respuesta", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (mostrarRespuesta) {
                        val ahora = System.currentTimeMillis()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Calificacion.DIFICIL to ColorDificil,
                                Calificacion.REGULAR to ColorRegular,
                                Calificacion.FACIL to ColorFacil,
                            ).forEach { (cal, color) ->
                                Button(
                                    onClick = { responder(cal) },
                                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                                    modifier = Modifier.weight(1f).height(64.dp),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(cal.etiqueta)
                                        Text(
                                            RepeticionEspaciada.vistaPrevia(actual.estado, cal, ahora),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Button(onClick = { mostrarRespuesta = true }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                            Text("Mostrar respuesta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LadoTarjeta(texto: String, imagen: String?, estilo: androidx.compose.ui.text.TextStyle) {
    imagen?.let { ImagenLocal(it) }
    if (texto.isNotBlank()) Text(texto, style = estilo, textAlign = TextAlign.Center)
}

@Composable
private fun FinDeSesion(repasadas: Int, aciertos: Int, cerrar: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🎉", style = MaterialTheme.typography.displayMedium)
        Text("¡Sesión terminada!", style = MaterialTheme.typography.headlineSmall)
        if (repasadas > 0) {
            Text("Repasaste $repasadas tarjetas · ${aciertos * 100 / repasadas}% sin dificultad", textAlign = TextAlign.Center)
        } else {
            Text("No hay tarjetas pendientes en este mazo.", textAlign = TextAlign.Center)
        }
        Button(onClick = cerrar) { Text("Volver") }
    }
}
