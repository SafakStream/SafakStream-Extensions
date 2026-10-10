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
        val document = app.get(url, headers = HEADERS).document
        return document.select("a[data-dizipal-pageloader]")
            .filter { it.selectFirst("img.lazyload, img[data-src]") != null }
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = HEADERS).document
        
        // TÜM JSON-LD'leri al, içlerinden TVSeries/Movie olanı bul
        val jsonLdScripts = document.select("script[type=application/ld+json]")
        var jsonObj: JSONObject? = null
        
        for (script in jsonLdScripts) {
            try {
                val parsed = JSONObject(script.data())
                val parsedType = parsed.optString("@type")
                if (parsedType == "TVSeries" || parsedType == "Movie" || parsedType == "TVEpisode") {
                    jsonObj = parsed
                    break
                }
            } catch (e: Exception) {
                // Parse edilemeyen JSON-LD'yi atla
            }
        }
        
        // JSON-LD yoksa HTML'den çek
        if (jsonObj == null) {
            val title = document.selectFirst("h1")?.text() ?: return null
            val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            val plot = document.selectFirst("meta[property=og:description]")?.attr("content")
            
            val type = when {
                url.contains("/movies/") -> TvType.Movie
                else -> TvType.TvSeries
            }
            
            return newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
        
        val json = jsonObj
        
        // @type string veya array olabilir
        val typeRaw = json.opt("@type")
        val type = when (typeRaw) {
            is String -> typeRaw
            is JSONArray -> {
                var found = "TVSeries"
                for (i in 0 until typeRaw.length()) {
                    val t = typeRaw.optString(i)
                    if (t == "TVSeries" || t == "Movie" || t == "TVEpisode") {
                        found = t
                        break
                    }
                }
                found
            }
            else -> "TVSeries"
        }
        
        // name string veya array olabilir
        val nameRaw = json.opt("name")
        val title = when (nameRaw) {
            is String -> nameRaw
            is JSONArray -> if (nameRaw.length() > 0) nameRaw.optString(0) else ""
            else -> ""
        }.ifBlank {
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
            val seasonsRaw = json.opt("containsSeason")
            
            // containsSeason array veya object olabilir!
            val seasonsList = mutableListOf<JSONObject>()
            when (seasonsRaw) {
                is JSONArray -> {
                    for (i in 0 until seasonsRaw.length()) {
                        seasonsRaw.optJSONObject(i)?.let { seasonsList.add(it) }
                    }
                }
                is JSONObject -> {
                    seasonsList.add(seasonsRaw)
                }
            }
            
            for ((i, season) in seasonsList.withIndex()) {
                val seasonNum = season.optInt("seasonNumber", i + 1)
                val epsRaw = season.opt("episode")
                
                val epsList = mutableListOf<JSONObject>()
                when (epsRaw) {
                    is JSONArray -> {
                        for (j in 0 until epsRaw.length()) {
                            epsRaw.optJSONObject(j)?.let { epsList.add(it) }
                        }
                    }
                    is JSONObject -> {
                        epsList.add(epsRaw)
                    }
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
        val document = app.get(data, headers = HEADERS).document
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")

        if (!iframeSrc.isNullOrBlank()) {
            loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            return true
        }

        return false
    }
}
