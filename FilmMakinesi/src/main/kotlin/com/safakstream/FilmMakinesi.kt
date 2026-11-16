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

    // Cloudflare'i aşmak için Chrome header'ları
    private val chromeHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1"
    )

    // Yedek plan: WebViewResolver
    private val webViewInterceptor = WebViewResolver(
        interceptUrl = Regex(".*"),
        useOkhttp = false
    )

    // Nikstream gibi sequential istekler
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override var sequentialMainPageScrollDelay = 100L

    override val mainPage = mainPageOf(
        "$mainUrl/filmler-fmuyqb/" to "Son Filmler",
        "$mainUrl/yabanci-dizi-izle-fmq45w/" to "Son Diziler"
    )

    // Yardımcı fonksiyon: Chrome header'ları ile dene, engellenirse WebView'e geç
    private suspend fun fetchWithFallback(url: String): org.jsoup.nodes.Document {
        val normalDoc = app.get(url, headers = chromeHeaders).document
        val title = normalDoc.title()
        return if (title.contains("Attention Required") || title.contains("Cloudflare") || title.contains("Just a moment")) {
            // Engellendiyse WebView'e geç
            app.get(url, interceptor = webViewInterceptor).document
        } else {
            normalDoc
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}sayfa/$page/"
        val document = fetchWithFallback(url)
        val items = document.select("div.item-relative > a.item, a.item-ep, div.tv--section a.item")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/arama/?s=$query"
        val document = fetchWithFallback(url)
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
        val document = fetchWithFallback(url)
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
        val document = fetchWithFallback(data)
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
