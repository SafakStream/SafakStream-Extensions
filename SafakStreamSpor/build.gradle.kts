// SafakStream-Extensions/BeyazElma/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "BeyazElma - Canlı maç yayınları, spor kanalları ve canlı skorları Cloudstream üzerinden izle."

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
    iconUrl = "https://beyazelma78.com/favicon.png"
}
