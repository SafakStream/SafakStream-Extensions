package com.safakstream

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject

class DiziPal : MainAPI() {
    override var mainUrl              = "https://dizipal1822.com"
    override var name                 = "DiziPal"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Ana Sayfa"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        val document = app.get(request.data, headers = mapOf("User-Agent" to userAgent)).document
        val items = mutableListOf<SearchResponse>()

        for (li in document.select("li[data-u]")) {
            val type = li.attr("data-k")
            val title = li.attr("data-t")
            val url = li.attr("data-u")
            val poster = li.attr("data-p")
            if (url.isBlank()) continue

            val response = if (type.equals("Film", true)) {
                newMovieSearchResponse(title, url, TvType.Movie)
            } else {
                newTvSeriesSearchResponse(title, url, TvType.TvSeries)
            }
            response.posterUrl = poster
            if (items.none { it.url == url }) items.add(response)
        }

        if (items.isEmpty()) {
            for (a in document.select("a.trend-rank-card")) {
                val href = a.attr("href")
                val title = a.attr("title").removeSuffix(" izle").trim()
                if (href.isBlank() || title.isBlank()) continue
                val isMovie = href.contains("/film/")
                val response = if (isMovie) {
                    newMovieSearchResponse(title, href, TvType.Movie)
                } else {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries)
                }
                if (items.none { it.url == href }) items.add(response)
            }
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "${mainUrl}/bg/searchcontent",
            headers = mapOf(
                "User-Agent" to userAgent,
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$mainUrl/"
            ),
            data = mapOf("searchterm" to query)
        )
        val text = response.text.trim()
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<SearchResponse>()
        try {
            val root = JSONObject(text)
            val data = root.optJSONObject("data") ?: return emptyList()
            val arr = data.optJSONArray("result") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("object_name")
                if (title.isBlank()) continue
                val slug = obj.optString("used_slug").replace("\\/", "/")
                val usedType = obj.optString("used_type")
                val poster = obj.optString("object_poster_url")
                if (slug.isBlank()) continue
                val fullUrl = if (slug.startsWith("http")) slug else "$mainUrl/$slug"

                val searchResp = if (usedType.equals("Movies", true)) {
                    newMovieSearchResponse(title, fullUrl, TvType.Movie)
                } else {
                    newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries)
                }
                searchResp.posterUrl = poster
                results.add(searchResp)
            }
        } catch (e: Exception) {
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to userAgent)).document

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("meta[name=description]")?.attr("content")

        if (url.contains("/film/")) {
            val title = document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" | ")?.substringBefore(" İzle")?.trim()
                ?: document.selectFirst("h1")?.text()?.trim() ?: return null

            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val title = document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" | ")?.substringBefore(" İzle")?.trim()
                ?: document.selectFirst("h1")?.text()?.trim() ?: return null

            val episodes = mutableListOf<Episode>()

            for (a in document.select("a[href*='-sezon/'][href*='-bolum']")) {
                val href = a.attr("href").let { if (it.startsWith("http")) it else "$mainUrl$it" }
                if (!href.contains("-sezon/")) continue

                val epName = a.attr("aria-label")
                    .ifBlank { a.text().trim() }
                    .ifBlank { null } ?: continue

                val seasonMatch = Regex("(\\d+)-sezon").find(href)
                val episodeMatch = Regex("(\\d+)-bolum").find(href)
                val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
                val episode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()

                episodes.add(newEpisode(href) {
                    this.name = epName
                    this.season = season
                    this.episode = episode
                })
            }

            val uniqueEpisodes = episodes.distinctBy { it.data }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, uniqueEpisodes) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return false
    }
}
