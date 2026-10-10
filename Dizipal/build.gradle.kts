version = 1

cloudstream {
    language = "tr"
    description = "Dizipal - Türkçe dizi ve film izleme sitesi eklentisi."
    authors = listOf("SafakStream")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie"
    )
    iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/builds/icons/Dizipal.png"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
