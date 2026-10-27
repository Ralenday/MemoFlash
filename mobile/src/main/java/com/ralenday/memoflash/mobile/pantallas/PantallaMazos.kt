package com.ralenday.memoflash.mobile.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Mazo
import com.ralenday.memoflash.core.ResumenMazo
import com.ralenday.memoflash.core.sync.Sincronizador
import com.ralenday.memoflash.mobile.componentes.BarraInferior
import com.ralenday.memoflash.mobile.componentes.Bloque
import com.ralenday.memoflash.mobile.componentes.DialogoMazo
import com.ralenday.memoflash.mobile.componentes.EstadoVacio
import com.ralenday.memoflash.mobile.componentes.IconoCircular
import com.ralenday.memoflash.mobile.componentes.Pastilla
import com.ralenday.memoflash.mobile.datos.LocalBaseDatos
import com.ralenday.memoflash.mobile.datos.formatearFecha
import com.ralenday.memoflash.mobile.datos.plural
import com.ralenday.memoflash.mobile.datos.rememberConsulta
import com.ralenday.memoflash.mobile.nube.EstadoSesion
import com.ralenday.memoflash.mobile.nube.LocalCuenta
import com.ralenday.memoflash.mobile.tema.Iconos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMazos(abrirMazo: (String) -> Unit, irAEstadisticas: () -> Unit) {
    val db = LocalBaseDatos.current
    val cuenta = LocalCuenta.current
    val sesion by cuenta.sesion.collectAsState()
    val nube by cuenta.nube.collectAsState()
    val scope = rememberCoroutineScope()
    val mazos = rememberConsulta { mazos() }
    var creando by rememberSaveable { mutableStateOf(false) }
    var cerrarSesion by rememberSaveable { mutableStateOf(false) }
    val lista = rememberLazyListState()
    val barra = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // El botón flotante se encoge a solo ícono al bajar por la lista
    val botonExtendido by remember { derivedStateOf { lista.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(barra.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("MemoFlash") },
                scrollBehavior = barra,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        bottomBar = { BarraInferior(enMazos = true, irAMazos = {}, irAEstadisticas = irAEstadisticas) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creando = true },
                expanded = botonExtendido,
                icon = { Icon(Iconos.Agregar, contentDescription = null) },
                // Al encogerse solo queda el ícono; así un lector de pantalla sigue sabiendo qué hace
                modifier = Modifier.semantics { contentDescription = "Nuevo mazo" },
                text = { Text("Nuevo mazo") },
            )
        },
    ) { padding ->
        LazyColumn(
            state = lista,
            contentPadding = PaddingValues(20.dp, padding.calculateTopPadding(), 20.dp, padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "resumen") { ResumenDelDia(mazos, Modifier.animateItem()) }
            item(key = "sync") { TarjetaSincronizacion(Modifier.animateItem()) }
            if (sesion is EstadoSesion.Activa) {
                item(key = "cuenta") {
                    TarjetaCuenta(
                        correo = (sesion as EstadoSesion.Activa).correo,
                        sincronizando = nube.sincronizando,
                        ultima = nube.ultima,
                        error = nube.error,
                        sincronizar = cuenta::sincronizarEnSegundoPlano,
                        salir = { cerrarSesion = true },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "titulo") {
                Text(
                    "Mazos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp).animateItem(),
                )
            }
            when {
                mazos == null -> item(key = "cargando") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                mazos.isEmpty() -> item(key = "vacio") {
                    EstadoVacio(
                        Iconos.Mazos,
                        "Aún no tienes mazos",
                        "Crea uno con el botón de abajo para empezar a estudiar.",
                        Modifier.animateItem(),
                    )
                }
                else -> items(mazos, key = { it.mazo.uuid }) {
                    FilaMazo(it, Modifier.animateItem()) { abrirMazo(it.mazo.uuid) }
                }
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
    if (cerrarSesion) {
        AlertDialog(
            onDismissRequest = { cerrarSesion = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Tus mazos siguen guardados en tu cuenta. En este teléfono se quitan hasta que vuelvas a entrar.") },
            confirmButton = {
                TextButton(onClick = {
                    cerrarSesion = false
                    scope.launch { cuenta.salir() }
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { cerrarSesion = false }) { Text("Cancelar") } },
        )
    }
}

/** Encabezado con lo que toca repasar hoy entre todos los mazos. */
@Composable
private fun ResumenDelDia(mazos: List<ResumenMazo>?, modifier: Modifier = Modifier) {
    val pendientes = mazos?.sumOf { it.pendientes } ?: 0
    Bloque(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Para hoy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            AnimatedContent(
                targetState = pendientes,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "pendientes",
            ) { n ->
                Text(
                    if (n == 0) "Todo al día" else plural(n, "tarjeta") + " por repasar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                if (pendientes == 0) "Vuelve más tarde o agrega tarjetas nuevas."
                else "Repasar un poco cada día es lo que más ayuda a recordar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun FilaMazo(resumen: ResumenMazo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Bloque(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    resumen.mazo.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (resumen.mazo.descripcion.isNotBlank()) {
                    Text(
                        resumen.mazo.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        plural(resumen.total, "tarjeta"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (resumen.mazo.enReloj) {
                        Icon(
                            Iconos.Reloj,
                            contentDescription = "Disponible en el reloj",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (resumen.pendientes > 0) {
                Pastilla("${resumen.pendientes} pendientes")
            } else {
                Pastilla(
                    "Al día",
                    fondo = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    icono = Iconos.Listo,
                )
            }
        }
    }
}

/** Muestra la cuenta y si la última copia llegó a Supabase. */
@Composable
private fun TarjetaCuenta(
    correo: String,
    sincronizando: Boolean,
    ultima: Long,
    error: String?,
    sincronizar: () -> Unit,
    salir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Bloque(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    correo.ifBlank { "Cuenta" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        sincronizando -> "Guardando en tu cuenta…"
                        error != null -> "Sin conexión. Se reintentará solo"
                        ultima > 0 -> "Guardado: ${formatearFecha(ultima)}"
                        else -> "Se guarda en tu cuenta"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = sincronizar, enabled = !sincronizando) {
                if (sincronizando) IconoGirando() else Icon(Iconos.Sincronizar, contentDescription = "Guardar en la cuenta")
            }
            IconButton(onClick = salir) {
                Icon(Iconos.Salir, contentDescription = "Cerrar sesión")
            }
        }
    }
}

/** Muestra si el reloj está conectado y permite forzar una sincronización. */
@Composable
private fun TarjetaSincronizacion(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sincronizador = remember { Sincronizador.get(context) }
    val estado by sincronizador.estado.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { sincronizador.actualizarConexion() }

    val fondoIcono by animateColorAsState(
        if (estado.conectado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(400),
        label = "fondoIcono",
    )

    Bloque(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoCircular(
                Iconos.Reloj,
                fondo = fondoIcono,
                color = if (estado.conectado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (estado.conectado) "Reloj conectado${estado.nombreOtro?.let { ": $it" } ?: ""}"
                    else "Reloj no conectado",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        estado.sincronizando -> "Sincronizando…"
                        estado.error != null && !estado.conectado -> "Tus cambios se enviarán al reconectar"
                        estado.ultimaSincronizacion > 0 -> "Última vez: ${formatearFecha(estado.ultimaSincronizacion)}"
                        else -> "Los cambios se envían solos al reloj"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { scope.launch(Dispatchers.IO) { sincronizador.sincronizarAhora() } },
                enabled = !estado.sincronizando,
            ) {
                if (estado.sincronizando) IconoGirando() else Icon(Iconos.Sincronizar, contentDescription = "Sincronizar")
            }
        }
    }
}

/** Ícono de sincronizar dando vueltas; solo existe mientras se sincroniza. */
@Composable
private fun IconoGirando() {
    val giro = rememberInfiniteTransition(label = "giro")
    val angulo by giro.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angulo",
    )
    Icon(Iconos.Sincronizar, contentDescription = "Sincronizando", modifier = Modifier.rotate(angulo))
}
