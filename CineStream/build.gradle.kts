// CineStream/build.gradle.kts
version = 1

android {
    defaultConfig {
        android.buildFeatures.buildConfig = true
        
        buildConfigField("String", "SIMKL_API", "\"\"")
        buildConfigField("String", "TMDB_KEY", "\"\"")
        buildConfigField("String", "CASTLE_KEY", "\"\"")
        buildConfigField("String", "MOVIEBLAST_TOKEN", "\"\"")
        buildConfigField("String", "MOVIEBLAST_API", "\"\"")
        buildConfigField("String", "MOVIEBLAST_KEY", "\"\"")
    }
}

cloudstream {
    language = "tr"
    description = "Film, Dizi, Anime, Asya Dizileri ve Torrentler için tek adres çözümü - SafakStream tarafından uyarlandı."
    authors = listOf("SafakStream")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama",
        "Anime",
        "Torrent"
    )
    iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/builds/icons/CineStream.png"
}

dependencies {
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Kotlin Serialization (CineStreamParser.kt'deki @Serializable için)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    
    // Jsoup
    implementation("org.jsoup:jsoup:1.18.1")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
