// SafakStream-Extensions/Filmmakinesi/build.gradle.kts

version = 1

cloudstream {
    authors     = listOf("SafakStream")
    language    = "tr"
    description = "FilmMakinesi - Türkçe Dublaj ve Altyazılı Film & Dizi İzleme Sitesi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=filmmakinesi.to&sz=%size%"
}
