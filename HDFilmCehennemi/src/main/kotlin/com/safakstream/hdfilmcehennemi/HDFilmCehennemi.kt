package com.safakstream.hdfilmcehennemi
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
class HDFilmCehennemi : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val mainPage = mainPageOf("category/film-izle-2" to "Son Eklenen Filmler", "yabancidiziizle-5" to "Son Eklenen Diziler")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) "$mainUrl/${request.data}" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url).document
        val home = document.select("div.poster, a.poster").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request, home)
    }
    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.attr("href"))
        val title = this.selectFirst("strong.poster-title, h4.title, h2.title, .title")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } })
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }
    override suspend fun search(query: String): List<SearchResponse>? {
        val searchUrl = "$mainUrl/search?q=$query"
        val response = app.get(searchUrl, headers = mapOf("X-Requested-With" to "fetch", "Content-Type" to "application/json")).text
        val resultsJson = response.substringAfter("\"results\":[", "").substringBeforeLast("]")
        if (resultsJson.isBlank()) return null
        val htmlFragments = resultsJson.split("\",\"")
        val results = mutableListOf<SearchResponse>()
        for (fragment in htmlFragments) {
            val cleanHtml = fragment.replace("\\\"", "\"").replace("\\/", "/").trim('"', ' ')
            if (cleanHtml.isBlank()) continue
            val doc = org.jsoup.Jsoup.parse(cleanHtml)
            val a = doc.selectFirst("a") ?: continue
            val href = fixUrl(a.attr("href"))
            val title = a.selectFirst("h4.title")?.text() ?: continue
            val posterUrl = fixUrlNull(a.selectFirst("img")?.attr("src"))
            results.add(newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl })
        }
        return results
    }
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: "Bilinmeyen Film"
        val posterUrl = fixUrlNull(document.selectFirst("img.lazyload")?.attr("data-src"))
        val description = document.selectFirst("div.description, .film-desc")?.text()?.trim()
        val year = document.selectFirst("span.year")?.text()?.trim()?.toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = posterUrl
            this.plot = description
            this.year = year
        }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val iframeUrl = document.selectFirst("iframe")?.attr("data-src") ?: document.selectFirst("iframe")?.attr("src")
        if (iframeUrl != null) {
            val iframeDocument = app.get(fixUrl(iframeUrl)).text
            val contentUrl = Regex("\"contentUrl\":\\s*\"([^\"]+)\"").find(iframeDocument)?.groupValues?.get(1)
            if (contentUrl != null) {
                callback.invoke(newExtractorLink(source = this.name, name = this.name, url = contentUrl, type = ExtractorLinkType.M3U8) { this.referer = mainUrl; this.quality = Qualities.Unknown.value })
                return true
            }
        }
        return false
    }
}
