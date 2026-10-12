// SafakStream-Extensions/StreamingCommunity/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "it"
    description = "StreamingCommunity | Film e Serie TV"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/main/StreamingCommunity/icon.png"
}
