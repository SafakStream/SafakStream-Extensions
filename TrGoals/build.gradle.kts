// SafakStream-Extensions/TrGoals/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "TrGoals - Canlı maç yayınları, 7/24 TV kanalları ve spor karşılaşmalarını Cloudstream üzerinden izle."

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
    iconUrl = "https://www.trgoals183.top/lib/img/logo.png"
}
