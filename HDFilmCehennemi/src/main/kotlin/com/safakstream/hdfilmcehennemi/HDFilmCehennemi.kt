package com.safakstream.hdfilmcehennemi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HDFilmCehennemi : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "category/film-izle-2" to "Son Eklenen Filmler",
        "yabancidiziizle-5" to "Son Eklenen Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) "$mainUrl/${request.data}" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val home = document.select("div.poster, a.poster").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.attr("href"))
        val title = this.selectFirst("strong.poster-title, h4.title, h2.title, .title")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.let { 
            it.attr("data-src").ifBlank { it.attr("src") } 
        })
        val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val searchUrl = "$mainUrl/search?q=$query"
        val response = app.get(
            searchUrl,
            headers = mapOf(
                "X-Requested-With" to "fetch",
                "Content-Type" to "application/json",
                "User-Agent" to userAgent
            )
        ).text

        val resultsJson = response.substringAfter("\"results\":[", "").substringBeforeLast("]")
        if (resultsJson.isBlank()) return null

        val htmlFragments = resultsJson.split("\",\"")
        val results = mutableListOf<SearchResponse>()

        for (fragment in htmlFragments) {
            val cleanHtml = fragment.replace("\\\"", "\"").replace("\\/", "/").trim('"', ' ')
            if (cleanHtml.isBlank()) continue

            val doc = org.jsoup.Jsoup.parse(cleanHtml)
            val a = doc.selectFirst("a") ?: continue
            
            val href = fixUrl(a.attr("href"))
            val title = a.selectFirst("h4.title")?.text() ?: continue
            val posterUrl = fixUrlNull(a.selectFirst("img")?.attr("src"))
            val type = if (href.contains("/dizi/")) TvType.TvSeries else TvType.Movie
            
            results.add(newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            })
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val htmlText = document.html()

        val jsonLdRegex = Regex("<script type=\"application/ld\\+json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
        val jsonLd = jsonLdRegex.find(htmlText)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\/", "/")

        var title = document.selectFirst("h1")?.text()?.trim() ?: "Bilinmeyen Film"
        var posterUrl: String? = null
        var description: String? = null
        var year: Int? = null
        val isSeries = url.contains("/dizi/")

        if (jsonLd != null) {
            val descRegex = Regex("\"description\":\"(.*?)\"")
            description = descRegex.find(jsonLd)?.groupValues?.get(1)

            val posterRegex = Regex("\"thumbnail\":\\{\"@type\":\"ImageObject\",\"contentUrl\":\"(.*?)\"\\}")
            posterUrl = posterRegex.find(jsonLd)?.groupValues?.get(1)

            val yearRegex = Regex("\"datePublished\":\"(\\d{4})")
            year = yearRegex.find(jsonLd)?.groupValues?.get(1)?.toIntOrNull()
        }

        if (posterUrl == null) {
            posterUrl = fixUrlNull(document.selectFirst("img.lazyload")?.attr("data-src"))
        }

        return if (isSeries) {
            val episodes = mutableListOf<Episode>()

            val episodeElements = document.select("div.seasons-tab-content a.mini-poster")
            for (a in episodeElements) {
                val epUrl = fixUrl(a.attr("href"))
                val epName = a.selectFirst("h4.mini-poster-title")?.text()?.trim() ?: ""
                
                val seasonMatch = Regex("(\\d+)\\.\\s*Sezon").find(epName)
                val episodeMatch = Regex("(\\d+)\\.\\s*Bölüm").find(epName)
                
                val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(epUrl) {
                        this.name = epName
                        this.season = seasonNum
                        this.episode = episodeNum
                    }
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = description
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
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
        val document = app.get(
            data,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to mainUrl,
                "Origin" to mainUrl
            )
        ).document

        val iframeUrl = document.selectFirst("iframe")?.attr("data-src") 
            ?: document.selectFirst("iframe")?.attr("src")

        if (iframeUrl != null) {
            val iframeDoc = app.get(fixUrl(iframeUrl), headers = mapOf("User-Agent" to userAgent)).text
            val contentUrl = Regex("\"contentUrl\":\\s*\"([^\"]+)\"").find(iframeDoc)?.groupValues?.get(1)
                ?: Regex("(https?://[^\"']+\\.m3u8[^\"']*)").find(iframeDoc)?.groupValues?.get(1)

            if (contentUrl != null) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = contentUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                return true
            }
        }

        val videoIds = mutableSetOf<String>()
        
        document.select("[data-video]").forEach { 
            val id = it.attr("data-video")
            if (id.isNotBlank()) videoIds.add(id)
        }

        for (videoId in videoIds) {
            try {
                val response = app.get(
                    "$mainUrl/video/$videoId/",
                    headers = mapOf(
                        "X-Requested-With" to "fetch",
                        "Content-Type" to "application/json",
                        "User-Agent" to userAgent,
                        "Referer" to data,
                        "Origin" to mainUrl
                    )
                ).text

                if (response.contains("\"success\":true")) {
                    val iframeUrl2 = Regex("\"data-src\":\"([^\"]+)\"").find(response)?.groupValues?.get(1)
                        ?: Regex("\"src\":\"([^\"]+)\"").find(response)?.groupValues?.get(1)

                    if (iframeUrl2 != null) {
                        val iframeDoc = app.get(fixUrl(iframeUrl2), headers = mapOf("User-Agent" to userAgent)).text
                        val contentUrl = Regex("\"contentUrl\":\\s*\"([^\"]+)\"").find(iframeDoc)?.groupValues?.get(1)
                            ?: Regex("(https?://[^\"']+\\.m3u8[^\"']*)").find(iframeDoc)?.groupValues?.get(1)

                        if (contentUrl != null) {
                            callback.invoke(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - Kaynak ${videoIds.indexOf(videoId) + 1}",
                                    url = contentUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = mainUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) { }
        }

        return videoIds.isNotEmpty()
    }
}
