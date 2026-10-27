package com.ralenday.memoflash.mobile.datos

/**
 * Convierte una lista de texto en pares (frente, reverso). Una tarjeta por línea, separada por
 * tabulador, punto y coma, " - " , " = " o coma (en ese orden de preferencia). Así se puede pegar
 * desde Excel, Google Sheets, Quizlet o un .txt/.csv.
 */
object Importador {
    private val separadores = listOf("\t", ";", " - ", " = ", ",")

    fun analizar(texto: String): List<Pair<String, String>> =
        texto.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { linea ->
                val separador = separadores.firstOrNull { linea.contains(it) } ?: return@mapNotNull null
                val frente = linea.substringBefore(separador).trim().quitarComillas()
                val reverso = linea.substringAfter(separador).trim().quitarComillas()
                if (frente.isEmpty() || reverso.isEmpty()) null else frente to reverso
            }
            .toList()

    private fun String.quitarComillas() = removeSurrounding("\"").trim()
}
