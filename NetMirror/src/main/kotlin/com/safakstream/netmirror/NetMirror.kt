package com.safakstream.netmirror

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.safakstream.netmirror.entities.*

class NetMirror : MainAPI() {
    override var name = "NetMirror"
    override var mainUrl = "https://net77.cc"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/search.php?s=action" to "Aksiyon",
        "$mainUrl/search.php?s=netflix" to "Netflix",
        "$mainUrl/search.php?s=marvel" to "Marvel",
        "$mainUrl/search.php?s=avatar" to "Trend"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (page > 1) return null
        val res = app.get(request.data, headers = commonHeaders).text
        val data = tryParseJson<SearchData>(res) ?: return null
        val items = data.searchResult.orEmpty().mapNotNull { it.toSearchResponse() }
        if (items.isEmpty()) return null
        return newHomePageResponse(listOf(HomePageList(request.name, items, true)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search.php?s=${query.replace(" ", "%20")}"
        val res = app.get(url, headers = commonHeaders).text
        val data = tryParseJson<SearchData>(res) ?: return emptyList()
        return data.searchResult.orEmpty().mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split("|")
        val title = parts.getOrNull(0) ?: "NetMirror"
        val id = parts.getOrNull(1) ?: url.trim('/')
        val poster = "https://imgcdn.kim/poster/1920/$id.jpg"

        val epRes = app.get("$mainUrl/episodes.php?s=$id", headers = commonHeaders).text
        val epData = tryParseJson<EpisodesData>(epRes)
        val eps = epData?.episodes

        if (!eps.isNullOrEmpty()) {
            val episodes = eps.mapIndexedNotNull { idx, ep ->
                val epId = ep.id ?: return@mapIndexedNotNull null
                newEpisode(epId) {
                    this.name = ep.t ?: "Bolum ${idx + 1}"
                    this.season = 1
                    this.episode = ep.ep?.toIntOrNull() ?: (idx + 1)
                    this.posterUrl = ep.img ?: poster
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, id) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.trim('/')
        val res = app.get("$mainUrl/playlist.php?id=$id", headers = commonHeaders).text
        val playlists = tryParseJson<List<PlayList>>(res) ?: return false
        var found = false

        playlists.forEach { playlist ->
            playlist.sources?.forEach { source ->
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
                        this.headers = commonHeaders
                    }
                )
                found = true
            }
            playlist.tracks?.forEach { track ->
                val trackUrl = track.file ?: return@forEach
                subtitleCallback(newSubtitleFile(track.label ?: "Altyazi", trackUrl))
            }
        }
        return found
    }

    private fun SearchResult.toSearchResponse(): SearchResponse? {
        val id = this.id ?: return null
        val title = this.t ?: return null
        val poster = this.i ?: "https://imgcdn.kim/poster/1920/$id.jpg"
        return newMovieSearchResponse(title, "$title|$id", TvType.Movie) {
            this.posterUrl = poster
        }
    }
}
