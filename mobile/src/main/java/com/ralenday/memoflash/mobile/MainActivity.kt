package com.ralenday.memoflash.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.ralenday.memoflash.core.BaseDatos
import com.ralenday.memoflash.mobile.datos.LocalBaseDatos
import com.ralenday.memoflash.mobile.navegacion.AppMemoFlash
import com.ralenday.memoflash.mobile.tema.TemaMemoFlash

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val db = BaseDatos.get(this)
        setContent {
            TemaMemoFlash {
                CompositionLocalProvider(LocalBaseDatos provides db) { AppMemoFlash() }
            }
        }
    }
}
