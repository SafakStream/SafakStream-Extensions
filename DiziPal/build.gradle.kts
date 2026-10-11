// DiziPal/build.gradle.kts
version = 1

cloudstream {
    description = "DiziPal eklentisi - SafakStream tarafından uyarlandı."
    authors = listOf("SafakStream")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    language = "tr"
    iconUrl = "https://www.google.com/s2/favicons?domain=dizipal1588.com&sz=%size%"
}

dependencies {
    // Cloudflare bypass için ekstra gerek yok, CloudStream'de hazır.
}
