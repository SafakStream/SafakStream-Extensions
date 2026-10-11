package com.safakstream.dizipal

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

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
override val mainPage = mainPageOf(
    "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
    "$mainUrl/hd-film-izle" to "Filmler",
    "$mainUrl/anime" to "Animeler"
)

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = if (page == 1) request.data else "${request.data}/page/$page"
    val document = app.get(url).document
    val items = document.select("a[data-dizipal-pageloader]")
        .filter { it.selectFirst("img.lazyload, img[data-src]") != null }
        .mapNotNull { it.toSearchResult() }
        .distinctBy { it.url }
    return newHomePageResponse(request.name, items)
}

private fun org.jsoup.nodes.Element.toSearchResult(): SearchResponse? {
    val href = this.attr("href").takeIf { it.isNotBlank() } ?: return null
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
    val document = app.get(url).document
    return document.select("a[data-dizipal-pageloader]")
        .filter { it.selectFirst("img.lazyload, img[data-src]") != null }
        .mapNotNull { it.toSearchResult() }
        .distinctBy { it.url }
}
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val jsonLdScripts = document.select("script[type=application/ld+json]")
        var jsonObj: org.json.JSONObject? = null
        for (script in jsonLdScripts) {
            try {
                val parsed = org.json.JSONObject(script.data())
                val parsedType = parsed.optString("@type")
                if (parsedType == "TVSeries" || parsedType == "Movie" || parsedType == "TVEpisode") {
                    jsonObj = parsed
                    break
                }
            } catch (e: Exception) { }
        }
        if (jsonObj == null) return null
        val json = jsonObj
        val title = json.optString("name").ifBlank {
            document.selectFirst("h1")?.text() ?: return null
        }
        val description = json.optString("description").takeIf { it.isNotBlank() }
        val poster = json.optString("image").takeIf { it.isNotBlank() }
        val year = json.optString("datePublished").take(4).toIntOrNull()
        val type = json.optString("@type")
        val tvType = when (type) {
            "TVSeries" -> TvType.TvSeries
            "Movie" -> TvType.Movie
            else -> TvType.TvSeries
        }
        val episodes = mutableListOf<Episode>()
        if (tvType == TvType.TvSeries) {
            val seasonsRaw = json.opt("containsSeason")
            val seasonsList = mutableListOf<org.json.JSONObject>()
            when (seasonsRaw) {
                is org.json.JSONArray -> {
                    for (i in 0 until seasonsRaw.length()) {
                        seasonsRaw.optJSONObject(i)?.let { seasonsList.add(it) }
                    }
                }
                is org.json.JSONObject -> seasonsList.add(seasonsRaw)
            }
            for ((i, season) in seasonsList.withIndex()) {
                val seasonNum = season.optInt("seasonNumber", i + 1)
                val epsRaw = season.opt("episode")
                val epsList = mutableListOf<org.json.JSONObject>()
                when (epsRaw) {
                    is org.json.JSONArray -> {
                        for (j in 0 until epsRaw.length()) {
                            epsRaw.optJSONObject(j)?.let { epsList.add(it) }
                        }
                    }
                    is org.json.JSONObject -> epsList.add(epsRaw)
                }
                for ((j, ep) in epsList.withIndex()) {
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
        // Yöntem 1: Basit iframe (JS çalışmadan gelen)
        try {
            val document = app.get(data).document
            val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")
            if (!iframeSrc.isNullOrBlank() && iframeSrc.length > 10 && !iframeSrc.startsWith("about:")) {
                loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
                return true
            }
        } catch (e: Exception) {
            // iframe bulunamadı, WebView'a geç
        }

        // Yöntem 2: WebViewResolver (şifreli video için)
        return try {
            val webViewResolver = com.lagradost.cloudstream3.network.WebViewResolver(
                interceptUrl = Regex("""\.m3u8(\?.*)?$"""),
                additionalUrls = listOf(
                    Regex("""\.m3u8(\?.*)?$""")
                ),
                userAgent = null,
                useOkhttp = false,
                timeout = 20000L
            )

            val (mainRequest, additionalRequests) = webViewResolver.resolveUsingWebView(
                url = data,
                referer = data,
                method = "GET",
                requestCallBack = { request ->
                    val url = request.url.toString()
                    url.contains(".m3u8") || url.contains("master")
                }
            )

            var found = false

            // Ana istek (m3u8) bulunduysa
            mainRequest?.url?.toString()?.let { url ->
                if (url.contains(".m3u8") || url.contains("master")) {
                    callback.invoke(
                        com.lagradost.cloudstream3.utils.newExtractorLink(
                            source = this.name,
                            name = "Dizipal",
                            url = url,
                            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                        ) {
                            this.referer = data
                        }
                    )
                    found = true
                }
            }

            // Ek istekler (sadece m3u8)
            additionalRequests.forEach { req ->
                val url = req.url.toString()
                if (url.contains(".m3u8") || url.contains("master")) {
                    callback.invoke(
                        com.lagradost.cloudstream3.utils.newExtractorLink(
                            source = this.name,
                            name = "Dizipal",
                            url = url,
                            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                        ) {
                            this.referer = data
                        }
                    )
                    found = true
                }
            }

            found
        } catch (e: Exception) {
            false
        }
    }
}
