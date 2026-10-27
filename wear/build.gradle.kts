plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ralenday.memoflash.wear"
    compileSdk = 37

    defaultConfig {
        // Debe ser el MISMO applicationId que el del teléfono para que la Data Layer los enlace
        applicationId = "com.ralenday.memoflash"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.wear.compose:compose-material3:1.7.1")
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    implementation("androidx.wear.compose:compose-navigation:1.7.1")
}
