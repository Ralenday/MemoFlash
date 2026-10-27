package com.ralenday.memoflash.mobile.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ralenday.memoflash.mobile.nube.LocalCuenta
import com.ralenday.memoflash.mobile.nube.ResultadoAcceso
import com.ralenday.memoflash.mobile.nube.supabaseConfigurado
import kotlinx.coroutines.launch

@Composable
fun PantallaAcceso() {
    val cuenta = LocalCuenta.current
    val scope = rememberCoroutineScope()
    var creando by rememberSaveable { mutableStateOf(false) }
    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by rememberSaveable { mutableStateOf("") }
    var mensaje by rememberSaveable { mutableStateOf<String?>(null) }
    var esError by rememberSaveable { mutableStateOf(false) }
    var ocupado by rememberSaveable { mutableStateOf(false) }

    fun enviar() {
        if (ocupado) return
        val email = correo.trim()
        when {
            !email.contains('@') || !email.contains('.') -> {
                esError = true
                mensaje = "Escribe un correo válido."
            }
            contrasena.length < 6 -> {
                esError = true
                mensaje = "La contraseña debe tener al menos 6 caracteres."
            }
            else -> {
                ocupado = true
                mensaje = null
                scope.launch {
                    val resultado = if (creando) cuenta.registrar(email, contrasena) else cuenta.entrar(email, contrasena)
                    ocupado = false
                    when (resultado) {
                        ResultadoAcceso.Ok -> Unit
                        is ResultadoAcceso.Aviso -> {
                            esError = false
                            mensaje = resultado.texto
                            creando = false
                        }
                        is ResultadoAcceso.Error -> {
                            esError = true
                            mensaje = resultado.texto
                        }
                    }
                }
            }
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("MemoFlash", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (creando) "Crea una cuenta para guardar tus mazos." else "Entra para ver tus mazos en este teléfono.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!supabaseConfigurado()) {
                Text(
                    "Falta conectar el proyecto de Supabase en esta compilación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            mensaje?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = ::enviar,
                enabled = !ocupado && supabaseConfigurado(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (ocupado) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
                else Text(if (creando) "Crear cuenta" else "Entrar")
            }
            TextButton(
                onClick = {
                    creando = !creando
                    mensaje = null
                },
                enabled = !ocupado,
            ) {
                Text(if (creando) "¿Ya tienes cuenta? Entrar" else "¿No tienes cuenta? Crear una")
            }
        }
    }
}
