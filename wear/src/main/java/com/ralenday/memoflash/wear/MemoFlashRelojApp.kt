package com.ralenday.memoflash.wear

import android.app.Application
import com.ralenday.memoflash.core.sync.Sincronizador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MemoFlashRelojApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Sincronizador.get(this).iniciar(scope)
    }
}
