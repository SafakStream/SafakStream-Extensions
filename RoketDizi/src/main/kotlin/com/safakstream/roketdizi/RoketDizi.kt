package com.safakstream.roketdizi

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
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
import java.net.URLEncoder

class RoketDizi : MainAPI() {
    override var mainUrl = "https://roketdizi.life"
    override var name = "RoketDizi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Trend Diziler",
        "$mainUrl/" to "Trend Filmler",
        "$mainUrl/" to "Yeni Diziler",
        "$mainUrl/" to "Yeni Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val html = app.get(request.data, headers = headers, referer = mainUrl).text
            val secureData = extractSecureData(html)
                ?: return newHomePageResponse(request.name, emptyList())
            val root = mapper.readTree(secureData)

            val key = when (request.name) {
                "Trend Diziler" -> "getTrendSeries"
                "Trend Filmler" -> "getTrendMovies"
                "Yeni Diziler" -> "getLastSeriesAll"
                "Yeni Filmler" -> "getLastMovies"
                else -> return newHomePageResponse(request.name, emptyList())
            }
            val isMovie = request.name.contains("Filmler")

            val arr = root.get(key) ?: return newHomePageResponse(request.name, emptyList())
            val items = arr.mapNotNull { node -> node.toSearchResponse(isMovie) }
            Log.d("RoketDizi", "getMainPage ${request.name}: ${items.size} öğe")
            newHomePageResponse(request.name, items)
        } catch (e: Exception) {
            Log.e("RoketDizi", "getMainPage error: ${e.message}")
            newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val url = "$mainUrl/api/bg/searchContent?searchterm=${URLEncoder.encode(query, "UTF-8")}"
            val respText = app.post(
                url,
                headers = headers + mapOf(
                    "Accept" to "application/json, text/plain, */*",
                    "Referer" to "$mainUrl/"
                )
            ).text
            val respJson = mapper.readTree(respText)
            if (respJson.get("success")?.asBoolean() != true) return emptyList()
            val responseB64 = respJson.get("response")?.asText() ?: return emptyList()
            val decoded = decodeBase64(responseB64)
            val data = mapper.readTree(decoded)
            val results = data.get("result") ?: return emptyList()

            results.mapNotNull { node ->
                val slug = node.get("used_slug")?.asText()?.ifBlank { null } ?: return@mapNotNull null
                val type = node.get("used_type")?.asText() ?: "Series"
                val title = node.get("object_name")?.asText()?.ifBlank { null }
                    ?: node.get("used_title")?.asText()?.ifBlank { null }
                    ?: return@mapNotNull null
                val poster = node.get("object_poster_url")?.asText()?.ifBlank { null }
                val year = node.get("object_release_year")?.asInt()?.takeIf { it > 0 }
                val itemUrl = if (slug.startsWith("http")) slug else "$mainUrl/${slug.trimStart('/')}"
                val isMovie = type.equals("Movies", ignoreCase = true) || slug.startsWith("film/")

                if (isMovie) {
                    newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                        this.posterUrl = poster
                        this.year = year
                    }
                } else {
                    newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                        this.year = year
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RoketDizi", "search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val html = app.get(url, headers = headers, referer = mainUrl).text
            val secureData = extractSecureData(html) ?: return null
            val root = mapper.readTree(secureData)
            val contentItem = root.get("contentItem") ?: return null

            val title = contentItem.get("original_title")?.asText()?.ifBlank { null }
                ?: contentItem.get("used_title")?.asText()?.ifBlank { null }
                ?: return null
            val description = contentItem.get("description")?.asText()?.ifBlank { null }
            val poster = contentItem.get("poster_url")?.asText()?.ifBlank { null }
                ?: contentItem.get("face_url")?.asText()?.ifBlank { null }
            val year = contentItem.get("release_year")?.asInt()?.takeIf { it > 0 }
            val tags = contentItem.get("categories")?.asText()
                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }

            val related = root.get("RelatedResults")
            val seasonEpisodes = related?.get("getSerieSeasonAndEpisodes")?.get("result")

            if (seasonEpisodes != null && seasonEpisodes.size() > 0) {
                val episodes = mutableListOf<Episode>()
                seasonEpisodes.forEach { seasonNode ->
                    val seasonNo = seasonNode.get("season_no")?.asInt() ?: 1
                    val eps = seasonNode.get("episodes") ?: return@forEach
                    eps.forEach { ep ->
                        val epNo = ep.get("episode_no")?.asInt() ?: return@forEach
                        val epSlug = ep.get("used_slug")?.asText() ?: return@forEach
                        val epName = ep.get("episode_subtitle")?.asText()?.ifBlank { null }
                            ?: ep.get("episode_text")?.asText()
                            ?: "$seasonNo. Sezon $epNo. Bölüm"
                        val epUrl = if (epSlug.startsWith("http")) epSlug else "$mainUrl/${epSlug.trimStart('/')}"
                        episodes.add(newEpisode(epUrl) {
                            this.name = epName
                            this.season = seasonNo
                            this.episode = epNo
                            this.posterUrl = poster
                        })
                    }
                }
                Log.d("RoketDizi", "Series load: $title, ${episodes.size} episodes")
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                }
            } else {
                Log.d("RoketDizi", "Movie load: $title")
                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                }
            }
        } catch (e: Exception) {
            Log.e("RoketDizi", "load error: ${e.message}")
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            Log.d("RoketDizi", "loadLinks: $data")
            val html = app.get(data, headers = headers, referer = mainUrl).text
            val secureData = extractSecureData(html) ?: return false
            val root = mapper.readTree(secureData)
            val related = root.get("RelatedResults") ?: return false

            val iframeUrls = mutableSetOf<String>()

            // Series episode sources
            related.get("getEpisodeSources")?.get("result")?.forEach { src ->
                src.get("source_content")?.asText()?.let { extractIframesFromHtml(it, iframeUrls) }
            }

            // Movie part sources (dynamic keys)
            related.fieldNames().forEach { fieldName ->
                if (fieldName.startsWith("getMoviePartSourcesById_")) {
                    related.get(fieldName)?.get("result")?.forEach { src ->
                        src.get("source_content")?.asText()?.let { extractIframesFromHtml(it, iframeUrls) }
                    }
                }
            }

            val normalized = iframeUrls.map { raw ->
                when {
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/") -> "$mainUrl$raw"
                    else -> raw
                }
            }.distinct()

            Log.d("RoketDizi", "Found ${normalized.size} iframes")
            if (normalized.isEmpty()) return false

            normalized.forEach { iframeUrl ->
                try {
                    Log.d("RoketDizi", "Extracting: $iframeUrl")
                    RoketDiziPichiveExtractor().getUrl(iframeUrl, data, subtitleCallback, callback)
                } catch (e: Exception) {
                    Log.e("RoketDizi", "Extractor error: ${e.message}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e("RoketDizi", "loadLinks error: ${e.message}")
            false
        }
    }

    private fun extractSecureData(html: String): String? {
        val m = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: return null
        val secMatch = Regex(""""secureData"\s*:\s*"([^"]+)"""").find(m.groupValues[1]) ?: return null
        return decodeBase64(secMatch.groupValues[1])
    }

    private fun decodeBase64(b64: String): String {
        return try {
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractIframesFromHtml(content: String, out: MutableSet<String>) {
        val unescaped = content.replace("\\/", "/").replace("\\\"", "\"")
        Regex("""<iframe[^>]*src=["']([^"']+)["']""").findAll(unescaped).forEach {
            out.add(it.groupValues[1])
        }
        Regex("""(//[a-z0-9.-]*pichive\.online/iframe\.php\?v=[a-zA-Z0-9]+)""").findAll(unescaped).forEach {
            out.add(it.groupValues[1])
        }
        Regex("""(//[a-z0-9.-]+/iframe\.php\?v=[a-zA-Z0-9]+)""").findAll(unescaped).forEach {
            out.add(it.groupValues[1])
        }
    }

    private fun JsonNode.toSearchResponse(isMovie: Boolean): SearchResponse? {
        val slug = this.get("used_slug")?.asText()?.ifBlank { null } ?: return null
        val title = this.get("original_title")?.asText()?.ifBlank { null }
            ?: this.get("culture_title")?.asText()?.ifBlank { null }
            ?: return null
        val poster = this.get("poster_url")?.asText()?.ifBlank { null }
            ?: this.get("face_url")?.asText()?.ifBlank { null }
        val year = this.get("release_year")?.asInt()?.takeIf { it > 0 }
        val itemUrl = if (slug.startsWith("http")) slug else "$mainUrl/${slug.trimStart('/')}"

        return if (isMovie) {
            newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
            }
        } else {
            newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }
}
