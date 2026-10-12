package com.safakstream.sinewix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class SinewixProvider : MainAPI() {
    override var mainUrl = "https://ydfvfdizipanel.ru"
    override var name = "Sinewix"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "tr"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
        TvType.Cartoon
    )

    private val apiToken = "9iQNC5HQwPlaFuJDkhncJ5XTJ8feGXOJatAA"
    private val hash256 = "711bff4afeb47f07ab08a0b07e85d3835e739295e8a6361db77eebd93d96306b" // Eski hash, deneme amaçlı

    private val sineHeaders = mapOf(
        "User-Agent" to "EasyPlex (Android 14; SM-A546B; Samsung Galaxy A54 5G; tr)",
        "Accept" to "application/json"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/public/api/movies/latestadded/$apiToken" to "Son Eklenen Filmler",
        "$mainUrl/public/api/series/latestadded/$apiToken" to "Son Eklenen Diziler",
        "$mainUrl/public/api/series/newEpisodescontent/$apiToken" to "Yeni Bölümler",
        "$mainUrl/public/api/animes/latestadded/$apiToken" to "Son Animeler",
        "$mainUrl/public/api/genres/mediaLibrary/show/80/movie/$apiToken" to "Suç Filmleri",
        "$mainUrl/public/api/genres/mediaLibrary/show/80/serie/$apiToken" to "Suç Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/9648/movie/$apiToken" to "Gizem Filmleri",
        "$mainUrl/public/api/genres/mediaLibrary/show/9648/serie/$apiToken" to "Gizem Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/10769/serie/$apiToken" to "Kore Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/16/movie/$apiToken" to "Animasyonlar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"

        // POST isteği ile hash256'yı body'de gönderiyoruz
        val response = app.post(
            url,
            headers = sineHeaders,
            data = mapOf("hash256" to hash256)
        ).parsedSafe<SinewixResponse>()

        val items = response?.data?.mapNotNull { it.toSearchResponse(request.name) } ?: emptyList()
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/public/api/search/$query/$apiToken"
        val response = app.post(
            url,
            headers = sineHeaders,
            data = mapOf("hash256" to hash256)
        ).parsedSafe<SinewixResponse>()

        val items = response?.searchResponse ?: response?.data ?: emptyList()
        return items.mapNotNull { it.toSearchResponse(null) }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Load işlemi için de POST deneyelim
        val data = app.post(
            url,
            headers = sineHeaders,
            data = mapOf("hash256" to hash256)
        ).parsedSafe<SinewixItem>() ?: return null

        val title = data.name ?: data.title ?: return null
        val poster = data.posterPath ?: data.backdropPath ?: data.backdropPathTv ?: ""

        val isSeries = url.contains("series/show") ||
                url.contains("serie") ||
                data.type == "serie"
        val isAnime = url.contains("anime") || data.type == "anime"

        return if (isSeries || isAnime) {
            val episodes = data.seasons
                ?.flatMap { season ->
                    season.episodes?.map { episode ->
                        newEpisode(episode.videos?.firstOrNull()?.link ?: "") {
                            this.name = episode.name ?: "Bölüm ${episode.episodeNumber}"
                            this.season = season.seasonNumber
                            this.episode = episode.episodeNumber
                            this.posterUrl = episode.stillPath ?: episode.stillPathTv
                        }
                    } ?: emptyList()
                } ?: emptyList()

            newTvSeriesLoadResponse(
                title,
                url,
                if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
            }
        } else {
            val videoLink = data.videos?.firstOrNull()?.link ?: data.directLink ?: ""
            newMovieLoadResponse(title, url, TvType.Movie, videoLink) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        val isDirectFile = Regex("\\.(mkv|mp4|m3u8|webm|avi)(\\?|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(data)

        if (isDirectFile) {
            val isM3u8 = data.contains(".m3u8", ignoreCase = true)
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = data,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = mainUrl
                }
            )
        } else {
            loadExtractor(data, subtitleCallback, callback)
        }
        return true
    }

    private fun SinewixItem.toSearchResponse(url: String?): SearchResponse? {
        val itemTitle = name ?: title ?: return null
        val poster = posterPath ?: backdropPath ?: backdropPathTv ?: ""

        val type = when {
            url?.contains("serie") == true -> TvType.TvSeries
            url?.contains("anime") == true -> TvType.Anime
            this.type == "serie" -> TvType.TvSeries
            this.type == "anime" -> TvType.Anime
            else -> TvType.Movie
        }

        val href = if (type == TvType.Movie) {
            "$mainUrl/public/api/media/detail/$id/$apiToken"
        } else {
            "$mainUrl/public/api/series/show/$id/$apiToken"
        }

        return if (type == TvType.Movie) {
            newMovieSearchResponse(itemTitle, href, type) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(itemTitle, href, type) {
                this.posterUrl = poster
            }
        }
    }

    private fun SinewixYeniBolum.toSearchResponse(): SearchResponse? {
        val itemTitle = showName ?: return null
        val poster = posterPath ?: stillPath ?: ""

        val displayTitle = "$itemTitle S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"

        val href = if (!directLink.isNullOrBlank()) {
            directLink
        } else {
            "$mainUrl/public/api/series/show/$id/$apiToken"
        }

        return newTvSeriesSearchResponse(displayTitle, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }
}
