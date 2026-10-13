package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.FormBody
import java.util.UUID

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

    private var cookieValue: String = ""

    private suspend fun bypass(): String {
        if (cookieValue.isNotEmpty()) return cookieValue

        try {
            val headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Origin" to "https://net22.cc",
                "Referer" to "https://net22.cc/verify2",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
            )

            val body = FormBody.Builder()
                .add("g-recaptcha-response", UUID.randomUUID().toString())
                .build()

            val response = app.post(
                "https://net52.cc/verify.php",
                requestBody = body,
                headers = headers
            )

            val setCookie = response.headers["Set-Cookie"] ?: ""
            val match = Regex("t_hash_t=([^;]+)").find(setCookie)
            cookieValue = match?.groupValues?.get(1) ?: ""

            return cookieValue
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val cookie = bypass()
        val headers = mapOf(
            "Cookie" to "t_hash_t=$cookie; hd=on; ott=nf",
            "Referer" to "$mainUrl/home"
        )

        val document = app.get("$mainUrl/home", headers = headers).document
        val items = document.select("div.slider-item")

        val homePageLists = mutableListOf<HomePageList>()

        if (items.isNotEmpty()) {
            val movies = items.mapNotNull { element ->
                val id = element.attr("data-post")
                val title = element.selectFirst("a[aria-label]")?.attr("aria-label")
                    ?: return@mapNotNull null
                val posterUrl = element.selectFirst("img")?.attr("src")
                    ?: element.selectFirst("img")?.attr("data-src")

                newMovieSearchResponse(title, id, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
                }
            }

            if (movies.isNotEmpty()) {
                homePageLists.add(HomePageList("NetMirror", movies))
            }
        }

        return newHomePageResponse(homePageLists)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return null
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url
        return newMovieLoadResponse(
            "NetMirror Movie",
            id,
            TvType.Movie,
            id
        ) {
            this.posterUrl = "https://imgcdn.kim/poster/v/$id.jpg"
            this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
        }
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
