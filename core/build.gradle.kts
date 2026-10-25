plugins {
    id("com.android.library")
}

android {
    namespace = "com.ralenday.memoflash.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    // Wearable Data Layer: sincroniza teléfono <-> reloj por Bluetooth/Wi-Fi sin internet
    api("com.google.android.gms:play-services-wearable:19.0.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
