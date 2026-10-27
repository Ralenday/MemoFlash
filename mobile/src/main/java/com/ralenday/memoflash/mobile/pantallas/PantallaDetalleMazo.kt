package com.ralenday.memoflash.mobile.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.ralenday.memoflash.core.Tarjeta
import com.ralenday.memoflash.mobile.componentes.Bloque
import com.ralenday.memoflash.mobile.componentes.BotonIcono
import com.ralenday.memoflash.mobile.componentes.DialogoMazo
import com.ralenday.memoflash.mobile.componentes.EstadoVacio
import com.ralenday.memoflash.mobile.componentes.IconoCircular
import com.ralenday.memoflash.mobile.datos.Imagenes
import com.ralenday.memoflash.mobile.datos.LocalBaseDatos
import com.ralenday.memoflash.mobile.datos.cuandoToca
import com.ralenday.memoflash.mobile.datos.plural
import com.ralenday.memoflash.mobile.datos.rememberConsulta
import com.ralenday.memoflash.mobile.tema.Iconos
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
    val tarjetas = rememberConsulta(mazoId) { tarjetas(mazoId) }
    val ahora = System.currentTimeMillis()
    val pendientes = tarjetas?.count { it.estado.proximoRepaso <= ahora } ?: 0
    var menu by rememberSaveable { mutableStateOf(false) }
    var editandoMazo by rememberSaveable { mutableStateOf(false) }
    var confirmarBorrar by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BotonIcono(Iconos.Atras, "Atrás", atras) },
                actions = {
                    BotonIcono(Iconos.Mas, "Más opciones") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar mazo") }, onClick = { menu = false; editandoMazo = true })
                        DropdownMenuItem(text = { Text("Eliminar mazo") }, onClick = { menu = false; confirmarBorrar = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editar(null) }) {
                Icon(Iconos.Agregar, contentDescription = "Nueva tarjeta")
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(20.dp, padding.calculateTopPadding(), 20.dp, padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "encabezado") {
                Column(Modifier.padding(start = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mazo?.nombre ?: "", style = MaterialTheme.typography.headlineMedium)
                    if (!mazo?.descripcion.isNullOrBlank()) {
                        Text(
                            mazo?.descripcion ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "acciones") {
                Bloque(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Cifra("Pendientes", "$pendientes")
                            Cifra("En total", "${tarjetas?.size ?: 0}")
                        }
                        Button(
                            onClick = estudiar,
                            enabled = pendientes > 0,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (pendientes > 0) {
                                Icon(Iconos.Jugar, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Estudiar")
                            } else {
                                Icon(Iconos.Listo, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Todo al día")
                            }
                        }
                        FilledTonalButton(onClick = importar, modifier = Modifier.fillMaxWidth()) {
                            Icon(Iconos.Archivo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Importar lista")
                        }
                    }
                }
            }
            item(key = "reloj") {
                Bloque(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconoCircular(Iconos.Reloj)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Disponible en el reloj", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Solo se envía el texto, para que sea ligero",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = mazo?.enReloj == true,
                            onCheckedChange = { activo ->
                                mazo?.let {
                                    scope.launch(Dispatchers.IO) {
                                        db.guardarMazo(it.copy(enReloj = activo, actualizado = System.currentTimeMillis()))
                                    }
                                }
                            },
                        )
                    }
                }
            }
            item(key = "titulo") {
                Text(
                    "Tarjetas",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, bottom = 4.dp),
                )
            }
            if (tarjetas?.isEmpty() == true) {
                item(key = "vacio") {
                    EstadoVacio(
                        Iconos.Mazos,
                        "Este mazo está vacío",
                        "Agrega tarjetas con el botón + o importa una lista.",
                        Modifier.animateItem(),
                    )
                }
            }
            items(tarjetas ?: emptyList(), key = { it.uuid }) { tarjeta ->
                FilaTarjeta(tarjeta, ahora, Modifier.animateItem()) { editar(tarjeta.uuid) }
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
            text = { Text("Se borrarán sus ${plural(tarjetas?.size ?: 0, "tarjeta")} y su historial, también del reloj.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrar = false
                    val borrar = tarjetas.orEmpty()
                    scope.launch(Dispatchers.IO) {
                        borrar.forEach { Imagenes.borrar(it.imagenFrente); Imagenes.borrar(it.imagenReverso) }
                        db.eliminarMazo(mazoId)
                    }
                    atras()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Cifra(titulo: String, valor: String) {
    Column {
        Text(valor, style = MaterialTheme.typography.headlineMedium)
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilaTarjeta(tarjeta: Tarjeta, ahora: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pendiente = tarjeta.estado.proximoRepaso <= ahora
    Bloque(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextoConImagen(tarjeta.frente, tarjeta.imagenFrente != null, principal = true)
                TextoConImagen(tarjeta.reverso, tarjeta.imagenReverso != null, principal = false)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                cuandoToca(tarjeta.estado.proximoRepaso, ahora),
                style = MaterialTheme.typography.labelMedium,
                color = if (pendiente) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextoConImagen(texto: String, conImagen: Boolean, principal: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (conImagen) {
            Icon(
                Iconos.Imagen,
                contentDescription = "Con imagen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            texto.ifBlank { if (conImagen) "Imagen" else "" },
            style = if (principal) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (principal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (principal) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
