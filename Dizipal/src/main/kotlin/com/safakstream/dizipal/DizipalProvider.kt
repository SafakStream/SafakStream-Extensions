package com.safakstream.dizipal

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import org.jsoup.Jsoup
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
    }

    override val mainPage = mainPageOf(
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/hd-film-izle" to "Filmler",
        "$mainUrl/anime" to "Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}/page/$page"
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        val items = document.select("article, div.movie-box, div.poster, div.series-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2, h3, .title, a")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val poster = this.selectFirst("img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }
        return newMovieSearchResponse(title, fixUrl(href), TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        return document.select("article, div.movie-box, div.poster, div.series-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
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
        val document = app.get(data, headers = mapOf("User-Agent" to USER_AGENT)).document

        // 1) data-rm-k="true" div'inde şifreli JSON var
        val encryptedJson = document.selectFirst("div[data-rm-k=true]")?.text()

        // 2) iframe'i bul (JS çalıştırmayanlar için fallback)
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")

        if (!iframeSrc.isNullOrBlank()) {
            loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            return true
        }

        // 3) Eğer iframe boşsa, JS şifre çözme gerekli
        // Cloudstream WebViewResolver ile video URL'sini yakalayacağız
        // (Bu kısmı sonra ekleyeceğiz)

        return false
    }
}
