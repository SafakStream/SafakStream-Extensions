// CineStream/build.gradle.kts
version = 1 // Kendi deponuz için sürüm numarası

// NOT: Orijinal kod local.properties'ten API anahtarı okuyordu.
// Biz bu anahtarları geçici olarak boş bırakıyoruz.
// Eğer ilgili özellikleri (Simkl, TMDB vb.) kullanmak isterseniz,
// kendi API anahtarlarınızı buraya yazmanız gerekir.
android {
    defaultConfig {
        buildConfigField("String", "SIMKL_API", "\"\"")
        buildConfigField("String", "TMDB_KEY", "\"\"")
        buildConfigField("String", "CASTLE_KEY", "\"\"")
        buildConfigField("String", "MOVIEBLAST_TOKEN", "\"\"")
        buildConfigField("String", "MOVIEBLAST_API", "\"\"")
        buildConfigField("String", "MOVIEBLAST_KEY", "\"\"")
    }
}

cloudstream {
    language = "tr" // Türkçe yaptık
    description = "Film, Dizi, Anime, Asya Dizileri ve Torrentler için tek adres çözümü - SafakStream tarafından uyarlandı."
    authors = listOf("SafakStream") // Kendi adınızı yazdık
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama",
        "Anime",
        "Torrent"
    )
    // İkon yolunu depomuza göre düzenledik
    iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/builds/icons/CineStream.png"
}
