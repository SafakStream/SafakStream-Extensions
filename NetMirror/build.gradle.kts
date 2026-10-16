version = 1

cloudstream {
    description = "NetMirror eklentisi - Netflix, Hotstar, Prime icerikleri"
    authors = listOf("SafakStream")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    language = "tr"
    iconUrl = "https://www.google.com/s2/favicons?domain=net77.cc&sz=64"
}

android {
    namespace = "com.safakstream.netmirror"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("androidx.core:core:1.13.1")
    implementation("org.jsoup:jsoup:1.17.2")
}
