package com.safakstream.dizipal

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import org.json.JSONObject
import org.json.JSONArray

class DizipalProvider : MainAPI() {
    override var mainUrl = "https://dizipal1588.com"
    override var name = "Dizipal"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "tr"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.Anime
    )

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private val HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/hd-film-izle" to "Filmler",
        "$mainUrl/anime" to "Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}/page/$page"
        val document = app.get(url, headers = HEADERS).document
        val items = document.select("a[data-dizipal-pageloader]")
            .filter { it.selectFirst("img.lazyload, img[data-src]") != null }
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href").takeIf { it.isNotBlank() } ?: return null
        
        // Başlık: title > img[alt] > slug
        val titleFromAttr = this.attr("title").removeSuffix(" izle").trim()
        val titleFromAlt = this.selectFirst("img[alt]")?.attr("alt")?.removeSuffix(" izle")?.trim()
        val titleFromSlug = href.substringAfterLast("/")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        
        val title = titleFromAttr.takeIf { it.isNotBlank() && !it.contains("Dizipal", ignoreCase = true) }
            ?: titleFromAlt?.takeIf { it.isNotBlank() && !it.contains("Dizipal", ignoreCase = true) }
            ?: titleFromSlug
        
        // Poster
        val poster = this.selectFirst("img[data-src]")?.attr("data-src")
            ?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
        
        val type = when {
            href.contains("/movies/") -> TvType.Movie
            href.contains("/anime/") -> TvType.Anime
            else -> TvType.TvSeries
        }
        
        return newMovieSearchResponse(title, fixUrl(href), type) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url, headers = HEADERS).document
        return document.select("a[data-dizipal-pageloader]")
            .filter { it.selectFirst("img.lazyload, img[data-src]") != null }
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = HEADERS).document
        val ldJson = document.selectFirst("script[type=application/ld+json]")?.data() ?: return null

        val json = JSONObject(ldJson)
        val type = json.optString("@type")
        val title = json.optString("name").ifBlank {
            document.selectFirst("h1")?.text() ?: return null
        }
        val description = json.optString("description").takeIf { it.isNotBlank() }
        val poster = json.optString("image").takeIf { it.isNotBlank() }
        val year = json.optString("datePublished").take(4).toIntOrNull()

        val tvType = when (type) {
            "TVSeries" -> TvType.TvSeries
            "Movie" -> TvType.Movie
            else -> TvType.TvSeries
        }

        val episodes = mutableListOf<Episode>()

        if (tvType == TvType.TvSeries) {
            val seasons = json.optJSONArray("containsSeason") ?: JSONArray()
            for (i in 0 until seasons.length()) {
                val season = seasons.getJSONObject(i)
                val seasonNum = season.optInt("seasonNumber", i + 1)
                val eps = season.optJSONArray("episode") ?: JSONArray()
                for (j in 0 until eps.length()) {
                    val ep = eps.getJSONObject(j)
                    val epName = ep.optString("name").ifBlank { "$seasonNum. Sezon ${j + 1}. Bölüm" }
                    val epUrl = ep.optString("url")
                    val epNum = ep.optInt("episodeNumber", j + 1)
                    if (epUrl.isNotBlank()) {
                        episodes.add(
                            newEpisode(epUrl) {
                                this.name = epName
                                this.episode = epNum
                                this.season = seasonNum
                            }
                        )
                    }
                }
            }
        }

        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = HEADERS).document
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")

        if (!iframeSrc.isNullOrBlank()) {
            loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            return true
        }

        return false
    }
}
