plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.safakstream.youtube"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Sadece CloudStream API'si — NewPipe YOK
    compileOnly("com.github.recloudstream:cloudstream:master-SNAPSHOT")
    implementation("com.github.Blatzar:NiceHttp:0.4.0")
    implementation("org.jsoup:jsoup:1.17.2")
}
