package com.ralenday.memoflash.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Mazo
import com.ralenday.memoflash.core.ResumenMazo
import com.ralenday.memoflash.core.sync.Sincronizador
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMazos(abrirMazo: (String) -> Unit, irAEstadisticas: () -> Unit) {
    val db = LocalBaseDatos.current
    val scope = rememberCoroutineScope()
    val mazos = rememberConsulta { mazos() }
    var creando by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MemoFlash", fontWeight = FontWeight.Bold) }) },
        bottomBar = { BarraInferior(enMazos = true, irAMazos = {}, irAEstadisticas = irAEstadisticas) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creando = true }) { Text("＋  Nuevo mazo") }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { TarjetaSincronizacion() }
            when {
                mazos == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                mazos.isEmpty() -> item {
                    Text(
                        "Aún no tienes mazos. Crea uno para empezar a estudiar.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> items(mazos, key = { it.mazo.uuid }) { FilaMazo(it) { abrirMazo(it.mazo.uuid) } }
            }
        }
    }

    if (creando) {
        DialogoMazo(
            inicial = null,
            cerrar = { creando = false },
            guardar = { nombre, descripcion ->
                creando = false
                scope.launch(Dispatchers.IO) { db.guardarMazo(Mazo(nombre = nombre, descripcion = descripcion)) }
            },
        )
    }
}

@Composable
private fun FilaMazo(resumen: ResumenMazo, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(resumen.mazo.nombre, style = MaterialTheme.typography.titleMedium)
                if (resumen.mazo.descripcion.isNotBlank()) {
                    Text(
                        resumen.mazo.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    "${resumen.total} tarjetas" + if (resumen.mazo.enReloj) "  ·  ⌚ en el reloj" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (resumen.pendientes > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("${resumen.pendientes}", modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

/** Muestra si el reloj está conectado y permite forzar una sincronización. */
@Composable
private fun TarjetaSincronizacion() {
    val context = LocalContext.current
    val sincronizador = remember { Sincronizador.get(context) }
    val estado by sincronizador.estado.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { sincronizador.actualizarConexion() }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (estado.conectado) "⌚" else "📴", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (estado.conectado) "Reloj conectado${estado.nombreOtro?.let { ": $it" } ?: ""}"
                    else "Reloj no conectado",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    when {
                        estado.error != null && !estado.conectado -> "Tus cambios se enviarán al reconectar"
                        estado.ultimaSincronizacion > 0 -> "Última sincronización: ${formatearFecha(estado.ultimaSincronizacion)}"
                        else -> "Los cambios se envían solos al reloj"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (estado.sincronizando) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { scope.launch(Dispatchers.IO) { sincronizador.sincronizarAhora() } }) {
                    Text("Sincronizar")
                }
            }
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción (opcional)") })
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

@Composable
fun BarraInferior(enMazos: Boolean, irAMazos: () -> Unit, irAEstadisticas: () -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = enMazos,
            onClick = irAMazos,
            icon = { Text("🗂️") },
            label = { Text("Mazos") },
        )
        NavigationBarItem(
            selected = !enMazos,
            onClick = irAEstadisticas,
            icon = { Text("📊") },
            label = { Text("Estadísticas") },
        )
    }
}
