package com.ralenday.memoflash.mobile.pantallas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Tarjeta
import com.ralenday.memoflash.core.nuevoId
import com.ralenday.memoflash.mobile.componentes.Bloque
import com.ralenday.memoflash.mobile.componentes.BotonIcono
import com.ralenday.memoflash.mobile.componentes.ImagenLocal
import com.ralenday.memoflash.mobile.datos.Imagenes
import com.ralenday.memoflash.mobile.datos.LocalBaseDatos
import com.ralenday.memoflash.mobile.tema.Iconos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditor(mazoId: String, tarjetaId: String?, cerrar: () -> Unit) {
    val db = LocalBaseDatos.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uuid by rememberSaveable { mutableStateOf(tarjetaId ?: nuevoId()) }
    var frente by rememberSaveable { mutableStateOf("") }
    var reverso by rememberSaveable { mutableStateOf("") }
    var imagenFrente by rememberSaveable { mutableStateOf<String?>(null) }
    var imagenReverso by rememberSaveable { mutableStateOf<String?>(null) }
    var cargado by rememberSaveable { mutableStateOf(tarjetaId == null) }
    var original by rememberSaveable { mutableStateOf<List<String?>>(emptyList()) }

    LaunchedEffect(tarjetaId) {
        if (cargado || tarjetaId == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { db.tarjeta(tarjetaId) }?.let {
            frente = it.frente; reverso = it.reverso
            imagenFrente = it.imagenFrente; imagenReverso = it.imagenReverso
            original = listOf(it.imagenFrente, it.imagenReverso)
        }
        cargado = true
    }

    val elegirFrente = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) { Imagenes.copiar(context, it)?.let { ruta -> imagenFrente = ruta } } }
    }
    val elegirReverso = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) { Imagenes.copiar(context, it)?.let { ruta -> imagenReverso = ruta } } }
    }
    val soloImagenes = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val valida = (frente.isNotBlank() || imagenFrente != null) && (reverso.isNotBlank() || imagenReverso != null)

    fun guardar(despues: () -> Unit) {
        val tarjeta = Tarjeta(
            uuid = uuid, mazoUuid = mazoId, frente = frente.trim(), reverso = reverso.trim(),
            imagenFrente = imagenFrente, imagenReverso = imagenReverso,
        )
        val imagenesQuitadas = original.filterNotNull() - setOfNotNull(imagenFrente, imagenReverso)
        scope.launch(Dispatchers.IO) {
            db.guardarTarjeta(tarjeta)
            imagenesQuitadas.forEach { Imagenes.borrar(it) }
        }
        despues()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tarjetaId == null) "Nueva tarjeta" else "Editar tarjeta") },
                navigationIcon = { BotonIcono(Iconos.Cerrar, "Cerrar", cerrar) },
                actions = {
                    if (tarjetaId != null) {
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                db.eliminarTarjeta(tarjetaId)
                                original.forEach { Imagenes.borrar(it) }
                            }
                            cerrar()
                        }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CampoLado(
                titulo = "Frente",
                detalle = "La pregunta",
                texto = frente,
                cambiar = { frente = it },
                ejemplo = "¿Qué es la mitocondria?",
                lineas = 2,
                imagen = imagenFrente,
                elegirImagen = { elegirFrente.launch(soloImagenes) },
                quitarImagen = { imagenFrente = null },
            )
            CampoLado(
                titulo = "Reverso",
                detalle = "La respuesta",
                texto = reverso,
                cambiar = { reverso = it },
                ejemplo = "El orgánulo que produce energía (ATP)",
                lineas = 3,
                imagen = imagenReverso,
                elegirImagen = { elegirReverso.launch(soloImagenes) },
                quitarImagen = { imagenReverso = null },
            )

            Text(
                "En el reloj solo se muestra el texto. Si la tarjeta solo tiene imagen, el reloj te avisará que la veas en el teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { guardar(cerrar) },
                    enabled = valida && cargado,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Guardar") }
                if (tarjetaId == null) {
                    OutlinedButton(onClick = {
                        guardar {
                            uuid = nuevoId(); frente = ""; reverso = ""; imagenFrente = null; imagenReverso = null
                        }
                    }, enabled = valida, modifier = Modifier.weight(1f).height(52.dp)) { Text("Guardar y otra") }
                }
            }
        }
    }
}

/** Un lado de la tarjeta: texto y, opcionalmente, una imagen. */
@Composable
private fun CampoLado(
    titulo: String,
    detalle: String,
    texto: String,
    cambiar: (String) -> Unit,
    ejemplo: String,
    lineas: Int,
    imagen: String?,
    elegirImagen: () -> Unit,
    quitarImagen: () -> Unit,
) {
    Bloque(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                texto, cambiar,
                Modifier.fillMaxWidth(),
                minLines = lineas,
                placeholder = { Text(ejemplo) },
                shape = MaterialTheme.shapes.medium,
            )
            AnimatedContent(
                targetState = imagen,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) using SizeTransform() },
                label = "imagen",
            ) { ruta ->
                if (ruta == null) {
                    TextButton(onClick = elegirImagen) {
                        Icon(Iconos.Imagen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar imagen")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ImagenLocal(ruta)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = elegirImagen) { Text("Cambiar imagen") }
                            TextButton(onClick = quitarImagen) { Text("Quitar") }
                        }
                    }
                }
            }
        }
    }
}
