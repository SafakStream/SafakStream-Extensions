package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class DiziSol : MainAPI() {
    override var mainUrl = "https://dizisol.com"
    override var name = "DiziSol"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/diziler" to "Diziler",
        "/filmler" to "Filmler",
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl${request.data}"
        val html = try { app.get(url, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return newHomePageResponse(request.name, emptyList()) }
        val ssrJson = extractReactSsr(html) ?: return newHomePageResponse(request.name, emptyList())
        val isMovie = request.data.contains("film")
        val items = extractResults(ssrJson, isMovie)
        return newHomePageResponse(request.name, items, hasNext = false)
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
                val posterUrl = when {
                    poster == null -> null
                    poster.startsWith("http") -> poster
                    else -> "https://image.tmdb.org/t/p/w500$poster"
                }
                val isMovie = type == "movie"
                
                val finalUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?type=$type"

                if (isMovie) {
                    newMovieSearchResponse(title, finalUrl, TvType.Movie) { this.posterUrl = posterUrl; this.year = year }
                } else {
                    newTvSeriesSearchResponse(title, finalUrl, TvType.TvSeries) { this.posterUrl = posterUrl; this.year = year }
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
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
            
            val overview = safeStr(o, "overview", "aiOverview")
            val year = o.optInt("year", 0).takeIf { it > 0 }
            val rating = o.optDouble("rating", 0.0).takeIf { it > 0 }?.toInt()
            val poster = o.optString("poster").ifBlank { null }
            val genres = o.optString("genres").ifBlank { null }
            
            val finalIsTv = mediaType == "tv" || isTvByUrl

            if (finalIsTv) {
                val episodes = loadEpisodesForSeries(tmdbId)
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster; this.year = year; this.plot = overview
                    this.tags = genres?.split(",")?.map { it.trim() } ?: emptyList()
                    this.score = rating?.let { Score.from10(it.toDouble()) }
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster; this.year = year; this.plot = overview
                    this.tags = genres?.split(",")?.map { it.trim() } ?: emptyList()
                    this.score = rating?.let { Score.from10(it.toDouble()) }
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

            // Ana kaynak
            if (mainM3u8 != null) {
                // Yönlendirmeyi çöz
                val resolvedUrl = resolveStreamUrl(mainM3u8)
                callback(newExtractorLink(
                    source = "DiziSol (Ana)",
                    name = "DiziSol Ana",
                    url = resolvedUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = videoHeaders
                    this.quality = Qualities.Unknown.value
                })
                added = true
            }

            // Ek kaynaklar
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
                            source = "DiziSol ($provider)",
                            name = "DiziSol $provider",
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

    // m3u8 URL'sini takip ederek gerçek URL'yi al
    private suspend fun resolveStreamUrl(url: String): String {
        return try {
            val response = app.get(url, headers = videoHeaders, allowRedirects = true)
            val finalUrl = response.url
            Log.d("DiziSol", "resolveStreamUrl: $url -> $finalUrl")
            // Eğer sonuç .m3u8 ile bitiyorsa veya bir playlist içeriyorsa, son URL'yi döndür
            if (finalUrl.contains(".m3u8") || finalUrl != url) {
                finalUrl
            } else {
                // Aynı URL'ye döndüyse, içeriği kontrol et
                val body = response.text
                if (body.startsWith("#EXTM3U")) {
                    url
                } else {
                    // Base64 m3u8 olabilir (s4.dizisol.com/?m3u8=...)
                    val decoded = decodeM3u8IfBase64(body)
                    if (decoded != null) decoded else url
                }
            }
        } catch (e: Exception) {
            Log.e("DiziSol", "resolveStreamUrl hata: ${e.message}")
            url
        }
    }

    // Eğer m3u8 içeriği base64 ise decode et, içindeki m3u8 URL'sini al
    private fun decodeM3u8IfBase64(content: String): String? {
        return try {
            if (content.startsWith("#EXTM3U")) return null
            val decoded = String(android.util.Base64.decode(content.trim(), android.util.Base64.DEFAULT))
            if (decoded.startsWith("#EXTM3U")) {
                // İçindeki ilk m3u8 URL'sini bul (eğer varsa)
                val lines = decoded.lines()
                lines.firstOrNull { it.contains(".m3u8") || it.contains(".mp4") }
            } else null
        } catch (e: Exception) { null }
    }

    private fun extractReactSsr(html: String): String? {
        val marker = "<script id=\"__react_ssr__\" type=\"application/json\">"
        val start = html.indexOf(marker)
        if (start == -1) return null
        val contentStart = start + marker.length
        val end = html.indexOf("</script>", contentStart)
        if (end == -1) return null
        return html.substring(contentStart, end)
    }

    private fun extractResults(ssrJson: String, isMovie: Boolean): List<SearchResponse> {
        return try {
            val root = JSONObject(ssrJson)
            val queries = root.optJSONObject("state")?.optJSONArray("queries") ?: return emptyList()
            for (i in 0 until queries.length()) {
                val q = queries.getJSONObject(i)
                val data = q.optJSONObject("state")?.optJSONObject("data") ?: continue
                val resultsArr = data.optJSONArray("results") ?: continue
                if (resultsArr.length() == 0) continue
                
                return (0 until resultsArr.length()).mapNotNull { idx ->
                    try {
                        val o = resultsArr.getJSONObject(idx)
                        val tmdbId = o.optInt("id", 0)
                        if (tmdbId == 0) return@mapNotNull null
                        
                        val title = safeStr(o, "titleTr", "name", "title", "original_name") ?: "İçerik #$tmdbId"
                        val year = o.optString("first_air_date").take(4).toIntOrNull()
                            ?: o.optString("release_date").take(4).toIntOrNull()
                        val poster = o.optString("poster_path").ifBlank { null }
                        val typeStr = if (isMovie) "movie" else "tv"
                        val finalUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?type=$typeStr"

                        if (isMovie) {
                            newMovieSearchResponse(title, finalUrl, TvType.Movie) {
                                this.posterUrl = poster
                                this.year = year
                            }
                        } else {
                            newTvSeriesSearchResponse(title, finalUrl, TvType.TvSeries) {
                                this.posterUrl = poster
                                this.year = year
                            }
                        }
                    } catch (e: Exception) { null }
                }
            }
            emptyList()
        } catch (e: Exception) { emptyList() }
    }
}
