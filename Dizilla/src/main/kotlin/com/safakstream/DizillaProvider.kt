package com.safakstream

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class DizillaProvider : MainAPI() {
    override var mainUrl = "https://dizilla.now"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Popüler Diziler",
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/anime-izle" to "Animeler",
        "$mainUrl/kdrama-izle" to "K-Drama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = mutableListOf<SearchResponse>()

        // Ana sayfa (Popüler Diziler) için trends listesi
        val trends = document.select("article.movie-type-genres ul.trends li")
        if (trends.isNotEmpty()) {
            home.addAll(trends.mapNotNull { it.toTrendSearchResult() })
        } else {
            // Diğer kategoriler için kart listesi
            home.addAll(document.select("div.new-added-list a").mapNotNull { it.toCardSearchResult() })
        }

        val homePageList = listOf(HomePageList(request.name, home.distinctBy { it.url }))
        return newHomePageResponse(homePageList, hasNext = false)
    }

    private fun Element.toTrendSearchResult(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("alt")?.removeSuffix(" izle")?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toCardSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text()?.trim() ?: return null
        val href = this.attr("href").takeIf { it.isNotEmpty() } ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/bg/searchcontent",
            data = mapOf("q" to query, "search" to query),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        )

        val document = response.document
        val results = document.select("a[href*='/dizi/'], a[href*='/film/']").mapNotNull {
            it.toSearchResult()
        }
        return results.distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href").takeIf { it.isNotEmpty() } ?: return null
        val title = this.selectFirst("h3")?.text()?.trim()
            ?: this.attr("title")?.removeSuffix(" izle")?.trim()
            ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: return null

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img[alt*='izle']")?.attr("src")

        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("div[class*=description], p[class*=description]")?.text()?.trim()

        val year = Regex("(\\d{4})").find(title)?.value?.toIntOrNull()
        val isSeries = url.contains("/dizi/")

        return if (isSeries) {
            val episodes = mutableListOf<Episode>()

            document.select("a[href*='-sezon-'][href*='-bolum']").forEach { epElement ->
                val epHref = epElement.attr("href").takeIf { it.isNotEmpty() } ?: return@forEach
                val epName = epElement.selectFirst("h3")?.text()?.trim()
                    ?: epElement.attr("title")?.trim()
                    ?: return@forEach

                val infoText = epElement.text()
                val epSeason = Regex("""(\d+)\.\s*Sezon""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                val epEpisode = Regex("""(\d+)\.\s*Bölüm""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()

                episodes.add(
                    newEpisode(fixUrl(epHref)) {
                        this.name = epName
                        this.season = epSeason
                        this.episode = epEpisode
                    }
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
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
