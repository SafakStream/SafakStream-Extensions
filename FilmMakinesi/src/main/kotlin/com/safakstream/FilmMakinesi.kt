package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class FilmMakinesi : MainAPI() {
    override var mainUrl = "https://filmmakinesi.sh"
    override var name = "FilmMakinesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "$mainUrl/filmler/" else "$mainUrl/filmler/sayfa/$page/"
        val document = app.get(url).document

        val home = mutableListOf<HomePageList>()

        val movies = document.select("div.film-list div.item-relative").mapNotNull { it.toSearchResult() }
        if (movies.isNotEmpty()) {
            home.add(HomePageList("Son Filmler", movies))
        }

        return newHomePageResponse(home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("a.item") ?: return null
        val title = aTag.attr("data-title").takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(aTag.attr("href")) ?: return null
        val posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))
        val score = this.selectFirst("div.rating")?.text()?.trim()
        val isSeries = href.contains("/dizi/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/arama/?s=$query").document
        return document.select("div.film-list div.item-relative").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.select("div.info-description p").last()?.text()?.trim()
        val tags = document.selectFirst("dt:contains(Tür:) + dd")?.text()?.split(", ")
        val rating = document.selectFirst("dt:contains(IMDB Puanı:) + dd")?.text()?.trim()
        val year = document.selectFirst("dt:contains(Yapım Yılı:) + dd")?.text()?.trim()?.toIntOrNull()
        val isSeries = url.contains("/dizi/")

        return if (isSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
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

        val iframeSrc = document.selectFirst("div.after-player iframe")?.attr("data-src")
            ?: document.selectFirst("div.after-player iframe")?.attr("src")
            ?: ""

        if (iframeSrc.isNotBlank()) {
            loadExtractor(iframeSrc, "$mainUrl/", subtitleCallback, callback)
        }

        document.select("div.video-parts a").forEach {
            val iframeUrl = it.attr("data-video_url")
            if (iframeUrl.isNotBlank()) {
                loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, callback)
            }
        }

        return true
    }
}
