package com.ralenday.memoflash.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.core.Calificacion
import com.ralenday.memoflash.core.Origen
import com.ralenday.memoflash.core.RepeticionEspaciada.DIA_MS
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Paleta categórica validada (claro / oscuro) para las gráficas. */
private object PaletaGraficas {
    fun serie(i: Int, oscuro: Boolean): Color = (if (oscuro) oscuras else claras)[i]
    private val claras = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100))
    private val oscuras = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500))
}

data class Barra(val etiqueta: String, val detalle: String, val valor: Int)

data class Estadisticas(
    val totalTarjetas: Int,
    val repasosHoy: Int,
    val racha: Int,
    /** % de repasos (30 días) sin "Difícil". */
    val acierto: Int?,
    /** % de tarjetas ya aprendidas (intervalo ≥ 1 día) que recordaste: la retención real. */
    val retencion: Int?,
    val porDia: List<Barra>,
    val pronostico: List<Barra>,
    val estados: List<Pair<String, Int>>,
    val desdeTelefono: Int,
    val desdeReloj: Int,
)

private val locale = Locale.forLanguageTag("es-MX")

fun calcularEstadisticas(db: BaseDatos, ahora: Long = System.currentTimeMillis()): Estadisticas {
    val zona = ZoneId.systemDefault()
    val hoy = LocalDate.now(zona)
    fun dia(ms: Long) = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
    fun nombreDia(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2)
    fun detalleDia(d: LocalDate) = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${d.dayOfMonth}"

    val tarjetas = db.todasLasTarjetas()
    val historial = db.historial()
    val hace30 = ahora - 30 * DIA_MS
    val recientes = historial.filter { it.fecha >= hace30 }
    val porFecha = historial.groupingBy { dia(it.fecha) }.eachCount()

    // Retención: de los repasos de tarjetas que ya estaban aprendidas, cuántos recordaste
    val deAprendidas = historial.groupBy { it.tarjetaUuid }.values.flatMap { repasos ->
        repasos.sortedBy { it.fecha }.zipWithNext().filter { (antes, _) -> antes.intervaloResultante >= 1.0 }.map { it.second }
    }.filter { it.fecha >= hace30 }

    var racha = 0
    var d = if (porFecha.containsKey(hoy)) hoy else hoy.minusDays(1)
    while (porFecha.containsKey(d)) { racha++; d = d.minusDays(1) }

    return Estadisticas(
        totalTarjetas = tarjetas.size,
        repasosHoy = porFecha[hoy] ?: 0,
        racha = racha,
        acierto = recientes.takeIf { it.isNotEmpty() }
            ?.let { r -> r.count { it.calificacion != Calificacion.DIFICIL } * 100 / r.size },
        retencion = deAprendidas.takeIf { it.isNotEmpty() }
            ?.let { r -> r.count { it.calificacion != Calificacion.DIFICIL } * 100 / r.size },
        porDia = (13 downTo 0).map { hoy.minusDays(it.toLong()) }.map { Barra(nombreDia(it), detalleDia(it), porFecha[it] ?: 0) },
        pronostico = (0..6).map { hoy.plusDays(it.toLong()) }.map { fecha ->
            val n = tarjetas.count { t ->
                val toca = dia(maxOf(t.estado.proximoRepaso, ahora))
                if (fecha == hoy) !toca.isAfter(hoy) else toca == fecha
            }
            Barra(if (fecha == hoy) "Hoy" else nombreDia(fecha), if (fecha == hoy) "Hoy" else detalleDia(fecha), n)
        },
        estados = listOf(
            "Nuevas" to tarjetas.count { it.estado.repeticiones == 0 && it.estado.proximoRepaso == 0L },
            "Aprendiendo" to tarjetas.count { it.estado.proximoRepaso != 0L && it.estado.intervaloDias < 1 },
            "Jóvenes" to tarjetas.count { it.estado.intervaloDias in 1.0..<21.0 },
            "Maduras" to tarjetas.count { it.estado.intervaloDias >= 21 },
        ),
        desdeTelefono = recientes.count { it.origen == Origen.TELEFONO },
        desdeReloj = recientes.count { it.origen == Origen.RELOJ },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEstadisticas(irAMazos: () -> Unit) {
    val stats = rememberConsulta { calcularEstadisticas(this) }
    val oscuro = isSystemInDarkTheme()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Estadísticas", fontWeight = FontWeight.Bold) }) },
        bottomBar = { BarraInferior(enMazos = false, irAMazos = irAMazos, irAEstadisticas = {}) },
    ) { padding ->
        if (stats == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicador("Retención", stats.retencion?.let { "$it%" } ?: "—", "tarjetas aprendidas que recordaste (30 d)", Modifier.weight(1f))
                Indicador("Acierto", stats.acierto?.let { "$it%" } ?: "—", "repasos sin “Difícil” (30 d)", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicador("Hoy", "${stats.repasosHoy}", "repasos", Modifier.weight(1f))
                Indicador("Racha", "${stats.racha} 🔥", if (stats.racha == 1) "día seguido" else "días seguidos", Modifier.weight(1f))
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
                    Indicador("📱 Teléfono", "${stats.desdeTelefono}", "repasos", Modifier.weight(1f))
                    Indicador("⌚ Reloj", "${stats.desdeReloj}", "repasos", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Indicador(titulo: String, valor: String, detalle: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Seccion(titulo: String, subtitulo: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            contenido()
        }
    }
}

/** Barras verticales de una sola serie. Al tocar una barra se muestra su valor. */
@Composable
private fun GraficaBarras(barras: List<Barra>, color: Color, unidad: String) {
    var seleccion by remember(barras) { mutableStateOf<Int?>(null) }
    val maximo = (barras.maxOfOrNull { it.valor } ?: 0).coerceAtLeast(1)
    val linea = MaterialTheme.colorScheme.outlineVariant
    val total = barras.sumOf { it.valor }

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
            val alto = size.height * barra.valor / maximo
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    if (total == 0) {
        Text("Aún no hay tarjetas.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val hueco = 2.dp.toPx()
        var x = 0f
        drawRoundRect(fondo, size = size, cornerRadius = CornerRadius(4.dp.toPx()))
        partes.forEachIndexed { i, (_, n) ->
            if (n == 0) return@forEachIndexed
            val ancho = size.width * n / total
            drawRoundRect(
                PaletaGraficas.serie(i, oscuro),
                Offset(x, 0f),
                Size((ancho - hueco).coerceAtLeast(1f), size.height),
                CornerRadius(4.dp.toPx()),
            )
            x += ancho
        }
    }
    Spacer(Modifier.height(4.dp))
    partes.forEachIndexed { i, (nombre, n) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(PaletaGraficas.serie(i, oscuro), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(nombre, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$n  (${n * 100 / total}%)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
