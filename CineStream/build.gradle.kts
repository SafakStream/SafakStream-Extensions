// CineStream/build.gradle.kts
version = 1

// NOT: Orijinal kod local.properties'ten API anahtarı okuyordu.
// Biz bu anahtarları geçici olarak boş bırakıyoruz.
android {
    defaultConfig {
        // BU SATIRI EKLEYİN:
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
