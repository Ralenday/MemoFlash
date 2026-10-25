package com.ralenday.memoflash.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleMazo(
    mazoId: String,
    atras: () -> Unit,
    estudiar: () -> Unit,
    editar: (String?) -> Unit,
    importar: () -> Unit,
) {
    val db = LocalBaseDatos.current
    val scope = rememberCoroutineScope()
    val mazo = rememberConsulta(mazoId) { mazo(mazoId) }
    val tarjetas = rememberConsulta(mazoId) { tarjetas(mazoId) } ?: emptyList()
    val ahora = System.currentTimeMillis()
    val pendientes = tarjetas.count { it.estado.proximoRepaso <= ahora }
    var menu by rememberSaveable { mutableStateOf(false) }
    var editandoMazo by rememberSaveable { mutableStateOf(false) }
    var confirmarBorrar by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mazo?.nombre ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = atras) { Text("←", style = MaterialTheme.typography.titleLarge) } },
                actions = {
                    IconButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar mazo") }, onClick = { menu = false; editandoMazo = true })
                        DropdownMenuItem(text = { Text("Eliminar mazo") }, onClick = { menu = false; confirmarBorrar = true })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editar(null) }) { Text("＋  Tarjeta") }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, padding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Disponible en el reloj", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Solo se envía el texto, para que sea un mazo ligero",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = mazo?.enReloj == true,
                                onCheckedChange = { activo ->
                                    mazo?.let { scope.launch(Dispatchers.IO) { db.guardarMazo(it.copy(enReloj = activo, actualizado = System.currentTimeMillis())) } }
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = estudiar, enabled = pendientes > 0, modifier = Modifier.weight(1f)) {
                                Text(if (pendientes > 0) "Estudiar ($pendientes)" else "Todo al día ✓")
                            }
                            OutlinedButton(onClick = importar) { Text("Importar lista") }
                        }
                    }
                }
            }
            item {
                Text(
                    "${tarjetas.size} tarjetas",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(tarjetas, key = { it.uuid }) { tarjeta ->
                ListItem(
                    headlineContent = {
                        Text((if (tarjeta.imagenFrente != null) "🖼️ " else "") + tarjeta.frente, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text((if (tarjeta.imagenReverso != null) "🖼️ " else "") + tarjeta.reverso, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = { Text(cuandoToca(tarjeta.estado.proximoRepaso, ahora), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.clickable { editar(tarjeta.uuid) },
                )
                HorizontalDivider()
            }
        }
    }

    if (editandoMazo && mazo != null) {
        DialogoMazo(
            inicial = mazo,
            cerrar = { editandoMazo = false },
            guardar = { nombre, descripcion ->
                editandoMazo = false
                scope.launch(Dispatchers.IO) {
                    db.guardarMazo(mazo.copy(nombre = nombre, descripcion = descripcion, actualizado = System.currentTimeMillis()))
                }
            },
        )
    }

    if (confirmarBorrar) {
        AlertDialog(
            onDismissRequest = { confirmarBorrar = false },
            title = { Text("¿Eliminar mazo?") },
            text = { Text("Se borrarán sus ${tarjetas.size} tarjetas y su historial, también del reloj.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrar = false
                    scope.launch(Dispatchers.IO) {
                        tarjetas.forEach { Imagenes.borrar(it.imagenFrente); Imagenes.borrar(it.imagenReverso) }
                        db.eliminarMazo(mazoId)
                    }
                    atras()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar") } },
        )
    }
}
