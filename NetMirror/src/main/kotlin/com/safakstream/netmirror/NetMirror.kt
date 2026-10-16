package com.safakstream.netmirror

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.safakstream.netmirror.entities.*
import java.util.UUID

class NetMirror : MainAPI() {
    override var name = "NetMirror"
    override var mainUrl = "https://net52.cc"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    @Volatile private var cachedCookie: String? = null
    @Volatile private var cookieTimestamp: Long = 0L

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun headers(cookie: String? = null): Map<String, String> {
        val h = mutableMapOf(
            "User-Agent" to ua,
            "Accept" to "*/*",
            "Referer" to "$mainUrl/",
            "X-Requested-With" to "NetmirrorNewTV v1.0"
        )
        if (cookie != null) h["Cookie"] = "t_hash_t=$cookie"
        return h
    }

    private suspend fun getCookie(force: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!force && cachedCookie != null && (now - cookieTimestamp) < 259200000L) {
            return cachedCookie
        }
        return try {
            val res = app.post(
                "$mainUrl/verify.php",
                headers = mapOf(
                    "User-Agent" to ua,
                    "Origin" to "https://net22.cc",
                    "Referer" to "https://net22.cc/verify2",
                    "Content-Type" to "application/x-www-form-urlencoded"
                ),
                data = mapOf("g-recaptcha-response" to UUID.randomUUID().toString())
            )
            // NiceHttp'in cookies API'sini kullan
            val cookieVal = res.cookies["t_hash_t"]
            if (!cookieVal.isNullOrEmpty()) {
                cachedCookie = cookieVal
                cookieTimestamp = now
            }
            cookieVal
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/search.php?s=action" to "Aksiyon",
        "$mainUrl/search.php?s=netflix" to "Netflix",
        "$mainUrl/search.php?s=marvel" to "Marvel",
        "$mainUrl/search.php?s=avatar" to "Trend"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (page > 1) return null
        val cookie = getCookie()
        val res = app.get(request.data, headers = headers(cookie)).text
        val data = tryParseJson<SearchData>(res) ?: return null
        val items = data.searchResult.orEmpty().mapNotNull { it.toSearchResponse() }
        if (items.isEmpty()) return null
        return newHomePageResponse(listOf(HomePageList(request.name, items, true)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cookie = getCookie()
        val url = "$mainUrl/search.php?s=${query.replace(" ", "%20")}"
        val res = app.get(url, headers = headers(cookie)).text
        val data = tryParseJson<SearchData>(res) ?: return emptyList()
        return data.searchResult.orEmpty().mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.substringAfter("post:").substringBefore("/").trim()
        if (id.isEmpty()) return null

        val cookie = getCookie()
        val res = app.get("$mainUrl/post.php?id=$id", headers = headers(cookie)).text
        val data = tryParseJson<PostData>(res) ?: return null
        if (data.status != "y") return null

        val title = data.title ?: "NetMirror $id"
        val poster = "https://imgcdn.kim/poster/v/$id.jpg"
        val plot = data.desc
        val year = data.year?.toIntOrNull()
        val tags = data.genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        val actors: List<ActorData>? = data.cast
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { ActorData(Actor(it), null) }

        val isSeries = data.type == "t" || !data.season.isNullOrEmpty()

        return if (isSeries) {
            val episodes = mutableListOf<Episode>()
            data.season.orEmpty().forEach { season ->
                val seasonId = season.id ?: return@forEach
                val seasonNum = season.s?.toIntOrNull() ?: 1
                val epRes = app.get("$mainUrl/episodes.php?s=$seasonId", headers = headers(cookie)).text
                val epData = tryParseJson<EpisodesData>(epRes)
                epData?.episodes.orEmpty().forEach { ep ->
                    val epId = ep.id ?: return@forEach
                    episodes.add(newEpisode("ep:$epId") {
                        this.name = ep.t ?: "Bolum ${ep.ep ?: ""}"
                        this.season = seasonNum
                        this.episode = ep.ep?.toIntOrNull()
                        this.posterUrl = poster
                        this.description = ep.epDesc
                        this.runTime = ep.time?.removeSuffix("m")?.toIntOrNull()
                    })
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.actors = actors
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "mov:$id") {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.actors = actors
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.substringAfter(":").trim()
        if (id.isEmpty()) return false

        val cookie = getCookie()
        val res = app.get("$mainUrl/playlist.php?id=$id", headers = headers(cookie)).text
        val playlists = tryParseJson<List<NMPlayListItem>>(res) ?: return false
        var found = false

        playlists.forEach { playlist ->
            val sources = playlist.sources.orEmpty()
            sources.forEach { source ->
                val raw = source.file ?: return@forEach
                val videoUrl = if (raw.startsWith("http")) raw else "$mainUrl$raw"
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name ${source.label ?: ""}".trim(),
                        url = videoUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.quality = getQualityFromName(source.label)
                        this.referer = "$mainUrl/"
                        this.headers = headers(cookie)
                    }
                )
                found = true
            }
            val tracks = playlist.tracks.orEmpty()
            tracks.forEach { track ->
                val trackUrl = track.file ?: return@forEach
                subtitleCallback(newSubtitleFile(track.label ?: "Altyazi", trackUrl))
            }
        }
        return found
    }

    private fun SearchResult.toSearchResponse(): SearchResponse? {
        val id = this.id ?: return null
        val title = this.t ?: return null
        val poster = this.i ?: "https://imgcdn.kim/poster/v/$id.jpg"
        return newMovieSearchResponse(title, "post:$id", TvType.Movie) {
            this.posterUrl = poster
        }
    }
}
