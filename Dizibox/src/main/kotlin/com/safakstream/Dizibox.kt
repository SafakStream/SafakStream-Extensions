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
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.nodes.Document

class Dizibox : MainAPI() {
    override var mainUrl              = "https://ddizibox.com"
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val defaultPlayerBase = "https://pilavyerplay.top"

    // YENI KATEGORILER (RSS yerine sitenin HTML listelerini kaziyoruz)
    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Tüm Diziler",
        "${mainUrl}/filmler" to "Tüm Filmler",
        "${mainUrl}/yeni-diziler" to "Yeni Diziler",
        "${mainUrl}/yeni-filmler" to "Yeni Filmler"
    )

    // ============ ANA SAYFA (HTML) ============
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Sayfalama varsa ?page=2 seklinde ekle, yoksa 1. sayfayi cek
        val url = if (page > 1) "${request.data}?page=$page" else request.data
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val items = parseListingPage(doc)
        return newHomePageResponse(request.name, items)
    }

    // Hem arama hem ana sayfa listeleri icin ortak kaziyici (Senin arama mantiginin aynisi)
    private fun parseListingPage(doc: Document): List<SearchResponse> {
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

    // ============ ARAMA ============
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/ara?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return parseListingPage(doc)
    }

    // ============ DETAY ============
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val isMovie = url.contains("/film/")

        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: ""
        val title = ogTitle.substringBefore(" izle").substringBefore(" |").trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val description = doc.selectFirst("p.mt-5.max-w-3xl")?.text()?.trim()
            ?: doc.selectFirst("p.mt-5.max-w-2xl")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val year = Regex("""\((\d{4})\)""").find(ogTitle)?.groupValues?.get(1)?.toIntOrNull()

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            val episodeList = mutableListOf<Episode>()
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
            val seasonNum = Regex("""/sezon-(\d+)/""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            val episodeNum = Regex("""/bolum-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
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

    // ============ VIDEO LINKLERI ============
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("User-Agent" to userAgent)).document
        val pvId = doc.selectFirst("div[data-pv]")?.attr("data-pv")
            ?: doc.select("div[data-pv]").firstOrNull()?.attr("data-pv")
            ?: return false
        val scriptSrc = doc.select("script[src*=pilavyerplay]").firstOrNull()?.attr("src")
        val dynamicBase = if (scriptSrc != null && scriptSrc.contains("/assets/js/")) {
            scriptSrc.substringBefore("/assets/js/")
        } else {
            defaultPlayerBase
        }
        val playerUrl = "$dynamicBase/assets/js/s.php?s=$pvId"
        val playerHtml = app.get(
            playerUrl,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to data,
                "Origin" to mainUrl
            )
        ).text
        val playerJsonMatch = Regex("""window\.__PLAYER__\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(playerHtml)?.groupValues?.get(1) ?: return false

        val playerObj = JSONObject(playerJsonMatch)
        val streamUrl = playerObj.optString("stream", "")
        if (streamUrl.isBlank()) return false

        val subsArray = playerObj.optJSONArray("subs")
        if (subsArray != null) {
            for (i in 0 until subsArray.length()) {
                val sub = subsArray.getJSONObject(i)
                val subUrl = sub.optString("src", "")
                val subLang = sub.optString("lang", "tr")
                if (subUrl.isNotBlank()) {
                    subtitleCallback(SubtitleFile(subLang, subUrl))
                }
            }
        }

        val m3u8Response = app.get(
            streamUrl,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to playerUrl,
                "Origin" to dynamicBase
            )
        ).text

        if (!m3u8Response.contains("#EXTM3U")) {
            if (streamUrl.contains(".mp4")) {
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "DiziBox",
                        url = streamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = playerUrl
                        this.quality = 1080
                    }
                )
                return true
            }
            return false
        }

        callback(
            newExtractorLink(
                source = this.name,
                name = "DiziBox",
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = playerUrl
                this.quality = 1080
            }
        )

        return true
    }
}
