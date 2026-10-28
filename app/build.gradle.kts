plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "es.kinetrace.cajanegrapocket"
    compileSdk = 35

    defaultConfig {
        applicationId = "es.kinetrace.cajanegrapocket"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-prototipo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    }
}
