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
    override var mainUrl = "https://net52.cc"
    override var name = "NetMirror"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        println("NetMirror DEBUG: getMainPage basladi")

        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to "en-IN,en-US;q=0.9,en;q=0.8",
            "Cache-Control" to "max-age=0",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "same-origin",
            "Sec-Fetch-User" to "?1",
            "Upgrade-Insecure-Requests" to "1",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 5 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.132 Safari/537.36 /OS.Gatu v3.0",
            "X-Requested-With" to "XMLHttpRequest"
        )

        // net52.cc'ye istek at, yönlendirmeyi takip et
        val response = app.get(mainUrl, headers = headers, allowRedirects = true)
        println("NetMirror DEBUG: Status Code = ${response.code}")
        println("NetMirror DEBUG: Final URL = ${response.url}")

        val document = response.document
        val items = document.select("div.slider-item")
        println("NetMirror DEBUG: Bulunan slider-item sayisi = ${items.size}")

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
            println("NetMirror DEBUG: Olusturulan film sayisi = ${movies.size}")

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
