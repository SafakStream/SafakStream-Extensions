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

    // Yedek plan: Normal istek engellenirse WebView ile dene
    private val webViewInterceptor = WebViewResolver(
        interceptUrl = Regex(".*"),
        useOkhttp = false
    )

    override val mainPage = mainPageOf(
        "$mainUrl/filmler-fmuyqb/" to "Son Filmler",
        "$mainUrl/yabanci-dizi-izle-fmq45w/" to "Son Diziler"
    )

    // Cloudflare engelini kontrol eden yardımcı fonksiyon
    private suspend fun fetchWithFallback(url: String): org.jsoup.nodes.Document {
        // Önce normal Chrome header'ları ile dene
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
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = fetchWithFallback(data)
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
