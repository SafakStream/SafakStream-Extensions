// SafakStream-Extensions/Betmatik/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "Betmatik - Canlı maç yayınları ve 7/24 spor kanalları (Zirve Stream altyapısı)."

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
    iconUrl = "https://betmatiktv156.com/favicon.ico"
}
