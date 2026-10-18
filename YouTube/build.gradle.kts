// Youtube/build.gradle.kts
version = 1

cloudstream {
    description = "YouTube eklentisi - SafakStream tarafından uyarlandı."
    authors = listOf("SafakStream")
    status = 1
    tvTypes = listOf("Others")
    language = "tr"
    iconUrl = "https://www.google.com/s2/favicons?domain=youtube.com&sz=%size%"
}

dependencies {
    // NewPipe Extractor kütüphanesi (master-SNAPSHOT yerine stabil sürüm kullanıyoruz)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.3")
}
