package com.safakstream.youtube        // ← küçük harf

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class YouTubeProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = listOf(
            newMovieSearchResponse("Örnek Video", "$mainUrl/watch?v=dQw4w9WgXcQ", TvType.Movie) {
                this.posterUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
            }
        )
        return newHomePageResponse(listOf(HomePageList("Öne Çıkanlar", items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf(
            newMovieSearchResponse(query, "$mainUrl/results?search_query=$query", TvType.Movie) {
                this.posterUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
            }
        )
    }

    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse("Video", url, TvType.Movie, url) {
            this.posterUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
            this.plot = "Açıklama buraya"
        }
    }
}
