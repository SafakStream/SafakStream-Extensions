package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FilmmakinesiProvider : MainAPI() {
    override var mainUrl = "https://filmmakinesi.to"
    override var name = "FilmMakinesi"
    override var lang = "tr"
    override var hasQuickSearch = true
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var mainPage = listOf(
        MainPageData("Son Eklenenler", "$mainUrl/")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "$mainUrl/filmler-1/sayfa/$page/"
        val document = app.get(url).document

        val homeItems = document.select("div.film-list div.content div.col-6 a.item").mapNotNull { it.toSearchResult() }
        val seriesItems = document.select("div.tv--section a.item").mapNotNull { it.toSearchResult() }

        val allItems = (homeItems + seriesItems).distinctBy { it.url }

        return newHomePageResponse(
            list = listOf(HomePageList(request.name, allItems)),
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/arama/?s=$query"
        val document = app.get(url).document

        val results = document.select("div.film-list div.content div.col-6 a.item, div.content div.col-6 a.item, a.item").mapNotNull { it.toSearchResult() }

        if (results.isEmpty()) {
            val fallbackUrl = "$mainUrl/?s=$query"
            val fallbackDoc = app.get(fallbackUrl).document
            return fallbackDoc.select("a.item").mapNotNull { it.toSearchResult() }
        }

        return results.distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href").takeIf { it.isNotEmpty() } ?: return null
        val title = this.selectFirst("div.item-footer div.title")?.text()
            ?: this.selectFirst("div.item-title")?.text()
            ?: this.selectFirst("div.title")?.text()
            ?: return null

        val posterUrl = this.selectFirst("img.thumbnail")?.attr("src")
            ?: this.selectFirst("img")?.attr("src")

        val year = this.selectFirst("div.item-footer div.info span")?.text()?.toIntOrNull()
        val isSeries = href.contains("/dizi/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
            }
        } else {
            newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text()?.substringBefore(" izle")?.trim()
            ?: document.selectFirst("h1")?.text() ?: return null

        val posterUrl = document.selectFirst("img.cover-img")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val year = document.selectFirst("h1.title span.date a")?.text()?.toIntOrNull()
        val description = document.selectFirst("div.info-description p")?.text()
        val imdb = document.selectFirst("div.imdb b")?.text()
        val duration = document.selectFirst("div.time")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val genres = document.select("div.type a").map { it.text() }
        val isSeries = url.contains("/dizi/")

        return if (isSeries) {
            val episodes = document.select("div.season-episodes a, div.episode-list a").mapNotNull { ep ->
                val epHref = ep.attr("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val epTitle = ep.text()
                newEpisode(fixUrl(epHref)) {
                    this.name = epTitle
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
                this.plot = description
                this.tags = genres
                this.duration = duration
                this.score = imdb?.replace(",", ".")?.toDoubleOrNull()?.let { Score.from10(it) }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
                this.plot = description
                this.tags = genres
                this.duration = duration
                this.score = imdb?.replace(",", ".")?.toDoubleOrNull()?.let { Score.from10(it) }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        var found = false

        document.select("div.video-parts a[data-video_url]").forEach { element ->
            val videoUrl = element.attr("data-video_url")
            val sourceName = element.text().trim()

            if (videoUrl.isNotEmpty()) {
                val linkName = when {
                    videoUrl.contains("closeload") -> "$sourceName (Close)"
                    videoUrl.contains("rapid") -> "$sourceName (Rapid)"
                    else -> sourceName
                }
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = linkName,
                        url = videoUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                    }
                )
                found = true
            }
        }

        if (!found) {
            document.select("div.after-player iframe").forEach { iframe ->
                val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
                if (src.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = fixUrl(src),
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = mainUrl
                        }
                    )
                    found = true
                }
            }
        }

        return found
    }
}
