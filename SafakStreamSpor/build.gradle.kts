// SafakStream-Extensions/SafakStreamSpor/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "SafakStream Spor - Canlı maç yayınları ve spor içerikleri"
    
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
    iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/builds/icons/SafakStreamSpor.png"
}
