package com.ralenday.memoflash.mobile

import android.app.Application
import com.ralenday.memoflash.core.sync.Sincronizador
import com.ralenday.memoflash.mobile.nube.Cuenta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MemoFlashApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Sincronizador.get(this).iniciar(scope, publicarAlArrancar = false)
        Cuenta.get(this).iniciar(scope)
    }
}
