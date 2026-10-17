// SafakStream-Extensions/PapazSports/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "PapazSports - Canlı maç yayınları ve 7/24 spor kanalları Cloudstream üzerinden izle."

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
    iconUrl = "https://www.papazsports1030.pro/lib/img/logo.png"
}
