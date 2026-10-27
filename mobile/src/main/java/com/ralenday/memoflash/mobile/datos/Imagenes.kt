package com.ralenday.memoflash.mobile.datos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

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
