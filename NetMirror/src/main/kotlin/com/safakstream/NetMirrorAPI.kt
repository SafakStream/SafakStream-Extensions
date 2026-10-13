package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty

data class NetMirrorPlaylistResponse(
    @JsonProperty("sources") val sources: List<NetMirrorSource>?,
    @JsonProperty("tracks") val tracks: List<NetMirrorTrack>?
)

data class NetMirrorSource(
    @JsonProperty("file") val file: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("type") val type: String?
)

data class NetMirrorTrack(
    @JsonProperty("file") val file: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("kind") val kind: String?,
    @JsonProperty("language") val language: String?
)

class NetMirrorAPI : MainAPI() {
    override var mainUrl = "https://net77.cc"
    override var name = "NetMirror"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        return null
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return null
    }

    override suspend fun load(url: String): LoadResponse? {
        return null
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val id = data
        val playlistUrl = "https://net52.cc/playlist.php?id=$id"
        val response = app.get(playlistUrl).text
        val playlist = tryParseJson<NetMirrorPlaylistResponse>(response)

        playlist?.sources?.forEach { source ->
            val videoUrl = if (source.file?.startsWith("/") == true) {
                "https://net52.cc${source.file}"
            } else {
                source.file ?: return@forEach
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = source.label ?: "NetMirror",
                    url = videoUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.quality = getQualityFromName(source.label)
                }
            )
        }

        playlist?.tracks?.forEach { track ->
            if (track.kind == "captions" && track.file != null) {
                val subUrl = if (track.file.startsWith("//")) {
                    "https:${track.file}"
                } else {
                    track.file
                }
                subtitleCallback.invoke(
                    newSubtitleFile(
                        lang = track.language ?: "en",
                        url = subUrl
                    )
                )
            }
        }

        return true
    }
}
