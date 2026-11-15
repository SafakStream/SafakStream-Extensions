// SafakStream-Extensions/FilmMakinesi/build.gradle.kts

version = 1

android {
    namespace = "com.safakstream.FilmMakinesi"
}

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "FilmMakinesi - En yeni ve güncel filmleri ve dizileri Full HD kalitesinde Cloudstream üzerinden izleyin."

    status  = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    iconUrl = "https://filmmakinesi.to/favicon.ico"
}
