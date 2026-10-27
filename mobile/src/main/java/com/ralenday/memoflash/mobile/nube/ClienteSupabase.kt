package com.ralenday.memoflash.mobile.nube

import com.ralenday.memoflash.mobile.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

fun supabaseConfigurado(): Boolean =
    BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

fun crearClienteSupabase() = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_KEY,
) {
    defaultSerializer = KotlinXSerializer(Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    })
    install(Auth)
    install(Postgrest)
}
