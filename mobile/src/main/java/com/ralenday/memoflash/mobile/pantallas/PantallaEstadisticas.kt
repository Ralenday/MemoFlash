package com.ralenday.memoflash.mobile.pantallas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.mobile.componentes.BarraInferior
import com.ralenday.memoflash.mobile.componentes.Bloque
import com.ralenday.memoflash.mobile.componentes.IconoCircular
import com.ralenday.memoflash.mobile.datos.Barra
import com.ralenday.memoflash.mobile.datos.calcularEstadisticas
import com.ralenday.memoflash.mobile.datos.rememberConsulta
import com.ralenday.memoflash.mobile.tema.Iconos

/** Paleta categórica validada (claro / oscuro) para las gráficas. */
private object PaletaGraficas {
    fun serie(i: Int, oscuro: Boolean): Color = (if (oscuro) oscuras else claras)[i]
    private val claras = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100))
    private val oscuras = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEstadisticas(irAMazos: () -> Unit) {
    val stats = rememberConsulta { calcularEstadisticas(this) }
    val oscuro = isSystemInDarkTheme()
    val barra = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(barra.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Estadísticas") },
                scrollBehavior = barra,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        bottomBar = { BarraInferior(enMazos = false, irAMazos = irAMazos, irAEstadisticas = {}) },
    ) { padding ->
        if (stats == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicador("Retención", stats.retencion?.let { "$it%" } ?: "—", "tarjetas aprendidas que recordaste (30 d)", Modifier.weight(1f))
                Indicador("Acierto", stats.acierto?.let { "$it%" } ?: "—", "repasos sin “Difícil” (30 d)", Modifier.weight(1f))
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicador("Hoy", "${stats.repasosHoy}", "repasos", Modifier.weight(1f))
                Indicador("Racha", "${stats.racha}", if (stats.racha == 1) "día seguido" else "días seguidos", Modifier.weight(1f))
                Indicador("Tarjetas", "${stats.totalTarjetas}", "en total", Modifier.weight(1f))
            }

            Seccion("Repasos por día", "Últimas 2 semanas · toca una barra") {
                GraficaBarras(stats.porDia, PaletaGraficas.serie(0, oscuro), unidad = "repasos")
            }
            Seccion("Próximos repasos", "Tarjetas que tocan cada día") {
                GraficaBarras(stats.pronostico, PaletaGraficas.serie(0, oscuro), unidad = "tarjetas")
            }
            Seccion("Estado de tus tarjetas", "Maduras = intervalo de 21 días o más") {
                BarraApilada(stats.estados, oscuro)
            }
            Seccion("Dónde estudias", "Repasos de los últimos 30 días") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dispositivo(Iconos.Telefono, "Teléfono", stats.desdeTelefono, Modifier.weight(1f))
                    Dispositivo(Iconos.Reloj, "Reloj", stats.desdeReloj, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Indicador(titulo: String, valor: String, detalle: String, modifier: Modifier = Modifier) {
    Bloque(modifier.fillMaxHeight()) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = valor,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "valor",
            ) { Text(it, style = MaterialTheme.typography.headlineMedium) }
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Dispositivo(icono: ImageVector, nombre: String, repasos: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconoCircular(icono, tamano = 36)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("$repasos", style = MaterialTheme.typography.titleLarge)
            Text("$nombre · repasos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Seccion(titulo: String, subtitulo: String, contenido: @Composable () -> Unit) {
    Bloque(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            contenido()
        }
    }
}

/** Crece de 0 a 1 la primera vez que se muestra, para que las gráficas "suban". */
@Composable
private fun rememberAparicion(clave: Any?): Float {
    val avance = remember(clave) { Animatable(0f) }
    LaunchedEffect(clave) { avance.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return avance.value
}

/** Barras verticales de una sola serie. Al tocar una barra se muestra su valor. */
@Composable
private fun GraficaBarras(barras: List<Barra>, color: Color, unidad: String) {
    var seleccion by remember(barras) { mutableStateOf<Int?>(null) }
    val maximo = (barras.maxOfOrNull { it.valor } ?: 0).coerceAtLeast(1)
    val linea = MaterialTheme.colorScheme.outlineVariant
    val total = barras.sumOf { it.valor }
    val avance = rememberAparicion(barras)

    Text(
        seleccion?.let { "${barras[it].detalle}: ${barras[it].valor} $unidad" } ?: "Total: $total $unidad · máx. $maximo",
        style = MaterialTheme.typography.labelLarge,
    )
    Canvas(
        Modifier.fillMaxWidth().height(140.dp).pointerInput(barras) {
            detectTapGestures { pos ->
                val i = (pos.x / (size.width / barras.size)).toInt().coerceIn(0, barras.lastIndex)
                seleccion = if (seleccion == i) null else i
            }
        },
    ) {
        val ancho = size.width / barras.size
        val grosor = (ancho * 0.6f).coerceAtMost(28.dp.toPx())
        drawLine(linea, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        barras.forEachIndexed { i, barra ->
            if (barra.valor == 0) return@forEachIndexed
            val alto = size.height * barra.valor / maximo * avance
            val alfa = if (seleccion == null || seleccion == i) 1f else 0.35f
            barraRedondeada(color.copy(alpha = alfa), Offset(i * ancho + (ancho - grosor) / 2, size.height - alto), Size(grosor, alto))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        barras.forEach {
            Text(
                it.etiqueta,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Solo las esquinas superiores redondeadas (4 dp); la base queda recta sobre el eje. */
private fun DrawScope.barraRedondeada(color: Color, origen: Offset, tamano: Size) {
    val radio = minOf(4.dp.toPx(), tamano.height, tamano.width / 2)
    drawRoundRect(color, origen, tamano, CornerRadius(radio))
    if (tamano.height > radio) {
        drawRect(color, Offset(origen.x, origen.y + tamano.height - radio), Size(tamano.width, radio))
    }
}

/** Una barra horizontal partida por estado, con leyenda y conteos. */
@Composable
private fun BarraApilada(partes: List<Pair<String, Int>>, oscuro: Boolean) {
    val total = partes.sumOf { it.second }
    val fondo = MaterialTheme.colorScheme.surfaceContainerHighest
    val avance = rememberAparicion(partes)
    if (total == 0) {
        Text("Aún no hay tarjetas.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val hueco = 2.dp.toPx()
        var x = 0f
        drawRoundRect(fondo, size = size, cornerRadius = CornerRadius(6.dp.toPx()))
        partes.forEachIndexed { i, (_, n) ->
            if (n == 0) return@forEachIndexed
            val ancho = size.width * n / total * avance
            drawRoundRect(
                PaletaGraficas.serie(i, oscuro),
                Offset(x, 0f),
                Size((ancho - hueco).coerceAtLeast(1f), size.height),
                CornerRadius(6.dp.toPx()),
            )
            x += ancho
        }
    }
    Spacer(Modifier.height(6.dp))
    partes.forEachIndexed { i, (nombre, n) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Box(Modifier.size(10.dp).background(PaletaGraficas.serie(i, oscuro), RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Text(nombre, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                "$n  (${n * 100 / total}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
