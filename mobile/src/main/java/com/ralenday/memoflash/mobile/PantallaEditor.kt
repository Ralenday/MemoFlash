package com.ralenday.memoflash.mobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Tarjeta
import com.ralenday.memoflash.core.nuevoId
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
                navigationIcon = { IconButton(onClick = cerrar) { Text("✕", style = MaterialTheme.typography.titleLarge) } },
                actions = {
                    if (tarjetaId != null) {
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                db.eliminarTarjeta(tarjetaId)
                                original.forEach { Imagenes.borrar(it) }
                            }
                            cerrar()
                        }) { Text("Eliminar") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Frente (pregunta)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(frente, { frente = it }, Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("¿Qué es la mitocondria?") })
            SelectorImagen(imagenFrente, elegir = { elegirFrente.launch(soloImagenes) }, quitar = { imagenFrente = null })

            Text("Reverso (respuesta)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(reverso, { reverso = it }, Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("El orgánulo que produce energía (ATP)") })
            SelectorImagen(imagenReverso, elegir = { elegirReverso.launch(soloImagenes) }, quitar = { imagenReverso = null })

            Text(
                "En el reloj solo se muestra el texto. Si la tarjeta solo tiene imagen, el reloj te avisará que la veas en el teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { guardar(cerrar) }, enabled = valida && cargado, modifier = Modifier.weight(1f)) { Text("Guardar") }
                if (tarjetaId == null) {
                    OutlinedButton(onClick = {
                        guardar {
                            uuid = nuevoId(); frente = ""; reverso = ""; imagenFrente = null; imagenReverso = null
                        }
                    }, enabled = valida, modifier = Modifier.weight(1f)) { Text("Guardar y otra") }
                }
            }
        }
    }
}

@Composable
private fun SelectorImagen(ruta: String?, elegir: () -> Unit, quitar: () -> Unit) {
    if (ruta == null) {
        OutlinedButton(onClick = elegir) { Text("🖼️  Agregar imagen") }
    } else {
        ImagenLocal(ruta)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = elegir) { Text("Cambiar imagen") }
            TextButton(onClick = quitar) { Text("Quitar") }
        }
    }
}
