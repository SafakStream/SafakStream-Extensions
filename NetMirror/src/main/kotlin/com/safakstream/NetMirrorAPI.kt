package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element

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

    // Ana sayfa filmlerini çekme
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get("$mainUrl/home").document
        
        val homePageLists = mutableListOf<HomePageList>()
        
        // Ana sayfadaki tüm "slider-item" kartlarını bul
        val items = document.select("div.slider-item")
        
        if (items.isNotEmpty()) {
            val movies = items.mapNotNull { element ->
                // Film ID'sini al
                val id = element.attr("data-post")
                
                // Film başlığını al
                val title = element.selectFirst("a[aria-label]")?.attr("aria-label") 
                    ?: return@mapNotNull null
                
                // Poster URL'sini al
                val posterUrl = element.selectFirst("img")?.attr("src") 
                    ?: element.selectFirst("img")?.attr("data-src")
                
                // Yeni bir film nesnesi oluştur
                newMovieSearchResponse(title, id, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }
            
            if (movies.isNotEmpty()) {
                homePageLists.add(HomePageList("NetMirror", movies))
            }
        }
        
        return newHomePageResponse(homePageLists)
    }

    // Arama fonksiyonu
    override suspend fun search(query: String): List<SearchResponse>? {
        // Şimdilik boş, sonra dolduracağız
        return null
    }

    // Film detay sayfası
    override suspend fun load(url: String): LoadResponse? {
        // url = film ID'si (örn: "80057918")
        val id = url
        
        // Film başlığını ve posterini çekmek için ana sayfadan bilgi alalım
        // Ama şimdilik basit bir yapı kuralım
        return newMovieLoadResponse(
            "NetMirror Movie", // Başlık
            id, // URL (ID)
            TvType.Movie,
            id // data (loadLinks'e gönderilecek veri)
        ) {
            this.posterUrl = "https://imgcdn.kim/poster/341/$id.jpg"
        }
    }

    // Video linklerini çekme
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
