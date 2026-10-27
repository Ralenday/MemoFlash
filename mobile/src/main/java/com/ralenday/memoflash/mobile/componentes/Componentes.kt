package com.ralenday.memoflash.mobile.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Mazo
import com.ralenday.memoflash.mobile.datos.Imagenes
import com.ralenday.memoflash.mobile.tema.Iconos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icono, contentDescription = descripcion) }
}

@Composable
fun BarraInferior(enMazos: Boolean, irAMazos: () -> Unit, irAEstadisticas: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        NavigationBarItem(
            selected = enMazos,
            onClick = irAMazos,
            icon = { Icon(Iconos.Mazos, contentDescription = null) },
            label = { Text("Mazos") },
        )
        NavigationBarItem(
            selected = !enMazos,
            onClick = irAEstadisticas,
            icon = { Icon(Iconos.Graficas, contentDescription = null) },
            label = { Text("Estadísticas") },
        )
    }
}

/** Contenedor plano y redondeado: la base visual de casi todas las pantallas. */
@Composable
fun Bloque(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    contenido: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = color, content = contenido)
    } else {
        Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = color, content = contenido)
    }
}

/** Etiqueta pequeña con fondo suave, para conteos y estados. */
@Composable
fun Pastilla(
    texto: String,
    modifier: Modifier = Modifier,
    fondo: Color = MaterialTheme.colorScheme.primaryContainer,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icono: ImageVector? = null,
) {
    Surface(modifier = modifier, shape = CircleShape, color = fondo, contentColor = color) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icono?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(texto, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Ícono dentro de un círculo de color suave. */
@Composable
fun IconoCircular(
    icono: ImageVector,
    modifier: Modifier = Modifier,
    fondo: Color = MaterialTheme.colorScheme.primaryContainer,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    tamano: Int = 40,
) {
    Surface(modifier = modifier.size(tamano.dp), shape = CircleShape, color = fondo, contentColor = color) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = null, modifier = Modifier.size((tamano * 0.5f).dp))
        }
    }
}

/** Mensaje centrado para listas vacías. */
@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, detalle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconoCircular(
            icono,
            fondo = MaterialTheme.colorScheme.surfaceContainerHigh,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            tamano = 64,
        )
        Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Imagen de una tarjeta. Aparece con un desvanecido suave cuando termina de cargar. */
@Composable
fun ImagenLocal(ruta: String, modifier: Modifier = Modifier) {
    val imagen by produceState<ImageBitmap?>(null, ruta) {
        value = withContext(Dispatchers.IO) { Imagenes.cargar(ruta) }
    }
    val alfa by animateFloatAsState(if (imagen != null) 1f else 0f, tween(300), label = "imagen")
    imagen?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(16.dp)).alpha(alfa),
        )
    }
}

@Composable
fun DialogoMazo(inicial: Mazo?, cerrar: () -> Unit, guardar: (String, String) -> Unit) {
    var nombre by rememberSaveable { mutableStateOf(inicial?.nombre ?: "") }
    var descripcion by rememberSaveable { mutableStateOf(inicial?.descripcion ?: "") }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(if (inicial == null) "Nuevo mazo" else "Editar mazo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    nombre, { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    descripcion, { descripcion = it },
                    label = { Text("Descripción (opcional)") },
                    shape = MaterialTheme.shapes.medium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { guardar(nombre.trim(), descripcion.trim()) }, enabled = nombre.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
