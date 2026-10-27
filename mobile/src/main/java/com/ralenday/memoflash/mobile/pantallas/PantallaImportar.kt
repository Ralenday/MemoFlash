package com.ralenday.memoflash.mobile.pantallas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.core.Tarjeta
import com.ralenday.memoflash.mobile.componentes.Bloque
import com.ralenday.memoflash.mobile.componentes.BotonIcono
import com.ralenday.memoflash.mobile.datos.Importador
import com.ralenday.memoflash.mobile.datos.LocalBaseDatos
import com.ralenday.memoflash.mobile.datos.plural
import com.ralenday.memoflash.mobile.tema.Iconos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaImportar(mazoId: String, cerrar: () -> Unit) {
    val db = LocalBaseDatos.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var texto by rememberSaveable { mutableStateOf("") }
    val pares = remember(texto) { Importador.analizar(texto) }

    val abrirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            texto = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar lista") },
                navigationIcon = { BotonIcono(Iconos.Cerrar, "Cerrar", cerrar) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Una tarjeta por línea: frente y reverso separados por tabulador, punto y coma, guion o coma. " +
                    "Puedes copiar dos columnas de Excel o Google Sheets y pegarlas aquí.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("H2O; Agua\nNaCl; Sal de mesa\nCO2; Dióxido de carbono") },
            )
            OutlinedButton(onClick = { abrirArchivo.launch(arrayOf("text/*")) }) {
                Icon(Iconos.Archivo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abrir archivo .txt / .csv")
            }

            AnimatedVisibility(pares.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Bloque(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Vista previa", style = MaterialTheme.typography.titleSmall)
                        pares.take(5).forEach { (frente, reverso) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(frente, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    reverso,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (pares.size > 5) {
                            Text(
                                "y ${pares.size - 5} más",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val tarjetas = pares.map { (f, r) -> Tarjeta(mazoUuid = mazoId, frente = f, reverso = r) }
                    scope.launch(Dispatchers.IO) { db.insertarTarjetas(tarjetas) }
                    cerrar()
                },
                enabled = pares.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (pares.isEmpty()) "Importar" else "Importar ${plural(pares.size, "tarjeta")}") }
        }
    }
}
