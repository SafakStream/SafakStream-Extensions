package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class SafakStreamCinema : MainAPI() {
    override var mainUrl = "https://dizisol.com"
    override var name = "SafakStream Cinema"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "top10" to "Bugünün En İyi 10'u",
        "/diziler" to "Diziler",
        "/filmler" to "Filmler",
        "turkishTV" to "Türk Dizileri",
        "turkishMovies" to "Türk Filmleri",
        "random" to "🎲 Rastgele",
    )

    private val ua = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val videoHeaders = mapOf(
        "Referer" to "https://dizisol.com/",
        "Origin" to "https://dizisol.com",
        "User-Agent" to ua
    )

    private fun safeStr(o: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!o.has(key) || o.isNull(key)) continue
            val v = o.optString(key, "").trim()
            if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) return v
        }
        return null
    }

    private fun posterUrl(path: String?): String? = when {
        path == null -> null
        path.startsWith("http") -> path
        else -> "https://image.tmdb.org/t/p/w500$path"
    }

    private fun cleanOverview(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var t: String = text
        t = t.replace(Regex("(?i)dizisol\\.com[^\\s]*"), "")
        val mdIdx = t.indexOf("###")
        if (mdIdx > 0) t = t.substring(0, mdIdx)
        t = t.replace("**", "")
        for (seo in listOf("nasil bir film ve kimler izlemeli", "nasıl bir film ve kimler izlemeli", "konusu nedir?", "konusu nedir")) {
            val idx = t.indexOf(seo, ignoreCase = true)
            if (idx > 0) {
                t = t.substring(0, idx)
                break
            }
        }
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length > 1200) t = t.substring(0, 1200) + "..."
        return t.ifBlank { null }
    }

    private fun decodeHtmlEntities(s: String): String = s
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private fun extractReactSsr(html: String): String? {
        val marker = "<script id=\"__react_ssr__\" type=\"application/json\">"
        val start = html.indexOf(marker)
        if (start == -1) return null
        val contentStart = start + marker.length
        val end = html.indexOf("</script>", contentStart)
        if (end == -1) return null
        return html.substring(contentStart, end)
    }

    private fun extractTrailerKeyFromJson(data: JSONObject): String? {
        return try {
            val videos = data.optJSONObject("videos") ?: return null
            val results = videos.optJSONArray("results") ?: return null
            val priority = listOf("Trailer", "Teaser", "Clip")
            for (pType in priority) {
                for (j in 0 until results.length()) {
                    val v = results.getJSONObject(j)
                    if (v.optString("site") == "YouTube" && v.optString("type") == pType) {
                        return v.optString("key")
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val items = when (request.data) {
                "top10" -> parseTop10()
                "turkishTV", "turkishMovies" -> parseTmdbRaf(request.data)
                "random" -> parseRandom()
                else -> parseLibraryPage(request.data)
            }
            newHomePageResponse(request.name, items)
        } catch (e: Exception) {
            Log.e("SafakStreamCinema", "getMainPage hata (${request.data}): ${e.message}", e)
            newHomePageResponse(request.name, emptyList())
        }
    }

    private suspend fun parseTop10(): List<SearchResponse> {
        val html = app.get(mainUrl, headers = mapOf("User-Agent" to ua)).text
        val idx = html.indexOf("Bugünün En İyi")
        if (idx == -1) return emptyList()
        val section = html.substring(idx, minOf(html.length, idx + 15000))
        val pattern = Regex("""<a[^>]*href="(/film/[^"]+)"[^>]*>.*?<img[^>]*src="([^"]+)"[^>]*alt="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(section).take(10).mapNotNull { match ->
            try {
                val slug = match.groupValues[1]
                val poster = match.groupValues[2]
                val rawTitle = match.groupValues[3]
                val title = decodeHtmlEntities(rawTitle.removeSuffix(" izle afişi").removeSuffix(" izle").trim())
                newMovieSearchResponse(title, "$mainUrl$slug", TvType.Movie) {
                    this.posterUrl = poster
                }
            } catch (e: Exception) { null }
        }.toList()
    }

    private suspend fun parseTmdbRaf(rafName: String): List<SearchResponse> {
        val html = app.get(mainUrl, headers = mapOf("User-Agent" to ua)).text
        val ssrJson = extractReactSsr(html) ?: return emptyList()
        val isMovie = rafName == "turkishMovies"
        return try {
            val root = JSONObject(ssrJson)
            val queries = root.optJSONObject("state")?.optJSONArray("queries") ?: return emptyList()
            for (i in 0 until queries.length()) {
                val q = queries.getJSONObject(i)
                val qKey = q.optJSONArray("queryKey") ?: continue
                if (qKey.length() >= 2 && qKey.optString(0) == "tmdb" && qKey.optString(1) == rafName) {
                    val data = q.optJSONObject("state")?.optJSONObject("data") ?: continue
                    val resultsArr = data.optJSONArray("results") ?: continue
                    return (0 until resultsArr.length()).mapNotNull { idx ->
                        try {
                            val o = resultsArr.getJSONObject(idx)
                            val tmdbId = o.optInt("id", 0)
                            if (tmdbId == 0) return@mapNotNull null
                            val title = safeStr(o, "titleTr", "name", "title", "original_name") ?: "İçerik #$tmdbId"
                            val year = o.optString("first_air_date").take(4).toIntOrNull()
                                ?: o.optString("release_date").take(4).toIntOrNull()
                            val poster = posterUrl(o.optString("poster_path").ifBlank { null })
                            val typeStr = if (isMovie) "movie" else "tv"
                            val finalUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?type=$typeStr"
                            if (isMovie) {
                                newMovieSearchResponse(title, finalUrl, TvType.Movie) { this.posterUrl = poster; this.year = year }
                            } else {
                                newTvSeriesSearchResponse(title, finalUrl, TvType.TvSeries) { this.posterUrl = poster; this.year = year }
                            }
                        } catch (e: Exception) { null }
                    }
                }
            }
            emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun parseLibraryPage(pagePath: String): List<SearchResponse> {
        val url = "$mainUrl$pagePath"
        val html = try { app.get(url, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return emptyList() }
        val isMovie = pagePath.contains("film")
        val prefix = if (isMovie) "/film/" else "/dizi/"
        val pattern = Regex("""<a[^>]*href="($prefix[^"]+)"[^>]*>.*?<img[^>]*src="([^"]+)"[^>]*alt="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        val seen = mutableSetOf<String>()
        val results = mutableListOf<SearchResponse>()
        for (match in pattern.findAll(html)) {
            if (results.size >= 30) break
            try {
                val slug = match.groupValues[1]
                if (!seen.add(slug)) continue
                val poster = match.groupValues[2]
                val rawTitle = match.groupValues[3]
                val title = decodeHtmlEntities(rawTitle.removeSuffix(" izle afişi").removeSuffix(" izle").removeSuffix(" afişi").trim())
                if (isMovie) {
                    results.add(newMovieSearchResponse(title, "$mainUrl$slug", TvType.Movie) { this.posterUrl = poster })
                } else {
                    results.add(newTvSeriesSearchResponse(title, "$mainUrl$slug", TvType.TvSeries) { this.posterUrl = poster })
                }
            } catch (e: Exception) { }
        }
        return results
    }

    private suspend fun parseRandom(): List<SearchResponse> {
        val allItems = mutableListOf<SearchResponse>()
        try {
            val diziHtml = app.get("$mainUrl/diziler", headers = mapOf("User-Agent" to ua)).text
            val pattern = Regex("""<a[^>]*href="(/dizi/[^"]+)"[^>]*>.*?<img[^>]*src="([^"]+)"[^>]*alt="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            pattern.findAll(diziHtml).take(20).forEach { match ->
                try {
                    val slug = match.groupValues[1]
                    val poster = match.groupValues[2]
                    val title = decodeHtmlEntities(match.groupValues[3].removeSuffix(" izle afişi").removeSuffix(" izle").trim())
                    allItems.add(newTvSeriesSearchResponse(title, "$mainUrl$slug", TvType.TvSeries) { this.posterUrl = poster })
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        try {
            val filmHtml = app.get("$mainUrl/filmler", headers = mapOf("User-Agent" to ua)).text
            val pattern = Regex("""<a[^>]*href="(/film/[^"]+)"[^>]*>.*?<img[^>]*src="([^"]+)"[^>]*alt="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            pattern.findAll(filmHtml).take(20).forEach { match ->
                try {
                    val slug = match.groupValues[1]
                    val poster = match.groupValues[2]
                    val title = decodeHtmlEntities(match.groupValues[3].removeSuffix(" izle afişi").removeSuffix(" izle").trim())
                    allItems.add(newMovieSearchResponse(title, "$mainUrl$slug", TvType.Movie) { this.posterUrl = poster })
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        return allItems.shuffled().take(15)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/api/movies/search?q=$encoded"
        val json = try { app.get(url, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return emptyList() }
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val tmdbId = o.optInt("tmdbId", 0)
                if (tmdbId == 0) return@mapNotNull null
                val title = safeStr(o, "titleTr", "title", "name", "original_name") ?: "İçerik #$tmdbId"
                val year = o.optInt("year", 0).takeIf { it > 0 }
                val type = o.optString("type", "movie")
                val poster = o.optString("poster").ifBlank { null }
                val isMovie = type == "movie"
                val finalUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?type=$type"
                if (isMovie) {
                    newMovieSearchResponse(title, finalUrl, TvType.Movie) { this.posterUrl = posterUrl(poster); this.year = year }
                } else {
                    newTvSeriesSearchResponse(title, finalUrl, TvType.TvSeries) { this.posterUrl = posterUrl(poster); this.year = year }
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d("SafakStreamCinema", "load ÇAĞRILDI: $url")
        if (url.contains("/film/") || url.contains("/dizi/")) {
            return loadFromPage(url)
        }
        
        // API URL'si ise, önce slug bulmayı dene (hızlı SSR için)
        if (url.contains("/api/movies/by-tmdb/")) {
            try {
                val tmdbId = url.substringAfterLast("/").substringBefore("?").toIntOrNull()
                val typeParam = url.substringAfter("?type=", "").substringBefore("&")
                val isTvApi = typeParam == "tv"
                if (tmdbId != null) {
                    val apiJson = app.get(url, headers = mapOf("User-Agent" to ua)).text
                    if (!apiJson.startsWith("<")) {
                        val o = JSONObject(apiJson)
                        val titleForSearch = safeStr(o, "titleTr", "title", "name", "original_name")
                        if (titleForSearch != null) {
                            val searchUrl = "$mainUrl/api/movies/search?q=" + URLEncoder.encode(titleForSearch, "UTF-8")
                            val searchJson = try { app.get(searchUrl, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { null }
                            if (searchJson != null && !searchJson.startsWith("<") && searchJson != "[]") {
                                val arr = JSONArray(searchJson)
                                for (i in 0 until arr.length()) {
                                    val s = arr.getJSONObject(i)
                                    if (s.optInt("tmdbId", 0) == tmdbId) {
                                        val slug = s.optString("slug").ifBlank { null }
                                        if (slug != null) {
                                            val pageUrl = "$mainUrl/" + (if (isTvApi) "dizi/" else "film/") + slug
                                            Log.d("SafakStreamCinema", "Slug bulundu: $pageUrl")
                                            val result = loadFromPage(pageUrl)
                                            if (result != null) return result
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SafakStreamCinema", "Slug arama hatası: ${e.message}")
            }
        }
        val typeParam = url.substringAfter("?type=", "").substringBefore("&")
        val isTvByUrl = typeParam == "tv"
        val tmdbId = url.substringAfterLast("/").substringBefore("?").toIntOrNull() ?: return null

        var json: String? = null
        try {
            json = app.get(url, headers = mapOf("User-Agent" to ua)).text
            if (json.startsWith("<!DOCTYPE") || json.startsWith("<html")) json = null
        } catch (e: Exception) { json = null }

        if (json == null || json.isBlank()) {
            val pageUrl = if (isTvByUrl) "$mainUrl/api/movies/by-tmdb/$tmdbId?season=0&episode=0" else "$mainUrl/api/movies/by-tmdb/$tmdbId"
            json = try {
                val r = app.get(pageUrl, headers = mapOf("User-Agent" to ua)).text
                if (r.startsWith("<!DOCTYPE") || r.startsWith("<html")) null else r
            } catch (e: Exception) { null }
        }
        if (json == null || json.isBlank()) return null

        return try {
            val o = JSONObject(json)
            val mediaType = o.optString("mediaType", if (isTvByUrl) "tv" else "movie")
            val title = safeStr(o, "titleTr", "title", "name", "original_name", "original_title")
                ?: if (isTvByUrl) "Dizi #$tmdbId" else "Film #$tmdbId"
            val overview = cleanOverview(safeStr(o, "aiOverview", "overview"))
            val year = o.optInt("year", 0).takeIf { it > 0 }
            val rating = o.optDouble("rating", 0.0).takeIf { it > 0 }?.toInt()
            val poster = o.optString("poster").ifBlank { null }
            val genres = o.optString("genres").ifBlank { null }
            val finalIsTv = mediaType == "tv" || isTvByUrl
            val browserUrl = if (finalIsTv) "https://www.themoviedb.org/tv/$tmdbId" else "https://www.themoviedb.org/movie/$tmdbId"

            if (finalIsTv) {
                val episodes = loadEpisodesForSeries(tmdbId)
                newTvSeriesLoadResponse(title, browserUrl, TvType.TvSeries, episodes) {
                    this.posterUrl = poster; this.year = year; this.plot = overview
                    this.tags = genres?.split(",")?.map { it.trim() } ?: emptyList()
                    this.score = rating?.let { Score.from10(it.toDouble()) }
                }
            } else {
                newMovieLoadResponse(title, browserUrl, TvType.Movie, url) {
                    this.posterUrl = poster; this.year = year; this.plot = overview
                    this.tags = genres?.split(",")?.map { it.trim() } ?: emptyList()
                    this.score = rating?.let { Score.from10(it.toDouble()) }
                }
            }
        } catch (e: Exception) { null }
    }

    private suspend fun loadFromPage(url: String): LoadResponse? {
        val isTv = url.contains("/dizi/")
        Log.d("SafakStreamCinema", "loadFromPage ÇAĞRILDI: $url (isTv=$isTv)")
        val html = try { app.get(url, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return null }
        val ssrJson = extractReactSsr(html) ?: return null
        return try {
            val root = JSONObject(ssrJson)
            val queries = root.optJSONObject("state")?.optJSONArray("queries") ?: return null
            var tmdbId = 0
            var title: String? = null
            var overview: String? = null
            var year: Int? = null
            var rating: Int? = null
            var poster: String? = null
            var trailerUrl: String? = null
            val episodesFromSsr = mutableListOf<Episode>()

            for (i in 0 until queries.length()) {
                val q = queries.getJSONObject(i)
                val qKey = q.optJSONArray("queryKey") ?: continue
                if (qKey.length() >= 3 && qKey.optString(0) == "tmdb" && qKey.optString(1) == "details") {
                    val data = q.optJSONObject("state")?.optJSONObject("data") ?: continue
                    tmdbId = data.optInt("id", 0)
                    title = safeStr(data, "titleTr", "name", "title", "original_name", "original_title")
                    overview = cleanOverview(safeStr(data, "aiOverview", "overview"))
                    val airDate = data.optString("first_air_date").ifBlank { data.optString("release_date") }
                    year = airDate.take(4).toIntOrNull()
                    val vote = data.optDouble("vote_average", 0.0)
                    rating = vote.takeIf { it > 0 }?.toInt()
                    poster = posterUrl(data.optString("poster_path").ifBlank { null })
                    val key = extractTrailerKeyFromJson(data)
                    if (key != null) trailerUrl = "https://www.youtube.com/watch?v=$key"
                    
                    // SSR'daki supplement alanından bölümleri çıkar (tek istek, hızlı!)
                    if (isTv) {
                        val supplement: String = root.optJSONObject("initial")?.optString("supplement")?.ifBlank { null } ?: ""
                        if (supplement.isNotEmpty()) {
                            val epPattern = Regex("""href="(/dizi/[^"]+/(\d+)-sezon-(\d+)-bolum)"[^>]*>([^<]+)</a>""")
                            epPattern.findAll(supplement).forEach { m ->
                                val season = m.groupValues[2].toIntOrNull() ?: 0
                                val episode = m.groupValues[3].toIntOrNull() ?: 0
                                val epApiUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?season=$season&episode=$episode"
                                episodesFromSsr.add(newEpisode(epApiUrl) {
                                    this.name = "$episode. Bölüm"
                                    this.season = season
                                    this.episode = episode
                                })
                            }
                        }
                    }
                    break
                }
            }

            if (tmdbId == 0) return null
            val apiUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?type=" + (if (isTv) "tv" else "movie")
            val finalTitle = title ?: "İçerik #$tmdbId"
            val browserUrl = if (isTv) "https://www.themoviedb.org/tv/$tmdbId" else "https://www.themoviedb.org/movie/$tmdbId"

            if (isTv) {
                Log.d("SafakStreamCinema", "episodesFromSsr boyut: ${episodesFromSsr.size}")
                val episodes = if (episodesFromSsr.isNotEmpty()) episodesFromSsr else loadEpisodesForSeries(tmdbId)
                Log.d("SafakStreamCinema", "Kullanılan bölüm sayısı: ${episodes.size}")
                newTvSeriesLoadResponse(finalTitle, browserUrl, TvType.TvSeries, episodes) {
                    this.posterUrl = poster; this.year = year; this.plot = overview
                    this.score = rating?.let { Score.from10(it.toDouble()) }
                    addTrailer(trailerUrl)
                }
            } else {
                newMovieLoadResponse(finalTitle, browserUrl, TvType.Movie, apiUrl) {
                    this.posterUrl = poster; this.year = year; this.plot = overview
                    this.score = rating?.let { Score.from10(it.toDouble()) }
                    addTrailer(trailerUrl)
                }
            }
        } catch (e: Exception) { null }
    }

    private suspend fun loadEpisodesForSeries(tmdbId: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        var season = 1
        while (season <= 20) {
            val seasonUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?season=$season&episode=1"
            val seasonJson = try { app.get(seasonUrl, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { break }
            if (seasonJson.isBlank() || seasonJson.startsWith("<")) break
            val seasonObj = try { JSONObject(seasonJson) } catch (e: Exception) { break }
            if (seasonObj.optInt("id", 0) == 0) break
            var ep = 1
            var foundInSeason = false
            while (ep <= 100) {
                val epUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?season=$season&episode=$ep"
                val epJson = try { app.get(epUrl, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { break }
                if (epJson.isBlank() || epJson.startsWith("<")) break
                val epObj = try { JSONObject(epJson) } catch (e: Exception) { break }
                if (epObj.optInt("id", 0) == 0) break
                val epTitle = "$ep. Bölüm"
                val epOverview = epObj.optString("overview").ifBlank { "" }
                val epPoster = epObj.optString("backdrop").ifBlank { epObj.optString("poster") }.ifBlank { null }
                episodes.add(newEpisode(epUrl) {
                    this.name = epTitle
                    this.season = season
                    this.episode = ep
                    this.posterUrl = epPoster
                    this.description = epOverview
                })
                foundInSeason = true
                ep++
            }
            if (!foundInSeason) break
            season++
        }
        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = try { app.get(data, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return false }
        return try {
            val o = JSONObject(json)
            val mainM3u8 = o.optString("m3u8Url").ifBlank { null }
            val subTr = o.optString("subtitleTr").ifBlank { null }
            val subEn = o.optString("subtitleEn").ifBlank { null }
            subTr?.let { subtitleCallback(SubtitleFile("Türkçe", it)) }
            subEn?.let { subtitleCallback(SubtitleFile("English", it)) }
            var added = false
            if (mainM3u8 != null) {
                val resolvedUrl = resolveStreamUrl(mainM3u8)
                callback(newExtractorLink(
                    source = "SafakStream (Ana)",
                    name = "SafakStream Ana",
                    url = resolvedUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = videoHeaders
                    this.quality = Qualities.Unknown.value
                })
                added = true
            }
            val sourcesArr = o.optJSONArray("sources")
            if (sourcesArr != null) {
                for (i in 0 until sourcesArr.length()) {
                    val src = sourcesArr.getJSONObject(i)
                    val provider = src.optString("provider", "ek-$i")
                    val m3u8 = src.optString("m3u8Url").ifBlank { null }
                    val sTr = src.optString("subtitleTr").ifBlank { null }
                    val sEn = src.optString("subtitleEn").ifBlank { null }
                    sTr?.let { subtitleCallback(SubtitleFile("Türkçe ($provider)", it)) }
                    sEn?.let { subtitleCallback(SubtitleFile("English ($provider)", it)) }
                    if (m3u8 != null) {
                        val resolved = resolveStreamUrl(m3u8)
                        callback(newExtractorLink(
                            source = "SafakStream ($provider)",
                            name = "SafakStream $provider",
                            url = resolved,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.headers = videoHeaders
                            this.quality = Qualities.Unknown.value
                        })
                        added = true
                    }
                }
            }
            added
        } catch (e: Exception) { false }
    }

    private suspend fun resolveStreamUrl(url: String): String {
        return try {
            val response = app.get(url, headers = videoHeaders, allowRedirects = true)
            val finalUrl = response.url
            if (finalUrl.contains(".m3u8") || finalUrl != url) {
                finalUrl
            } else {
                val body = response.text
                if (body.startsWith("#EXTM3U")) url
                else {
                    val decoded = decodeM3u8IfBase64(body)
                    if (decoded != null) decoded else url
                }
            }
        } catch (e: Exception) { url }
    }

    private fun decodeM3u8IfBase64(content: String): String? {
        return try {
            if (content.startsWith("#EXTM3U")) return null
            val decoded = String(android.util.Base64.decode(content.trim(), android.util.Base64.DEFAULT))
            if (decoded.startsWith("#EXTM3U")) {
                decoded.lines().firstOrNull { it.contains(".m3u8") || it.contains(".mp4") }
            } else null
        } catch (e: Exception) { null }
    }
}
