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
        var isSeries = url.contains("/dizi/")

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
        val document = app.get(data).document

        val videoIds = document.select("[data-video]").mapNotNull { it.attr("data-video").ifBlank { null } }

        if (videoIds.isEmpty()) return false

        for (videoId in videoIds) {
            val apiUrl = "$mainUrl/video/$videoId/"
            val response = app.get(
                apiUrl,
                headers = mapOf(
                    "X-Requested-With" to "fetch",
                    "Content-Type" to "application/json"
                )
            ).text

            val contentUrl = Regex("\"contentUrl\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1)
                ?: Regex("(https?://[^\"']+\\.m3u8[^\"']*)").find(response)?.groupValues?.get(1)
                ?: Regex("(https?://[^\"']+\\.mp4[^\"']*)").find(response)?.groupValues?.get(1)

            if (contentUrl != null) {
                val isM3u8 = contentUrl.contains(".m3u8")
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - Kaynak ${videoIds.indexOf(videoId) + 1}",
                        url = contentUrl,
                        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            } else {
                val iframeUrl = Regex("\"iframe\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1)
                    ?: Regex("<iframe[^>]*src=\"([^\"]+)\"").find(response)?.groupValues?.get(1)

                if (iframeUrl != null) {
                    val iframeDocument = app.get(fixUrl(iframeUrl)).text
                    val iframeContentUrl = Regex("\"contentUrl\":\\s*\"([^\"]+)\"").find(iframeDocument)?.groupValues?.get(1)
                        ?: Regex("(https?://[^\"']+\\.m3u8[^\"']*)").find(iframeDocument)?.groupValues?.get(1)

                    if (iframeContentUrl != null) {
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} - Kaynak ${videoIds.indexOf(videoId) + 1}",
                                url = iframeContentUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            }
        }

        return true
    }
}
