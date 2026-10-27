import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.ralenday.memoflash.mobile"
    compileSdk = 37

    defaultConfig {
        // Debe ser el MISMO applicationId que el del reloj para que la Data Layer los enlace
        applicationId = "com.ralenday.memoflash"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val supabase = Properties()
        val archivo = rootProject.file("supabase.properties")
        if (archivo.exists()) archivo.inputStream().use { supabase.load(it) }
        fun campo(nombre: String): String {
            val valor = supabase.getProperty(nombre).orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$valor\""
        }
        buildConfigField("String", "SUPABASE_URL", campo("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_KEY", campo("SUPABASE_KEY"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Espresso 3.7+ es compatible con Android 16/17 (la versión que trae compose ui-test es anterior)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
