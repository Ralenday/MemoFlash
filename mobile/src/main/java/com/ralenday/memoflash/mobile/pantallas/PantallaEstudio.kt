package com.ralenday.memoflash.mobile.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.RepeticionEspaciada
import com.ralenday.memoflash.core.Tarjeta
import com.ralenday.memoflash.mobile.componentes.BotonIcono
import com.ralenday.memoflash.mobile.componentes.IconoCircular
import com.ralenday.memoflash.mobile.componentes.ImagenLocal
import com.ralenday.memoflash.mobile.datos.LocalBaseDatos
import com.ralenday.memoflash.mobile.datos.plural
import com.ralenday.memoflash.mobile.datos.rememberConsulta
import com.ralenday.memoflash.mobile.tema.ColoresRespuesta
import com.ralenday.memoflash.mobile.tema.Iconos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una tarjeta en un momento de la sesión. El índice distingue una difícil que vuelve a salir. */
private data class Turno(val indice: Int, val tarjeta: Tarjeta)

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

    val progreso by animateFloatAsState(
        if (repasadas + cola.size == 0) 0f else repasadas.toFloat() / (repasadas + cola.size),
        tween(400),
        label = "progreso",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mazo?.nombre ?: "Estudio", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { BotonIcono(Iconos.Cerrar, "Cerrar", cerrar) },
                actions = {
                    if (cola.isNotEmpty()) {
                        Text(
                            "${cola.size} restantes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val actual = cola.firstOrNull()
            when {
                cargando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                actual == null -> FinDeSesion(repasadas, aciertos, cerrar, Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { progreso },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(20.dp))
                    AnimatedContent(
                        targetState = Turno(repasadas, actual),
                        contentKey = { it.indice },
                        transitionSpec = {
                            (slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(350))) togetherWith
                                (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200)))
                        },
                        label = "tarjeta",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) { turno ->
                        // La tarjeta que sale ya fue respondida: se queda mostrando su respuesta
                        val volteada = if (turno.indice == repasadas) mostrarRespuesta else true
                        TarjetaVolteable(turno.tarjeta, volteada) { mostrarRespuesta = true }
                    }
                    Spacer(Modifier.height(20.dp))
                    AnimatedContent(
                        targetState = mostrarRespuesta,
                        transitionSpec = {
                            (fadeIn(tween(250, delayMillis = 100)) + slideInVertically(tween(250, delayMillis = 100)) { it / 2 }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 4 })
                        },
                        label = "botones",
                    ) { respondiendo ->
                        if (respondiendo) {
                            BotonesRespuesta(actual) { responder(it) }
                        } else {
                            Button(
                                onClick = { mostrarRespuesta = true },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                shape = MaterialTheme.shapes.large,
                            ) { Text("Mostrar respuesta") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Tarjeta que gira sobre su eje vertical para descubrir la respuesta. */
@Composable
private fun TarjetaVolteable(tarjeta: Tarjeta, volteada: Boolean, voltear: () -> Unit) {
    val giro by animateFloatAsState(
        if (volteada) 180f else 0f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "giro",
    )
    val mostrandoReverso = giro > 90f
    Surface(
        onClick = voltear,
        enabled = !volteada,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = if (mostrandoReverso) "Respuesta" else "Pregunta" }
            .graphicsLayer {
                rotationY = giro
                cameraDistance = 14f * density
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // La cara de atrás se gira otra vez para que el texto no se vea al revés
                .graphicsLayer { rotationY = if (mostrandoReverso) 180f else 0f }
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            if (!mostrandoReverso) {
                Etiqueta("Pregunta")
                LadoTarjeta(tarjeta.frente, tarjeta.imagenFrente, MaterialTheme.typography.headlineSmall)
                Text(
                    "Toca para ver la respuesta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    tarjeta.frente,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                HorizontalDivider(Modifier.fillMaxWidth(0.3f), color = MaterialTheme.colorScheme.outlineVariant)
                Etiqueta("Respuesta")
                LadoTarjeta(tarjeta.reverso, tarjeta.imagenReverso, MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LadoTarjeta(texto: String, imagen: String?, estilo: TextStyle) {
    imagen?.let { ImagenLocal(it) }
    if (texto.isNotBlank()) Text(texto, style = estilo, textAlign = TextAlign.Center)
}

/** Tres botones tonales: el color indica la dificultad sin gritar. */
@Composable
private fun BotonesRespuesta(tarjeta: Tarjeta, responder: (Calificacion) -> Unit) {
    val ahora = System.currentTimeMillis()
    val oscuro = isSystemInDarkTheme()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Calificacion.DIFICIL to ColoresRespuesta.dificil,
            Calificacion.REGULAR to ColoresRespuesta.regular,
            Calificacion.FACIL to ColoresRespuesta.facil,
        ).forEach { (cal, color) ->
            Surface(
                onClick = { responder(cal) },
                shape = MaterialTheme.shapes.large,
                color = color.copy(alpha = if (oscuro) 0.22f else 0.12f),
                contentColor = if (oscuro) lerpBlanco(color) else color,
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(cal.etiqueta, style = MaterialTheme.typography.labelLarge)
                    Text(
                        RepeticionEspaciada.vistaPrevia(tarjeta.estado, cal, ahora),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** Aclara un color para que se lea bien sobre fondo oscuro. */
private fun lerpBlanco(color: Color) = Color(
    red = color.red + (1 - color.red) * 0.35f,
    green = color.green + (1 - color.green) * 0.35f,
    blue = color.blue + (1 - color.blue) * 0.35f,
)

@Composable
private fun FinDeSesion(repasadas: Int, aciertos: Int, cerrar: () -> Unit, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val escala by animateFloatAsState(
        if (visible) 1f else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "escala",
    )
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconoCircular(Iconos.Listo, tamano = 88, modifier = Modifier.graphicsLayer { scaleX = escala; scaleY = escala })
        Spacer(Modifier.height(8.dp))
        Text("Sesión terminada", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (repasadas > 0) "Repasaste ${plural(repasadas, "tarjeta")} · ${aciertos * 100 / repasadas}% sin dificultad"
            else "No hay tarjetas pendientes en este mazo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = cerrar, modifier = Modifier.height(48.dp)) { Text("Volver al mazo") }
    }
}
