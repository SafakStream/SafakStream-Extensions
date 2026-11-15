package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.nodes.Element

class FilmMakinesi : MainAPI() {
    override var mainUrl = "https://filmmakinesi.to"
    override var name = "FilmMakinesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val webViewInterceptor = WebViewResolver(
        interceptUrl = Regex(".*"),
        useOkhttp = false
    )

    override val mainPage = mainPageOf(
        "$mainUrl/filmler-fmuyqb/" to "Son Filmler",
        "$mainUrl/yabanci-dizi-izle-fmq45w/" to "Son Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}sayfa/$page/"
        val document = app.get(url, interceptor = webViewInterceptor).document
        val items = document.select("div.item-relative > a.item, a.item-ep, div.tv--section a.item")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/arama/?s=$query"
        val document = app.get(url, interceptor = webViewInterceptor).document
        return document.select("div.item-relative > a.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = attr("href")
        if (href.isBlank()) return null
        val title = attr("data-title").ifBlank { selectFirst("div.title")?.text() ?: return null }
        val poster = selectFirst("img.thumbnail")?.attr("src") ?: ""
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, fixUrl(href), type) {
            this.posterUrl = fixUrl(poster)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = webViewInterceptor).document
        val title = document.selectFirst("h1.title, div.title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("img.thumbnail, meta[property=og:image]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.attr("src")
        } ?: ""
        val description = document.selectFirst("div.description, meta[name=description]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }

        val isTvSeries = url.contains("/dizi/")

        if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.ep-content a, div.episodes a, a.item-ep").forEach { ep ->
                val epUrl = fixUrl(ep.attr("href"))
                val epName = ep.selectFirst("div.ep-title")?.text() ?: ep.text()
                episodes.add(newEpisode(epUrl) { this.name = epName })
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrl(poster)
                this.plot = description
                this.episodes = episodes
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data, interceptor = webViewInterceptor).document
        val iframe = document.selectFirst("iframe")?.attr("src") ?: return false
        val fixedIframe = fixUrl(iframe)

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = fixedIframe
            ) {
                this.quality = Qualities.Unknown.value
                this.referer = data
            }
        )
        return true
    }

    private fun fixUrl(url: String): String {
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        return "$mainUrl/${url.removePrefix("/")}"
    }
}
