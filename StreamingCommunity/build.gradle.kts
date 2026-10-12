// SafakStream-Extensions/StreamingCommunity/build.gradle.kts

version = 1

cloudstream {
    description = "StreamingCommunity | Film e Serie TV"
    authors = listOf("SafakStream")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     **/
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Documentary",
        "Cartoon"
    )

    requiresResources = true
    language = "it"

    iconUrl = "https://streamingunity.dog/apple-touch-icon.png?v=2"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
}
