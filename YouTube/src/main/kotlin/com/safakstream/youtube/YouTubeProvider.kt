package com.safakstream.youtube

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class YouTubeProvider : MainAPI() {

    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Others)

    // --- ANA SAYFA ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = listOf(
            newMovieSearchResponse(
                "Örnek Video",
                "$mainUrl/watch?v=dQw4w9WgXcQ",
                TvType.Others
            ) {
                this.posterUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
            }
        )
        return newHomePageResponse(
            listOf(HomePageList("Popüler", items)),
            hasNext = false
        )
    }

    // --- ARAMA ---
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/results?search_query=${query.replace(" ", "+")}"
        val document = app.get(url).document

        val results = document.select("ytd-video-renderer").mapNotNull { it ->
            it.toSearchResult()
        }

        return results
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val videoUrl = this.selectFirst("a#video-title")?.attr("href")
            ?: return null
        val title = this.selectFirst("a#video-title")?.text()
            ?: return null
        val poster = this.selectFirst("img")?.attr("src")

        val fullUrl = if (videoUrl.startsWith("http")) {
            videoUrl
        } else {
            "$mainUrl$videoUrl"
        }

        return newMovieSearchResponse(title, fullUrl, TvType.Others) {
            this.posterUrl = poster
        }
    }

    // --- DETAY SAYFASI ---
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: document.selectFirst("title")?.text()
            ?: "Bilinmeyen Video"

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    // --- OYNATMA LİNKİ ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // CloudStream'in hazır extractor sistemini kullan
        loadExtractor(data, null, subtitleCallback, callback)
        return true
    }
}
