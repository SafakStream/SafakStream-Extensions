package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data).document

        val trendItems = document.select("article.movie-type-genres ul.trends li").mapNotNull { it.toSearchResult() }
        val newItems = document.select("div.new-added-list a").mapNotNull { it.toSearchResultFromCard() }

        val homePageList = mutableListOf<HomePageList>()
        if (trendItems.isNotEmpty()) homePageList.add(HomePageList("Popüler Diziler", trendItems))
        if (newItems.isNotEmpty()) homePageList.add(HomePageList("Yeni Eklenenler", newItems))

        return newHomePageResponse(homePageList, hasNext = false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("img")?.attr("alt")?.removeSuffix(" izle")?.trim() ?: return null
        val poster = this.selectFirst("img")?.attr("src")

        return newTvSeriesSearchResponse(
            name = title,
            url = fixUrl(href),
            posterUrl = poster,
        )
    }

    private fun Element.toSearchResultFromCard(): SearchResponse? {
        val href = this.attr("href").takeIf { it.isNotEmpty() } ?: return null
        val title = this.selectFirst("h3")?.text()?.trim() ?: return null
        val poster = this.selectFirst("img")?.attr("src")

        return newTvSeriesSearchResponse(
            name = title,
            url = fixUrl(href),
            posterUrl = poster,
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
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
            it.toSearchResultFromSearchPage()
        }
        return results.distinctBy { it.url }
    }

    private fun Element.toSearchResultFromSearchPage(): SearchResponse? {
        val href = this.attr("href").takeIf { it.isNotEmpty() } ?: return null
        val title = this.selectFirst("h3")?.text()?.trim()
            ?: this.attr("title")?.removeSuffix(" izle")?.trim()
            ?: return null
        val poster = this.selectFirst("img")?.attr("src")

        return newTvSeriesSearchResponse(
            name = title,
            url = fixUrl(href),
            posterUrl = poster,
        )
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
                val epTitle = epElement.selectFirst("h3")?.text()?.trim()
                    ?: epElement.attr("title")?.trim()
                    ?: return@forEach

                val seasonEpRegex = Regex("(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm")
                val match = seasonEpRegex.find(epElement.text())
                val season = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episode = match?.groupValues?.get(2)?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(fixUrl(epHref)) {
                        this.name = epTitle
                        this.season = season
                        this.episode = episode
                    }
                )
            }

            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                posterUrl = poster,
                plot = description,
                year = year,
                episodes = episodes,
            )
        } else {
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                posterUrl = poster,
                plot = description,
                year = year,
                dataUrl = url,
            )
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
