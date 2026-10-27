package com.ralenday.memoflash.wear

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.RepeticionEspaciada
import com.ralenday.memoflash.core.Tarjeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** En el reloj las sesiones son cortas: ideales para la fila del banco. */
private const val TARJETAS_POR_SESION = 10

private val colores = mapOf(
    Calificacion.DIFICIL to Color(0xFFE5484D),
    Calificacion.REGULAR to Color(0xFFE59A00),
    Calificacion.FACIL to Color(0xFF2E9E5B),
)

@Composable
fun PantallaRepaso(mazoId: String, terminar: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BaseDatos.get(context) }
    val vibracion = remember { Vibracion(context) }
    val scope = rememberCoroutineScope()
    val cola = remember { mutableStateListOf<Tarjeta>() }
    var cargando by remember { mutableStateOf(true) }
    var volteada by remember { mutableStateOf(false) }
    var repasadas by remember { mutableIntStateOf(0) }
    val lista = rememberTransformingLazyColumnState()

    LaunchedEffect(mazoId) {
        cola.addAll(withContext(Dispatchers.IO) { db.pendientes(mazoId, limite = TARJETAS_POR_SESION) })
        cargando = false
    }
    // Al cambiar de tarjeta o voltearla, volver arriba
    LaunchedEffect(repasadas, volteada) { lista.scrollToItem(0) }

    fun responder(cal: Calificacion) {
        val tarjeta = cola.first()
        val ahora = System.currentTimeMillis()
        if (cal == Calificacion.DIFICIL) {
            cola.add(tarjeta.copy(estado = RepeticionEspaciada.calificar(tarjeta.estado, cal, ahora)))
        }
        cola.removeAt(0)
        volteada = false
        repasadas++
        if (cola.isEmpty()) vibracion.fin() else vibracion.toque()
        // Se guarda en el SQLite del reloj; el Sincronizador lo enviará al teléfono
        scope.launch(Dispatchers.IO) { db.registrarRepaso(tarjeta, cal, Origen.RELOJ, ahora) }
    }

    val actual = cola.firstOrNull()
    ScreenScaffold(scrollState = lista) { padding ->
        TransformingLazyColumn(state = lista, contentPadding = padding) {
            when {
                cargando -> item { Text("Cargando…") }
                actual == null -> {
                    item { ListHeader { Text("¡Listo!") } }
                    item {
                        Text(
                            if (repasadas > 0) "Repasaste $repasadas tarjetas" else "Nada pendiente",
                            textAlign = TextAlign.Center,
                        )
                    }
                    item { Button(onClick = terminar, label = { Text("Volver") }) }
                }
                else -> {
                    item {
                        Text(
                            "${cola.size} restantes",
                            style = MaterialTheme.typography.labelSmall,
                            color = Azul,
                        )
                    }
                    item {
                        LadoTarjeta(
                            texto = actual.frente,
                            tieneImagen = actual.imagenFrente != null,
                            grande = true,
                            modifier = Modifier.clickable(enabled = !volteada) { volteada = true; vibracion.toque() },
                        )
                    }
                    if (!volteada) {
                        item {
                            Button(
                                onClick = { volteada = true; vibracion.toque() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Ver respuesta", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            )
                        }
                    } else {
                        item {
                            LadoTarjeta(actual.reverso, actual.imagenReverso != null, grande = false, color = Azul)
                        }
                        val ahora = System.currentTimeMillis()
                        Calificacion.entries.forEach { cal ->
                            item(key = cal.name) {
                                Button(
                                    onClick = { responder(cal) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colores.getValue(cal),
                                        contentColor = Color.White,
                                        secondaryContentColor = Color.White.copy(alpha = 0.85f),
                                    ),
                                    label = { Text(cal.etiqueta) },
                                    secondaryLabel = { Text(RepeticionEspaciada.vistaPrevia(actual.estado, cal, ahora)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LadoTarjeta(
    texto: String,
    tieneImagen: Boolean,
    grande: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (texto.isNotBlank()) {
            Text(
                texto,
                textAlign = TextAlign.Center,
                fontSize = if (grande) 18.sp else 15.sp,
                fontWeight = if (grande) FontWeight.Bold else FontWeight.Normal,
                color = color,
            )
        }
        if (tieneImagen) {
            Text(
                if (texto.isBlank()) "Imagen: ábrela en el teléfono" else "Con imagen: mírala en el teléfono",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
