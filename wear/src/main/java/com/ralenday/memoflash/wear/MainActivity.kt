package com.ralenday.memoflash.wear

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.core.ResumenMazo
import com.ralenday.memoflash.core.sync.Sincronizador
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Azul = Color(0xFF7FB8E6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppReloj() }
    }
}

@Composable
fun AppReloj() {
    val nav = rememberSwipeDismissableNavController()
    MaterialTheme {
        AppScaffold {
            // Desliza a la derecha para regresar
            SwipeDismissableNavHost(navController = nav, startDestination = "mazos") {
                composable("mazos") { PantallaMazos(abrir = { nav.navigate("repaso/$it") }) }
                composable("repaso/{mazo}") { entrada ->
                    PantallaRepaso(
                        mazoId = entrada.arguments?.getString("mazo").orEmpty(),
                        terminar = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaMazos(abrir: (String) -> Unit) {
    val context = LocalContext.current
    val db = remember { BaseDatos.get(context) }
    val sincronizador = remember { Sincronizador.get(context) }
    val version by db.cambios.collectAsState()
    val sync by sincronizador.estado.collectAsState()
    val scope = rememberCoroutineScope()
    val mazos by produceState<List<ResumenMazo>?>(null, version) {
        value = withContext(Dispatchers.IO) { db.mazos() }
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { sincronizador.sincronizarAhora() } }
    val lista = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = lista) { padding ->
        TransformingLazyColumn(state = lista, contentPadding = padding) {
            item { ListHeader { Text("MemoFlash") } }
            val actuales = mazos
            if (actuales != null && actuales.isEmpty()) {
                item {
                    Text(
                        "Crea mazos en el teléfono y aparecerán aquí ⌚",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            actuales?.forEach { resumen ->
                item(key = resumen.mazo.uuid) {
                    val hay = resumen.pendientes > 0
                    Button(
                        onClick = { abrir(resumen.mazo.uuid) },
                        enabled = hay,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Azul.copy(alpha = 0.25f),
                            contentColor = Color.White,
                            secondaryContentColor = Azul,
                        ),
                        label = { Text(resumen.mazo.nombre, maxLines = 2) },
                        secondaryLabel = { Text(if (hay) "${resumen.pendientes} por repasar" else "Al día ✓") },
                    )
                }
            }
            item {
                Text(
                    when {
                        sync.sincronizando -> "Sincronizando…"
                        sync.conectado -> "📱 Teléfono conectado"
                        else -> "📴 Sin teléfono: se guarda aquí"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactButton(
                    onClick = { scope.launch(Dispatchers.IO) { sincronizador.sincronizarAhora() } },
                    label = { Text("Sincronizar") },
                )
            }
        }
    }
}

/** Vibraciones cortas para confirmar sin mirar la pantalla. */
class Vibracion(context: Context) {
    private val vibrador = context.getSystemService(Vibrator::class.java)
    fun toque() = vibrar(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    fun fin() = vibrar(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80, 80, 250), -1))
    private fun vibrar(efecto: VibrationEffect) {
        if (vibrador?.hasVibrator() == true) vibrador.vibrate(efecto)
    }
}
