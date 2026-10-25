package com.ralenday.memoflash.mobile

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.BaseDatos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalBaseDatos = staticCompositionLocalOf<BaseDatos> { error("Falta BaseDatos") }

/** Lee de SQLite en segundo plano y vuelve a leer cada vez que la base cambia. */
@Composable
fun <T> rememberConsulta(vararg claves: Any?, leer: BaseDatos.() -> T): T? {
    val db = LocalBaseDatos.current
    val version by db.cambios.collectAsState()
    val resultado by produceState<T?>(null, version, *claves) {
        value = withContext(Dispatchers.IO) { db.leer() }
    }
    return resultado
}

object Imagenes {
    /** Copia la imagen elegida a la carpeta privada de la app y devuelve la ruta. */
    fun copiar(context: Context, uri: Uri): String? = try {
        val carpeta = File(context.filesDir, "imagenes").apply { mkdirs() }
        val destino = File(carpeta, "${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { entrada ->
            destino.outputStream().use { entrada.copyTo(it) }
        }
        destino.absolutePath
    } catch (e: Exception) {
        null
    }

    fun borrar(ruta: String?) {
        ruta?.let { File(it).delete() }
    }

    /** Carga la imagen reducida para no gastar memoria con fotos grandes. */
    fun cargar(ruta: String, anchoMaximo: Int = 1080): ImageBitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, limites)
        var escala = 1
        while (limites.outWidth / (escala * 2) >= anchoMaximo) escala *= 2
        return BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = escala })
            ?.asImageBitmap()
    }
}

@Composable
fun ImagenLocal(ruta: String, modifier: Modifier = Modifier) {
    val imagen by produceState<ImageBitmap?>(null, ruta) {
        value = withContext(Dispatchers.IO) { Imagenes.cargar(ruta) }
    }
    imagen?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

fun formatearFecha(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("es-MX")).format(Date(ms))

/** "Hoy", "Mañana" o "en 5 d" según cuándo toca repasar. */
fun cuandoToca(proximo: Long, ahora: Long = System.currentTimeMillis()): String {
    if (proximo <= ahora) return "Pendiente"
    val horas = (proximo - ahora) / 3_600_000.0
    return when {
        horas < 1 -> "en ${(horas * 60).toInt().coerceAtLeast(1)} min"
        horas < 24 -> "en ${horas.toInt()} h"
        else -> "en ${(horas / 24).toInt()} d"
    }
}
