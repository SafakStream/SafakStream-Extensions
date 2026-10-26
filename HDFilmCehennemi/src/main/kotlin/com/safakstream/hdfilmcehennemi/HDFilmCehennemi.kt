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

    override val mainPage = mainPageOf(
        "category/film-izle-2" to "Son Eklenen Filmler",
        "yabancidiziizle-5" to "Son Eklenen Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) "$mainUrl/${request.data}" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url).document
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
                "Content-Type" to "application/json"
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
        val document = app.get(url).document
        val htmlText = document.html()

        val jsonLdRegex = Regex("<script type=\"application/ld\\+json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
        val jsonLd = jsonLdRegex.find(htmlText)?.groupValues?.get(1)

        var title = document.selectFirst("h1")?.text()?.trim() ?: "Bilinmeyen Film"
        var posterUrl: String? = null
        var description: String? = null
        var year: Int? = null
        val isSeries = url.contains("/dizi/")

        if (jsonLd != null) {
            val descRegex = Regex("\"description\":\"(.*?)\"")
            description = descRegex.find(jsonLd)?.groupValues?.get(1)?.replace("\\\"", "\"")

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

            if (jsonLd != null) {
                val seasonBlocks = jsonLd.split("\"@type\":\"TVSeason\"")
                var seasonIndex = 1
                
                for (block in seasonBlocks.drop(1)) {
                    val seasonNum = Regex("\"seasonNumber\":\\s*\"(\\d+)\"").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: seasonIndex
                    
                    val epBlocks = block.split("\"@type\":\"TVEpisode\"")
                    for (epBlock in epBlocks.drop(1)) {
                        val epNum = Regex("\"episodeNumber\":\\s*\"(\\d+)\"").find(epBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val epName = Regex("\"name\":\\s*\"([^\"]+)\"").find(epBlock)?.groupValues?.get(1) ?: "Bölüm $epNum"
                        val epUrl = Regex("\"url\":\\s*\"([^\"]+)\"").find(epBlock)?.groupValues?.get(1) ?: continue
                        
                        episodes.add(
                            newEpisode(fixUrl(epUrl)) {
                                this.name = epName
                                this.season = seasonNum
                                this.episode = epNum
                            }
                        )
                    }
                    seasonIndex++
                }
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
                "Referer" to mainUrl,
                "Origin" to mainUrl,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        ).document

        // 1. Doğrudan iframe var mı?
        val iframeUrl = document.selectFirst("iframe")?.attr("data-src") 
            ?: document.selectFirst("iframe")?.attr("src")

        if (iframeUrl != null) {
            val iframeDoc = app.get(fixUrl(iframeUrl)).text
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

        // 2. Sayfadaki tüm olası ID'leri topla
        val possibleIds = mutableSetOf<String>()
        
        document.select("[data-video]").forEach { 
            val id = it.attr("data-video")
            if (id.isNotBlank()) possibleIds.add(id)
        }
        
        document.select("[data-action-target]").forEach { 
            val id = it.attr("data-action-target")
            if (id.isNotBlank()) possibleIds.add(id)
        }

        Regex("\"([0-9]{4,8})\"").findAll(document.html()).forEach { 
            possibleIds.add(it.groupValues[1])
        }

        // 3. Her ID için /video/ API'sine istek at
        for (videoId in possibleIds) {
            try {
                val response = app.get(
                    "$mainUrl/video/$videoId/",
                    headers = mapOf(
                        "X-Requested-With" to "fetch",
                        "Content-Type" to "application/json",
                        "Referer" to data,
                        "Origin" to mainUrl
                    )
                ).text

                if (response.contains("\"success\":true")) {
                    val iframeUrl2 = Regex("\"data-src\":\"([^\"]+)\"").find(response)?.groupValues?.get(1)
                        ?: Regex("\"src\":\"([^\"]+)\"").find(response)?.groupValues?.get(1)

                    if (iframeUrl2 != null) {
                        val iframeDoc = app.get(fixUrl(iframeUrl2)).text
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
                }
            } catch (e: Exception) { }
        }
        return false
    }
}
