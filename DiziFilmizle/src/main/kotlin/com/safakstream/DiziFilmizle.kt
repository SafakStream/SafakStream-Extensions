package com.safakstream

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONArray
import org.jsoup.Jsoup

class DiziFilmizle : MainAPI() {
    override var mainUrl = "https://dizifilmizle.to"
    override var name = "DiziFilmizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mapper = jacksonObjectMapper()

    override val mainPage = mainPageOf(
        "movies/latest" to "Son Filmler",
        "series/latest" to "Son Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val apiUrl = "$mainUrl/api/${request.data}?page=$page&limit=30"
        Log.d("DiziFilmizle", "getMainPage: $apiUrl")
        val response = app.get(apiUrl).text

        val items = mutableListOf<SearchResponse>()
        try {
            val parsed = mapper.readValue<MovieListResponse>(response)
            parsed.movies?.forEach { m -> items.add(m.toSearchResponse(false)) }
            parsed.series?.forEach { s -> items.add(s.toSearchResponse(true)) }
        } catch (e: Exception) {
            Log.e("DiziFilmizle", "getMainPage parse hatasi: ${e.message}")
        }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val apiUrl = "$mainUrl/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        Log.d("DiziFilmizle", "search: $apiUrl")
        val response = app.get(apiUrl).text
        val items = mutableListOf<SearchResponse>()
        try {
            val parsed = mapper.readValue<SearchResponse2>(response)
            parsed.results?.forEach { r -> items.add(r.toSearchResponse()) }
        } catch (e: Exception) {
            Log.e("DiziFilmizle", "search parse hatasi: ${e.message}")
        }
        return items
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d("DiziFilmizle", "load: $url")
        val isSeries = url.contains("/dizi/")
        val slug = url.substringAfterLast("/")
        return if (isSeries) loadSeries(url, slug) else loadMovie(url, slug)
    }

    private suspend fun loadMovie(url: String, slug: String): LoadResponse? {
        val doc = app.get(url).document
        val html = doc.html()

        val searchResp = app.get("$mainUrl/api/search?q=$slug").text
        val searchParsed = try { mapper.readValue<SearchResponse2>(searchResp) } catch (e: Exception) { null }
        val matched = searchParsed?.results?.firstOrNull { it.slug == slug }

        val title = matched?.title ?: doc.selectFirst("h1")?.text() ?: slug
        val poster = matched?.posterUrl
        val year = matched?.year
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
        }
    }

    private suspend fun loadSeries(url: String, slug: String): LoadResponse? {
        val doc = app.get(url).document
        val html = doc.html()

        val searchResp = app.get("$mainUrl/api/search?q=$slug").text
        val searchParsed = try { mapper.readValue<SearchResponse2>(searchResp) } catch (e: Exception) { null }
        val matched = searchParsed?.results?.firstOrNull { it.slug == slug }

        val title = matched?.title ?: doc.selectFirst("h1")?.text() ?: slug
        val poster = matched?.posterUrl
        val year = matched?.year
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")

        val episodes = mutableListOf<Episode>()

        try {
            val idx = html.indexOf("\"seasonsWithEpisodes\":")
            if (idx != -1) {
                val startIdx = html.indexOf('[', idx)
                if (startIdx != -1) {
                    var depth = 0
                    var endIdx = -1
                    var i = startIdx
                    var inString = false
                    var escaped = false
                    while (i < html.length) {
                        val c = html[i]
                        if (escaped) { escaped = false }
                        else if (c == '\\') { escaped = true }
                        else if (c == '"') { inString = !inString }
                        else if (!inString) {
                            if (c == '[') depth++
                            else if (c == ']') {
                                depth--
                                if (depth == 0) { endIdx = i; break }
                            }
                        }
                        i++
                    }

                    if (endIdx != -1) {
                        val jsonStr = html.substring(startIdx, endIdx + 1)
                            .replace("\\\"", "\"")
                            .replace("\\/", "/")
                            .replace("\\n", " ")
                            .replace("\\r", "")

                        Log.d("DiziFilmizle", "seasonsWithEpisodes: ${jsonStr.length} char")

                        val seasonsArr = JSONArray(jsonStr)
                        for (s in 0 until seasonsArr.length()) {
                            val season = seasonsArr.getJSONObject(s)
                            val seasonNumber = season.optInt("season_number", 1)
                            val episodesArr = season.optJSONArray("episodes") ?: continue

                            for (e in 0 until episodesArr.length()) {
                                val ep = episodesArr.getJSONObject(e)
                                val epNum = ep.optInt("episode_number", e + 1)
                                val epTitle = ep.optString("title_tr").ifBlank {
                                    ep.optString("title").ifBlank { "$seasonNumber. Sezon $epNum. Bölüm" }
                                }
                                val epPoster = ep.optString("thumbnail_url").ifBlank { null }
                                val epUrl = "$mainUrl/dizi/$slug/${seasonNumber}-sezon-${epNum}-bolum"

                                episodes.add(
                                    newEpisode(epUrl) {
                                        this.name = epTitle
                                        this.season = seasonNumber
                                        this.episode = epNum
                                        this.posterUrl = epPoster ?: poster
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DiziFilmizle", "seasonsWithEpisodes parse hatasi: ${e.message}")
        }

        Log.d("DiziFilmizle", "Toplam bolum: ${episodes.size}")

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DiziFilmizle", "loadLinks: $data")

        val html = app.get(data).document.html()

        // 1. Film icin parts
        val partsRegex = Regex("""parts.{0,3}\[([\s\S]*?)\]""")
        val partsMatch = partsRegex.find(html)
        if (partsMatch != null) {
            val partsStr = "[${partsMatch.groupValues[1]}]"
                .replace("\\\"", "\"")
                .replace("\\/", "/")
            try {
                val arr = JSONArray(partsStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pUrl = obj.optString("url", "")
                    if (pUrl.isNotBlank()) {
                        loadExtractor(pUrl, data, subtitleCallback, callback)
                    }
                }
                return true
            } catch (e: Exception) {
                Log.e("DiziFilmizle", "parts parse: ${e.message}")
            }
        }

        // 2. Dizi bolumu icin embed_player_url_1
        val embedRegex = Regex("""embed_player_url_1\\?":\s*\\?"(https?:[^"\\]+)""")
        val embedMatch = embedRegex.find(html)
        if (embedMatch != null) {
            val embedUrl = embedMatch.groupValues[1].replace("\\/", "/")
            if (embedUrl.isNotBlank()) {
                Log.d("DiziFilmizle", "bolum embed: $embedUrl")
                loadExtractor(embedUrl, data, subtitleCallback, callback)
                return true
            }
        }

        // 3. Fallback: herhangi bir vidmixi embed
        val fallback = Regex("""https?://vidmixi\.com/embed/[A-Za-z0-9]+""").find(html)
        if (fallback != null) {
            Log.d("DiziFilmizle", "fallback embed: ${fallback.value}")
            loadExtractor(fallback.value, data, subtitleCallback, callback)
            return true
        }

        return false
    }

    private fun MovieItem.toSearchResponse(isSeries: Boolean): SearchResponse {
        val response = if (isSeries) {
            newTvSeriesSearchResponse(this.title ?: "?", "$mainUrl/dizi/${this.slug}", TvType.TvSeries)
        } else {
            newMovieSearchResponse(this.title ?: "?", "$mainUrl/film/${this.slug}", TvType.Movie)
        }
        response.posterUrl = this.posterUrl
        (response as? com.lagradost.cloudstream3.MovieSearchResponse)?.year = this.year
        (response as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year = this.year
        return response
    }

    private fun SeriesItem.toSearchResponse(isSeries: Boolean): SearchResponse {
        val response = newTvSeriesSearchResponse(this.title ?: "?", "$mainUrl/dizi/${this.slug}", TvType.TvSeries)
        response.posterUrl = this.posterUrl
        (response as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year = this.startYear
        return response
    }

    private fun SearchItem.toSearchResponse(): SearchResponse {
        val response = newMovieSearchResponse(this.title ?: "?", "$mainUrl/film/${this.slug}", TvType.Movie)
        response.posterUrl = this.posterUrl
        (response as? com.lagradost.cloudstream3.MovieSearchResponse)?.year = this.year
        return response
    }
}

typealias SearchResponse2 = com.safakstream.SearchResponse
