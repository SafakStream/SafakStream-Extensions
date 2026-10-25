// SafakStream-Extensions/SafakStreamTV/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "SafakStream TV - Ulusal, haber, spor, müzik ve belgesel kanalları (M3U listesi üzerinden canlı yayın)."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1
    tvTypes = listOf(
        "LiveStream",
        "Others"
    )
    iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/main/logo.png"
}
