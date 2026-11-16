package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class FilmMakinesi : MainAPI() {
    override var mainUrl = "https://filmmakinesi.to"
    override var name = "FilmMakinesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val cloudflareKiller by lazy { CloudflareKiller() }

    override val mainPage = mainPageOf(
        "$mainUrl/filmler-fmuyqb/" to "Son Filmler",
        "$mainUrl/yabanci-dizi-izle-fmq45w/" to "Son Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}sayfa/$page/"
        val document = app.get(url, interceptor = cloudflareKiller).document
        val items = document.select("div.item-relative > a.item, a.item-ep, div.tv--section a.item")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/arama/?s=$query"
        val document = app.get(url, interceptor = cloudflareKiller).document
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
        val document = app.get(url, interceptor = cloudflareKiller).document
        val title = document.selectFirst("h1.title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.cover img.cover-img, meta[property=og:image]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.attr("src")
        } ?: ""
        val description = document.selectFirst("div.info-description, meta[name=description]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }

        val isTvSeries = url.contains("/dizi/")
        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("a.item-ep").forEach { ep ->
                val epUrl = fixUrl(ep.attr("href"))
                val epName = ep.selectFirst("div.ep-title")?.text() ?: ep.text()
                val seasonNum = Regex("sezon-(\\d+)").find(epUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = Regex("bolum-(\\d+)").find(epUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                episodes.add(newEpisode(epUrl) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = episodeNum
                })
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrl(poster)
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data, interceptor = cloudflareKiller).document
        var foundAny = false

        document.select("div.video-parts a").forEach { element ->
            val iframeUrl = element.attr("data-video_url")
            if (iframeUrl.isNotBlank()) {
                loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, callback)
                foundAny = true
            }
        }

        if (!foundAny) {
            val iframe = document.selectFirst("div.after-player iframe")?.attr("data-src")
                ?: document.selectFirst("div.after-player iframe")?.attr("src")
            if (!iframe.isNullOrBlank()) {
                loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
                foundAny = true
            }
        }
        return foundAny
    }

    private fun fixUrl(url: String): String {
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        return "$mainUrl/${url.removePrefix("/")}"
    }
}
