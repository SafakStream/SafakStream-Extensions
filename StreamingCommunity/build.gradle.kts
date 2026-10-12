plugins {
    id("com.android.library")
    // "org.jetbrains.kotlin.android" satırı KALDIRILDI
}

android {
    namespace = "com.safakstream.streamingcommunity"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Kotlin ayarları artık burada
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation("com.github.recloudstream:cloudstream3:master-SNAPSHOT")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
