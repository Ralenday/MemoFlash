package com.ralenday.memoflash.mobile.nube

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.core.Mazo
import com.ralenday.memoflash.core.Tarjeta
import com.ralenday.memoflash.core.fusionar
import com.ralenday.memoflash.core.sync.Sincronizador
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Activa(val correo: String) : EstadoSesion
}

data class EstadoNube(
    val sincronizando: Boolean = false,
    val ultima: Long = 0,
    val error: String? = null,
)

sealed interface ResultadoAcceso {
    data object Ok : ResultadoAcceso
    data class Aviso(val texto: String) : ResultadoAcceso
    data class Error(val texto: String) : ResultadoAcceso
}

val LocalCuenta = staticCompositionLocalOf<Cuenta> { error("Falta Cuenta") }

/**
 * Sesión de Supabase y copia de los mazos en la cuenta. El reloj sigue sincronizando
 * con el teléfono; la nube solo habla con el teléfono.
 */
class Cuenta private constructor(context: Context) {
    private val app = context.applicationContext
    private val db = BaseDatos.get(app)
    private val prefs = app.getSharedPreferences("nube", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private lateinit var scope: CoroutineScope
    private val cliente by lazy { crearClienteSupabase() }

    private val _sesion = MutableStateFlow<EstadoSesion>(EstadoSesion.Cargando)
    val sesion: StateFlow<EstadoSesion> = _sesion

    private val _nube = MutableStateFlow(EstadoNube(ultima = prefs.getLong(CLAVE_ULTIMA, 0)))
    val nube: StateFlow<EstadoNube> = _nube

    @OptIn(FlowPreview::class)
    fun iniciar(scope: CoroutineScope) {
        this.scope = scope
        if (!supabaseConfigurado()) {
            _sesion.value = EstadoSesion.SinSesion
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                cliente.auth.awaitInitialization()
                val usuario = cliente.auth.currentUserOrNull()
                if (usuario == null) {
                    _sesion.value = EstadoSesion.SinSesion
                } else {
                    _sesion.value = EstadoSesion.Activa(usuario.email.orEmpty())
                    sincronizar()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _sesion.value = EstadoSesion.SinSesion
            }
        }
        scope.launch(Dispatchers.IO) {
            db.cambiosLocales.debounce(1_500).collect {
                if (_sesion.value is EstadoSesion.Activa) sincronizar()
            }
        }
    }

    suspend fun entrar(correo: String, contrasena: String): ResultadoAcceso = acceder(correo, contrasena, crear = false)

    suspend fun registrar(correo: String, contrasena: String): ResultadoAcceso = acceder(correo, contrasena, crear = true)

    fun sincronizarEnSegundoPlano() {
        if (!::scope.isInitialized) return
        scope.launch { sincronizar() }
    }

    suspend fun salir() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (supabaseConfigurado()) {
                try {
                    cliente.auth.signOut()
                } catch (_: Exception) {
                    try {
                        cliente.auth.clearSession()
                    } catch (_: Exception) {
                    }
                }
            }
            db.vaciar()
            prefs.edit().remove(CLAVE_CUENTA).apply()
            _nube.value = EstadoNube()
            _sesion.value = EstadoSesion.SinSesion
            try {
                Sincronizador.get(app).sincronizarAhora()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun acceder(correo: String, contrasena: String, crear: Boolean): ResultadoAcceso {
        if (!supabaseConfigurado()) {
            return ResultadoAcceso.Error("Esta compilación no tiene los datos del proyecto de Supabase.")
        }
        val email = correo.trim()
        return try {
            withContext(Dispatchers.IO) {
                if (crear) {
                    cliente.auth.signUpWith(Email) {
                        this.email = email
                        password = contrasena
                    }
                    // La cuenta queda confirmada en la base. Si el registro no devuelve sesión,
                    // el inicio de sesión siguiente sí entra.
                    if (cliente.auth.currentSessionOrNull() == null) {
                        cliente.auth.signInWith(Email) {
                            this.email = email
                            password = contrasena
                        }
                    }
                } else {
                    cliente.auth.signInWith(Email) {
                        this.email = email
                        password = contrasena
                    }
                }
            }
            val usuario = cliente.auth.currentUserOrNull()
            if (usuario == null) {
                ResultadoAcceso.Aviso("Te enviamos un correo para confirmar la cuenta. Ábrelo y luego entra.")
            } else {
                _sesion.value = EstadoSesion.Activa(usuario.email ?: email)
                scope.launch { sincronizar() }
                ResultadoAcceso.Ok
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAcceso.Error(mensajeAcceso(e))
        }
    }

    private suspend fun sincronizar() = withContext(Dispatchers.IO) {
        if (_sesion.value !is EstadoSesion.Activa || !supabaseConfigurado()) return@withContext
        mutex.withLock {
            if (_sesion.value !is EstadoSesion.Activa) return@withLock
            val userId = cliente.auth.currentUserOrNull()?.id ?: return@withLock
            val cuentaGuardada = prefs.getString(CLAVE_CUENTA, null)
            val cambioDeCuenta = cuentaGuardada != null && cuentaGuardada != userId
            if (cambioDeCuenta) db.vaciar()
            prefs.edit().putString(CLAVE_CUENTA, userId).apply()
            _nube.value = _nube.value.copy(sincronizando = true, error = null)
            try {
                val unido = fusionar(db.instantanea(), Nube.descargar(cliente))
                db.aplicarFusion(unido)
                asegurarEjemplo()
                Nube.subir(cliente, db.instantanea(), userId)
                val ahora = System.currentTimeMillis()
                prefs.edit().putLong(CLAVE_ULTIMA, ahora).apply()
                _nube.value = EstadoNube(sincronizando = false, ultima = ahora)
                Sincronizador.get(app).sincronizarAhora()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _nube.value = _nube.value.copy(sincronizando = false, error = e.message ?: "Sin conexión")
                if (!cambioDeCuenta) {
                    try {
                        Sincronizador.get(app).sincronizarAhora()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /** La primera cuenta vacía recibe el mazo de ejemplo para poder probar el teléfono y el reloj. */
    private fun asegurarEjemplo() {
        val prefsApp = app.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefsApp.getBoolean("ejemplo_creado", false)) return
        if (db.todosLosMazos().isNotEmpty()) {
            prefsApp.edit().putBoolean("ejemplo_creado", true).apply()
            return
        }
        val mazo = Mazo(nombre = "Inglés básico", descripcion = "Vocabulario de ejemplo")
        db.guardarMazo(mazo)
        db.insertarTarjetas(
            listOf(
                "Apple" to "Manzana", "Dog" to "Perro", "House" to "Casa",
                "Book" to "Libro", "Water" to "Agua", "Friend" to "Amigo",
                "To learn" to "Aprender", "Tomorrow" to "Mañana",
            ).map { (frente, reverso) -> Tarjeta(mazoUuid = mazo.uuid, frente = frente, reverso = reverso) },
        )
        prefsApp.edit().putBoolean("ejemplo_creado", true).apply()
    }

    companion object {
        private const val CLAVE_CUENTA = "cuenta_id"
        private const val CLAVE_ULTIMA = "ultima"

        @Volatile
        private var instancia: Cuenta? = null

        fun get(context: Context): Cuenta =
            instancia ?: synchronized(this) { instancia ?: Cuenta(context).also { instancia = it } }
    }
}

private fun mensajeAcceso(error: Exception): String {
    val texto = error.message.orEmpty()
    return when {
        "Invalid login" in texto -> "Correo o contraseña incorrectos."
        "already registered" in texto || "already been registered" in texto -> "Ese correo ya tiene cuenta. Entra con la contraseña."
        "Password" in texto || "password" in texto -> "La contraseña debe tener al menos 6 caracteres."
        "rate limit" in texto.lowercase() -> "Espera un momento e inténtalo de nuevo."
        "Unable to resolve host" in texto || "failed to connect" in texto.lowercase() || "timeout" in texto.lowercase() ->
            "Sin conexión."
        else -> "No se pudo completar. Revisa el correo y la contraseña."
    }
}
