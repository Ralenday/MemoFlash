package com.ralenday.memoflash.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.mobile.datos.LocalBaseDatos
import com.ralenday.memoflash.mobile.navegacion.AppMemoFlash
import com.ralenday.memoflash.mobile.navegacion.NavegacionPrincipal
import com.ralenday.memoflash.mobile.nube.Cuenta
import com.ralenday.memoflash.mobile.nube.LocalCuenta
import com.ralenday.memoflash.mobile.tema.TemaMemoFlash

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val db = BaseDatos.get(this)
        val cuenta = Cuenta.get(this)
        val omitirCuenta = BuildConfig.DEBUG && AccesoDepuracion.omitirCuenta
        setContent {
            TemaMemoFlash {
                CompositionLocalProvider(LocalBaseDatos provides db, LocalCuenta provides cuenta) {
                    if (omitirCuenta) NavegacionPrincipal() else AppMemoFlash()
                }
            }
        }
    }
}
