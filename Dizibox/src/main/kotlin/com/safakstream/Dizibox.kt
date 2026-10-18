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
import org.jsoup.nodes.Document

class Dizibox : MainAPI() {
    override var mainUrl              = "https://ddizibox.com"
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "${mainUrl}/rss.xml" to "Son Eklenenler"
    )

    // ============ ANA SAYFA (RSS) ============
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())

        val body = app.get(request.data, headers = mapOf("User-Agent" to userAgent)).text
        val items = parseRss(body)
        return newHomePageResponse(request.name, items)
    }

    private fun parseRss(xml: String): List<SearchResponse> {
        val doc = org.jsoup.Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
        val results = mutableListOf<SearchResponse>()
        for (item in doc.select("item")) {
            val title = item.selectFirst("title")?.text()?.trim() ?: continue
            val link  = item.selectFirst("link")?.text()?.trim() ?: continue
            if (link.isBlank()) continue

            val cleanTitle = title.removeSuffix(" izle").trim()

            val poster = item.selectFirst("media|content")?.attr("url")
                ?: item.selectFirst("enclosure")?.attr("url")
                ?: ""

            val isMovie = link.contains("/film/")
            val response = if (isMovie) {
                newMovieSearchResponse(cleanTitle, link, TvType.Movie)
            } else {
                newTvSeriesSearchResponse(cleanTitle, link, TvType.TvSeries)
            }
            response.posterUrl = poster
            if (results.none { it.url == link }) results.add(response)
        }
        return results
    }

    // ============ ARAMA ============
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/ara?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return parseSearchResults(doc)
    }

    private fun parseSearchResults(doc: Document): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        for (img in doc.select("img[alt]")) {
            val alt = img.attr("alt")
            if (!alt.contains(" izle")) continue
            val a = img.closest("a") ?: continue
            val href = a.attr("href")
            if (href.isBlank()) continue
            if (!href.contains("/dizi/") && !href.contains("/film/")) continue

            val cleanTitle = alt.removeSuffix(" izle").trim()
            val poster = img.attr("src")

            val isMovie = href.contains("/film/")
            val response = if (isMovie) {
                newMovieSearchResponse(cleanTitle, href, TvType.Movie)
            } else {
                newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries)
            }
            response.posterUrl = poster

            if (results.none { it.url == href }) results.add(response)
        }
        return results
    }

    // ============ DETAY ============
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val isMovie = url.contains("/film/")

        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: ""
        val title = ogTitle.substringBefore(" izle").substringBefore(" |").trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val year = Regex("""\((\d{4})\)""").find(ogTitle)?.groupValues?.get(1)?.toIntOrNull()

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            val episodeList = mutableListOf<Episode>()

            // Sezon linkleri: /dizi/{slug}?sezon=N#bolumler
            val seasonLinks = doc.select("a[href*='?sezon=']").map { it.attr("href") }.distinct()

            if (seasonLinks.isEmpty()) {
                episodeList.addAll(parseEpisodes(doc))
            } else {
                for (sLink in seasonLinks) {
                    val fullUrl = if (sLink.startsWith("http")) sLink else mainUrl + sLink
                    val seasonDoc = app.get(fullUrl, headers = mapOf("User-Agent" to userAgent)).document
                    episodeList.addAll(parseEpisodes(seasonDoc))
                }
            }

            val distinctEpisodes = episodeList.distinctBy { it.data }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, distinctEpisodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    private fun parseEpisodes(doc: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        for (a in doc.select("a[href*='/sezon-'][href*='/bolum-']")) {
            val href = a.attr("href")
            if (href.isBlank()) continue

            val seasonMatch = Regex("""/sezon-(\d+)/""").find(href)
            val episodeMatch = Regex("""/bolum-(\d+)""").find(href)
            val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull()
            val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull()

            val name = a.selectFirst("p")?.text()?.trim()
                ?: "Sezon ${seasonNum ?: "?"} Bölüm ${episodeNum ?: "?"}"

            episodes.add(
                newEpisode(href) {
                    this.name = name
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            )
        }
        return episodes
    }

    // ============ VIDEO LINKLERI (FAZ 1: BOS) ============
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Faz 2'de pilavyerplay.top entegrasyonu eklenecek
        return false
    }
}
