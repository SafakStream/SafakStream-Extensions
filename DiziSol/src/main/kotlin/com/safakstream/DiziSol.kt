package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl${request.data}"
        val html = try { app.get(url, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return newHomePageResponse(request.name, emptyList()) }
        val ssrJson = extractReactSsr(html) ?: return newHomePageResponse(request.name, emptyList())
        val tmdbIds = extractTmdbIds(ssrJson, request.data)
        val isMovie = request.data.contains("film")
        val type = if (isMovie) TvType.Movie else TvType.TvSeries

        // Her TMDb ID için detayları paralel olarak çek
        val items = coroutineScope {
            tmdbIds.map { tmdbId ->
                async {
                    try {
                        val typePath = if (isMovie) "movie" else "tv"
                        val detailUrl = "$mainUrl/api/tmdb/$typePath/$tmdbId?language=tr-TR"
                        val detailJson = app.get(detailUrl, headers = mapOf("User-Agent" to ua)).text
                        val o = JSONObject(detailJson)
                        
                        // Başlık: titleTr -> name -> title -> original_name
                        val title = o.optString("titleTr").ifBlank { 
                            o.optString("name").ifBlank { 
                                o.optString("title").ifBlank { 
                                    o.optString("original_name").ifBlank { 
                                        o.optString("original_title").ifBlank { "Bilinmeyen" } 
                                    } 
                                } 
                            } 
                        }
                        
                        val year = o.optString("first_air_date").take(4).toIntOrNull()
                            ?: o.optString("release_date").take(4).toIntOrNull()
                            ?: o.optInt("year", 0).takeIf { it > 0 }
                        
                        val posterPath = o.optString("poster_path").ifBlank { null }
                        val posterUrl = when {
                            posterPath == null -> null
                            posterPath.startsWith("http") -> posterPath
                            else -> "https://image.tmdb.org/t/p/w500$posterPath"
                        }
                        
                        val finalUrl = if (isMovie) "$mainUrl/api/movies/by-tmdb/$tmdbId" else "$mainUrl/api/movies/by-tmdb/$tmdbId?season=0&episode=0"

                        if (isMovie) {
                            newMovieSearchResponse(title, finalUrl, TvType.Movie) {
                                this.posterUrl = posterUrl
                                this.year = year
                            }
                        } else {
                            newTvSeriesSearchResponse(title, finalUrl, TvType.TvSeries) {
                                this.posterUrl = posterUrl
                                this.year = year
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DiziSol", "Ana sayfa detay hatası ($tmdbId): ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/api/movies/search?q=$encoded"
        val json = try { app.get(url, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return emptyList() }
        if (json.isBlank() || json == "[]") return emptyList()

        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val tmdbId = o.optInt("tmdbId", 0)
                if (tmdbId == 0) return@mapNotNull null
                
                // null düzeltmesi: titleTr boşsa title, o da boşsa "Bilinmeyen"
                val title = o.optString("titleTr").ifBlank { 
                    o.optString("title").ifBlank { 
                        o.optString("original_name").ifBlank { "Bilinmeyen" } 
                    } 
                }
                val year = o.optInt("year", 0).takeIf { it > 0 }
                val type = o.optString("type", "movie")
                val poster = o.optString("poster").ifBlank { null }
                val posterUrl = when {
                    poster == null -> null
                    poster.startsWith("http") -> poster
                    else -> "https://image.tmdb.org/t/p/w500$poster"
                }
                val isMovie = type == "movie"
                val finalUrl = if (isMovie) "$mainUrl/api/movies/by-tmdb/$tmdbId" else "$mainUrl/api/movies/by-tmdb/$tmdbId?season=0&episode=0"

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
        val tmdbId = url.substringAfterLast("/").substringBefore("?").toIntOrNull() ?: return null
        val json = try { app.get(url, headers = mapOf("User-Agent" to ua)).text } catch (e: Exception) { return null }

        return try {
            val o = JSONObject(json)
            val mediaType = o.optString("mediaType", "movie")
            
            // null düzeltmesi
            val title = o.optString("titleTr").ifBlank { 
                o.optString("title").ifBlank { 
                    o.optString("original_name").ifBlank { 
                        o.optString("original_title").ifBlank { "Bilinmeyen" } 
                    } 
                } 
            }
            
            val overview = o.optString("overview").ifBlank { 
                o.optString("aiOverview").ifBlank { null } 
            }
            val year = o.optInt("year", 0).takeIf { it > 0 }
            val rating = o.optDouble("rating", 0.0).takeIf { it > 0 }?.toInt()
            val poster = o.optString("poster").ifBlank { null }
            val genres = o.optString("genres").ifBlank { null }

            if (mediaType == "tv") {
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
            
            // Cloudflare için Origin, Referer ve User-Agent gerekli
            val videoHeaders = mapOf(
                "Referer" to "https://dizisol.com/",
                "Origin" to "https://dizisol.com",
                "User-Agent" to ua
            )

            if (mainM3u8 != null) {
                callback(newExtractorLink(
                    source = "DiziSol (Ana)",
                    name = "DiziSol Ana",
                    url = mainM3u8,
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
                        callback(newExtractorLink(
                            source = "DiziSol ($provider)",
                            name = "DiziSol $provider",
                            url = m3u8,
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

    private fun extractReactSsr(html: String): String? {
        val marker = "<script id=\"__react_ssr__\" type=\"application/json\">"
        val start = html.indexOf(marker)
        if (start == -1) return null
        val contentStart = start + marker.length
        val end = html.indexOf("</script>", contentStart)
        if (end == -1) return null
        return html.substring(contentStart, end)
    }

    private fun extractTmdbIds(ssrJson: String, pagePath: String): List<Int> {
        return try {
            val root = JSONObject(ssrJson)
            val queries = root.optJSONObject("state")?.optJSONArray("queries") ?: return emptyList()
            for (i in 0 until queries.length()) {
                val q = queries.getJSONObject(i)
                val qKey = q.optJSONArray("queryKey") ?: continue
                if (qKey.length() >= 2 && qKey.optString(0) == "library-tmdb-ids") {
                    val pageMatch = qKey.optString(2, "")
                    if (pageMatch == pagePath || pagePath.contains(pageMatch)) {
                        val data = q.optJSONObject("state")?.optJSONObject("data") ?: continue
                        val isMovie = pagePath.contains("film")
                        val arr = if (isMovie) data.optJSONArray("movies") else data.optJSONArray("tv")
                        if (arr != null) return (0 until arr.length()).map { arr.optInt(it, 0) }.filter { it > 0 }
                    }
                }
            }
            emptyList()
        } catch (e: Exception) { emptyList() }
    }
}
