package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject

class DiziSol : MainAPI() {
    override var mainUrl = "https://dizisol.com"
    override var name = "DiziSol"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Ana sayfa rafları: /diziler ve /filmler sayfalarından
    override val mainPage = mainPageOf(
        "/diziler" to "Diziler",
        "/filmler" to "Filmler",
    )

    private val ua = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl${request.data}"
        Log.d("DiziSol", "getMainPage: $url")
        
        val html = try {
            app.get(url, headers = mapOf("User-Agent" to ua)).text
        } catch (e: Exception) {
            Log.e("DiziSol", "getMainPage fetch hatasi: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }

        val ssrJson = extractReactSsr(html) ?: return newHomePageResponse(request.name, emptyList())

        val tmdbIds = extractTmdbIds(ssrJson, request.data)
        val titleOverrides = extractTitleOverrides(ssrJson)

        val items = tmdbIds.mapNotNull { tmdbId ->
            val override = titleOverrides[tmdbId]
            val title = override?.first ?: "TMDb #$tmdbId"
            val year = override?.second
            val isMovie = request.data.contains("film")
            val type = if (isMovie) TvType.Movie else TvType.TvSeries
            
            val finalUrl = if (isMovie) {
                "$mainUrl/api/movies/by-tmdb/$tmdbId"
            } else {
                "$mainUrl/api/movies/by-tmdb/$tmdbId?season=0&episode=0"
            }

            if (isMovie) {
                newMovieSearchResponse(title, finalUrl, TvType.Movie) {
                    this.posterUrl = null 
                    this.year = year
                }
            } else {
                newTvSeriesSearchResponse(title, finalUrl, TvType.TvSeries) {
                    this.posterUrl = null
                    this.year = year
                }
            }
        }

        return newHomePageResponse(request.name, items, hasNext = false)
    }

    // ============ SEARCH ============
    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/api/movies/search?q=$encoded"
        Log.d("DiziSol", "search: $url")
        
        val json = try {
            app.get(url, headers = mapOf("User-Agent" to ua)).text
        } catch (e: Exception) {
            Log.e("DiziSol", "search hata: ${e.message}")
            return emptyList()
        }

        if (json.isBlank() || json == "[]") return emptyList()

        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val tmdbId = o.optInt("tmdbId", 0)
                if (tmdbId == 0) return@mapNotNull null
                
                val title = o.optString("titleTr").ifBlank { o.optString("title") }
                val year = o.optInt("year", 0).takeIf { it > 0 }
                val type = o.optString("type", "movie")
                val poster = o.optString("poster").ifBlank { null }
                val posterUrl = when {
                    poster == null -> null
                    poster.startsWith("http") -> poster
                    else -> "https://image.tmdb.org/t/p/w500$poster"
                }

                val isMovie = type == "movie"
                val finalUrl = if (isMovie) {
                    "$mainUrl/api/movies/by-tmdb/$tmdbId"
                } else {
                    "$mainUrl/api/movies/by-tmdb/$tmdbId?season=0&episode=0"
                }

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
            }
        } catch (e: Exception) {
            Log.e("DiziSol", "search parse hata: ${e.message}")
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // ============ LOAD ============
    override suspend fun load(url: String): LoadResponse? {
        Log.d("DiziSol", "load: $url")
        val tmdbId = url.substringAfterLast("/").substringBefore("?").toIntOrNull() ?: return null

        val json = try {
            app.get(url, headers = mapOf("User-Agent" to ua)).text
        } catch (e: Exception) {
            Log.e("DiziSol", "load hata: ${e.message}")
            return null
        }

        return try {
            val o = JSONObject(json)
            val mediaType = o.optString("mediaType", "movie")
            val title = o.optString("titleTr").ifBlank { o.optString("title") }
            val overview = o.optString("overview").ifBlank { o.optString("aiOverview") }
            val year = o.optInt("year", 0).takeIf { it > 0 }
            val rating = o.optDouble("rating", 0.0).takeIf { it > 0 }?.toInt()
            val poster = o.optString("poster").ifBlank { null }
            val genres = o.optString("genres").ifBlank { null }

            if (mediaType == "tv") {
                val episodes = loadEpisodesForSeries(tmdbId)
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = overview
                    this.tags = genres?.split(",")?.map { it.trim() } ?: emptyList()
                    this.score = rating?.let { Score.from10(it.toDouble()) }
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = overview
                    this.tags = genres?.split(",")?.map { it.trim() } ?: emptyList()
                    this.score = rating?.let { Score.from10(it.toDouble()) }
                }
            }
        } catch (e: Exception) {
            Log.e("DiziSol", "load parse hata: ${e.message}")
            null
        }
    }

    private suspend fun loadEpisodesForSeries(tmdbId: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        var season = 1
        while (season <= 20) {
            val seasonUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?season=$season&episode=1"
            val seasonJson = try {
                app.get(seasonUrl, headers = mapOf("User-Agent" to ua)).text
            } catch (e: Exception) {
                break
            }
            if (seasonJson.isBlank() || seasonJson.startsWith("<")) break
            val seasonObj = try { JSONObject(seasonJson) } catch (e: Exception) { break }
            
            // Eğer sezon yoksa (id 0 ise) döngüden çık
            if (seasonObj.optInt("id", 0) == 0) break

            var ep = 1
            var foundInSeason = false
            while (ep <= 100) {
                val epUrl = "$mainUrl/api/movies/by-tmdb/$tmdbId?season=$season&episode=$ep"
                val epJson = try {
                    app.get(epUrl, headers = mapOf("User-Agent" to ua)).text
                } catch (e: Exception) { break }
                if (epJson.isBlank() || epJson.startsWith("<")) break
                
                val epObj = try { JSONObject(epJson) } catch (e: Exception) { break }
                val epId = epObj.optInt("id", 0)
                if (epId == 0) break
                
                // DÜZELTME 1: Bölüm başlığını daha temiz yap
                val epTitle = "$ep. Bölüm"
                
                // DÜZELTME 2: null yerine boş string kullan
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
        Log.d("DiziSol", "loadEpisodesForSeries: ${episodes.size} bölüm")
        return episodes
    }

    // ============ LOAD LINKS (VIDEO) ============
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DiziSol", "loadLinks: $data")
        val json = try {
            app.get(data, headers = mapOf("User-Agent" to ua)).text
        } catch (e: Exception) {
            Log.e("DiziSol", "loadLinks hata: ${e.message}")
            return false
        }

        return try {
            val o = JSONObject(json)
            val mainM3u8 = o.optString("m3u8Url").ifBlank { null }
            val subTr = o.optString("subtitleTr").ifBlank { null }
            val subEn = o.optString("subtitleEn").ifBlank { null }

            // Altyazıları ekle
            subTr?.let { subtitleCallback(SubtitleFile("Türkçe", it)) }
            subEn?.let { subtitleCallback(SubtitleFile("English", it)) }

            var added = false

            // Video kaynakları için gerekli başlıklar (403 hatasını çözer)
            val videoHeaders = mapOf(
                "Referer" to "https://dizisol.com/",
                "User-Agent" to ua
            )

            // Ana kaynak
            if (mainM3u8 != null) {
                callback(newExtractorLink(
                    source = "DiziSol (Ana)",
                    name = "DiziSol Ana",
                    url = mainM3u8,
                    type = ExtractorLinkType.M3U8,
                    headers = videoHeaders
                ) {
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
                    
                    // Her ek kaynağın kendi altyazıları da olabilir
                    val sTr = src.optString("subtitleTr").ifBlank { null }
                    val sEn = src.optString("subtitleEn").ifBlank { null }
                    sTr?.let { subtitleCallback(SubtitleFile("Türkçe ($provider)", it)) }
                    sEn?.let { subtitleCallback(SubtitleFile("English ($provider)", it)) }

                    if (m3u8 != null) {
                        callback(newExtractorLink(
                            source = "DiziSol ($provider)",
                            name = "DiziSol $provider",
                            url = m3u8,
                            type = ExtractorLinkType.M3U8,
                            headers = videoHeaders
                        ) {
                            this.quality = Qualities.Unknown.value
                        })
                        added = true
                    }
                }
            }

            added
        } catch (e: Exception) {
            Log.e("DiziSol", "loadLinks parse hata: ${e.message}")
            false
        }
    }

    // ============ YARDIMCI FONKSİYONLAR ============

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
                        if (arr != null) {
                            return (0 until arr.length()).map { arr.optInt(it, 0) }.filter { it > 0 }
                        }
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("DiziSol", "extractTmdbIds hata: ${e.message}")
            emptyList()
        }
    }

    private fun extractTitleOverrides(ssrJson: String): Map<Int, Pair<String?, Int?>> {
        return try {
            val root = JSONObject(ssrJson)
            val queries = root.optJSONObject("state")?.optJSONArray("queries") ?: return emptyMap()
            for (i in 0 until queries.length()) {
                val q = queries.getJSONObject(i)
                val qKey = q.optJSONArray("queryKey") ?: continue
                if (qKey.length() >= 1 && qKey.optString(0) == "title-overrides") {
                    val data = q.optJSONObject("state")?.optJSONObject("data") ?: continue
                    val result = mutableMapOf<Int, Pair<String?, Int?>>()
                    listOf("movie", "tv").forEach { type ->
                        val obj = data.optJSONObject(type) ?: return@forEach
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val tid = key.toIntOrNull() ?: continue
                            val entry = obj.optJSONObject(key) ?: continue
                            val titleTr = entry.optString("titleTr").ifBlank { null }
                            val year = entry.optInt("year", 0).takeIf { it > 0 }
                            result[tid] = titleTr to year
                        }
                    }
                    return result
                }
            }
            emptyMap()
        } catch (e: Exception) {
            Log.e("DiziSol", "extractTitleOverrides hata: ${e.message}")
            emptyMap()
        }
    }
}
