package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.api.Log
import org.json.JSONArray
import org.json.JSONObject

class StreamedProvider : MainAPI() {
    override var mainUrl = "https://streamed.pk"
    override var name = "Streamed"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    private val TAG = "StreamedProvider"

    private val sports = listOf(
        "football" to "Futbol",
        "basketball" to "Basketbol",
        "american-football" to "Amerikan Futbolu",
        "hockey" to "Buz Hokeyi",
        "baseball" to "Beyzbol",
        "motor-sports" to "Motor Sporlari",
        "fight" to "Dovus (UFC, Boks)",
        "tennis" to "Tenis",
        "rugby" to "Ragbi",
        "golf" to "Golf",
        "billiards" to "Bilardo",
        "afl" to "AFL",
        "darts" to "Dart",
        "cricket" to "Kriket",
        "other" to "Diger"
    )

    override val mainPage = mainPageOf(
        "live/popular" to "Canli Yayinlar",
        "featured" to "One Cikanlar",
        "football/popular" to "Futbol",
        "basketball/popular" to "Basketbol",
        "american-football/popular" to "Amerikan Futbolu",
        "hockey/popular" to "Buz Hokeyi",
        "fight/popular" to "Dovus",
        "tennis/popular" to "Tenis",
        "baseball/popular" to "Beyzbol",
        "motor-sports/popular" to "Motor Sporlari"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val endpoint = if (request.data == "featured") {
            "$mainUrl/api/matches/featured"
        } else {
            "$mainUrl/api/matches/${request.data}"
        }

        Log.d(TAG, "getMainPage -> $endpoint")

        val items = try {
            parseMatchList(app.get(endpoint).text)
        } catch (e: Exception) {
            Log.e(TAG, "getMainPage hata: ${e.message}")
            emptyList()
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val q = query.lowercase().trim()
        val results = mutableListOf<SearchResponse>()
        val seen = mutableSetOf<String>()

        for ((cat, _) in sports) {
            try {
                val endpoint = "$mainUrl/api/matches/$cat/popular"
                val items = parseMatchList(app.get(endpoint).text, category = cat)
                for (item in items) {
                    val title = (item as? LiveSearchResponse)?.name ?: continue
                    val id = item.url
                    if (id in seen) continue
                    if (title.lowercase().contains(q)) {
                        seen.add(id)
                        results.add(item)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "search $cat hata: ${e.message}")
            }
        }

        try {
            val featuredItems = parseMatchList(app.get("$mainUrl/api/matches/featured").text)
            for (item in featuredItems) {
                val title = (item as? LiveSearchResponse)?.name ?: continue
                val id = item.url
                if (id in seen) continue
                if (title.lowercase().contains(q)) {
                    seen.add(id)
                    results.add(item)
                }
            }
        } catch (_: Exception) {}

        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val matchId = url.substringAfterLast("/").substringBefore("?")
        if (matchId.isBlank()) return null

        val matchData = findMatch(matchId) ?: return null

        return newLiveStreamLoadResponse(
            matchData.title,
            url,
            url
        ) {
            this.posterUrl = if (matchData.poster.startsWith("http")) {
                matchData.poster
            } else {
                "$mainUrl${matchData.poster}"
            }
            this.plot = "${matchData.category.replaceFirstChar { it.uppercase() }} - ${matchData.sources.size} kaynak"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val matchId = data.substringAfterLast("/").substringBefore("?")
        if (matchId.isBlank()) return false

        val matchData = findMatch(matchId) ?: return false
        var found = false

        for (src in matchData.sources) {
            try {
                val streamApi = "$mainUrl/api/stream/${src.source}/${src.id}"
                Log.d(TAG, "loadLinks: $streamApi")
                val streamJson = app.get(streamApi).text
                val arr = JSONArray(streamJson)

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val embedUrl = obj.optString("embedUrl")
                    val lang = obj.optString("language", "Bilinmiyor")
                    val hd = obj.optBoolean("hd", false)
                    if (embedUrl.isBlank()) continue

                    loadExtractor(
                        embedUrl,
                        "$mainUrl/",
                        subtitleCallback,
                        callback
                    )
                    found = true

                    Log.d(TAG, "  -> $embedUrl ($lang, HD=$hd)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadLinks kaynak ${src.source} hata: ${e.message}")
            }
        }

        return found
    }

    private fun parseMatchList(json: String, category: String = ""): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title")
                val cat = category.ifBlank { obj.optString("category") }
                val poster = obj.optString("poster")
                if (id.isBlank() || title.isBlank()) continue

                val watchUrl = "$mainUrl/watch/$id"

                out.add(
                    newLiveSearchResponse(
                        title,
                        watchUrl,
                        TvType.Live,
                        false
                    ) {
                        this.posterUrl = if (poster.startsWith("http")) {
                            poster
                        } else {
                            "$mainUrl$poster"
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseMatchList hata: ${e.message}")
        }
        return out
    }

    private data class MatchData(
        val title: String,
        val category: String,
        val poster: String,
        val sources: List<SourceRef>
    )
    private data class SourceRef(val source: String, val id: String)

    private suspend fun findMatch(matchId: String): MatchData? {
        for ((cat, _) in sports) {
            try {
                val endpoint = "$mainUrl/api/matches/$cat/popular"
                val json = app.get(endpoint).text
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optString("id") == matchId) {
                        return parseMatchData(obj, cat)
                    }
                }
            } catch (_: Exception) {}
        }
        try {
            val json = app.get("$mainUrl/api/matches/featured").text
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("id") == matchId) {
                    return parseMatchData(obj, obj.optString("category"))
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun parseMatchData(obj: JSONObject, category: String): MatchData {
        val sources = mutableListOf<SourceRef>()
        val sourcesArr = obj.optJSONArray("sources")
        if (sourcesArr != null) {
            for (i in 0 until sourcesArr.length()) {
                val s = sourcesArr.getJSONObject(i)
                sources.add(SourceRef(
                    source = s.optString("source"),
                    id = s.optString("id")
                ))
            }
        }
        return MatchData(
            title = obj.optString("title"),
            category = category,
            poster = obj.optString("poster"),
            sources = sources
        )
    }
}
