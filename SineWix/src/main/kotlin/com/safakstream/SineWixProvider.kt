package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import java.net.URLEncoder

class SineWixProvider : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "SineWix"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    private val tmdbApiKey = "4e44d9029b1270a757cddc766a1bcb63"
    private val tmdbImageBase = "https://image.tmdb.org/t/p/w500"

    override val mainPage = mainPageOf(
        "$mainUrl/trending/all/week?api_key=$tmdbApiKey&language=tr-TR" to "Popüler",
        "$mainUrl/movie/popular?api_key=$tmdbApiKey&language=tr-TR" to "Popüler Filmler",
        "$mainUrl/tv/popular?api_key=$tmdbApiKey&language=tr-TR" to "Popüler Diziler",
        "$mainUrl/movie/top_rated?api_key=$tmdbApiKey&language=tr-TR" to "En İyi Filmler",
        "$mainUrl/tv/top_rated?api_key=$tmdbApiKey&language=tr-TR" to "En İyi Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}&page=$page"
        val response = app.get(url).text
        val json = tryParseJson<TmdbResponse>(response)
        val items = json?.results?.mapNotNull { it.toSearchResponse(this) } ?: emptyList()
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/search/multi?api_key=$tmdbApiKey&language=tr-TR&query=$encodedQuery"
        val response = app.get(url).text
        val json = tryParseJson<TmdbResponse>(response)
        return json?.results?.mapNotNull { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val type = if (url.contains("/tv/")) "tv" else "movie"
        val detailUrl = "$mainUrl/$type/$id?api_key=$tmdbApiKey&language=tr-TR&append_to_response=credits,videos,similar"
        val response = app.get(detailUrl).text
        val json = tryParseJson<TmdbDetail>(response) ?: throw Exception("Detaylar yüklenemedi")

        val title = json.title ?: json.name ?: "Bilinmeyen"
        val poster = json.posterPath?.let { "$tmdbImageBase$it" } ?: ""
        val backdrop = json.backdropPath?.let { "$tmdbImageBase$it" }
        val year = (json.releaseDate ?: json.firstAirDate)?.take(4)?.toIntOrNull()
        val plot = json.overview
        val score = json.voteAverage?.let { Score.from10(it) }

        return if (type == "tv") {
            val seasons = json.seasons ?: emptyList()
            val episodeList = mutableListOf<com.lagradost.cloudstream3.Episode>()
            seasons.forEach { season ->
                val seasonUrl = "$mainUrl/tv/$id/season/${season.seasonNumber}?api_key=$tmdbApiKey&language=tr-TR"
                val seasonResponse = app.get(seasonUrl).text
                val seasonJson = tryParseJson<SeasonDetail>(seasonResponse)
                seasonJson?.episodes?.forEach { ep ->
                    episodeList.add(newEpisode("$mainUrl/tv/$id/season/${season.seasonNumber}/episode/${ep.episodeNumber}") {
                        this.name = ep.name ?: "Bölüm ${ep.episodeNumber}"
                        this.season = season.seasonNumber
                        this.episode = ep.episodeNumber
                        this.posterUrl = ep.stillPath?.let { "$tmdbImageBase$it" }
                        this.description = ep.overview
                    })
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = plot
                this.score = score
                this.tags = json.genres?.mapNotNull { it.name } ?: emptyList()
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = plot
                this.score = score
                this.tags = json.genres?.mapNotNull { it.name } ?: emptyList()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.substringAfterLast("/")
        val type = if (data.contains("/tv/")) "tv" else "movie"
        val imdbId = getImdbId(id, type) ?: return false

        // Farklı kaynakları sırayla deneyeceğiz
        val sources = mutableListOf<String>()
        
        if (type == "movie") {
            sources.add("https://vidlink.pro/movie/$imdbId")
            sources.add("https://vidsrc.to/embed/movie/$imdbId")
            sources.add("https://vidsrc.sh/embed/movie/$imdbId")
        } else {
            val season = data.substringAfter("/season/").substringBefore("/")
            val episode = data.substringAfter("/episode/")
            sources.add("https://vidlink.pro/tv/$imdbId/$season/$episode")
            sources.add("https://vidsrc.to/embed/tv/$imdbId/$season/$episode")
            sources.add("https://vidsrc.sh/embed/tv/$imdbId/$season/$episode")
        }

        // Sırayla dene, biri çalışırsa diğerlerine geçme
        for (source in sources) {
            try {
                loadExtractor(source, subtitleCallback, callback)
            } catch (e: Exception) {
                // Hata alırsak bir sonraki kaynağa geç
            }
        }
        
        return true
    }

    private suspend fun getImdbId(tmdbId: String, type: String): String? {
        val url = "$mainUrl/$type/$tmdbId/external_ids?api_key=$tmdbApiKey"
        val response = app.get(url).text
        val json = tryParseJson<ExternalIds>(response)
        return json?.imdbId
    }
}

// Veri Modelleri
data class TmdbResponse(
    @JsonProperty("results") val results: List<TmdbItem>?
)

data class TmdbItem(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("backdrop_path") val backdropPath: String?,
    @JsonProperty("release_date") val releaseDate: String?,
    @JsonProperty("first_air_date") val firstAirDate: String?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("vote_average") val voteAverage: Double?,
    @JsonProperty("media_type") val mediaType: String?
)

fun TmdbItem.toSearchResponse(provider: MainAPI): SearchResponse? {
    if (id == null) return null
    val title = title ?: name ?: return null
    val isTv = mediaType == "tv" || name != null
    val url = if (isTv) "https://api.themoviedb.org/3/tv/$id" else "https://api.themoviedb.org/3/movie/$id"
    return provider.newMovieSearchResponse(title, url, if (isTv) TvType.TvSeries else TvType.Movie) {
        this.posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        this.year = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
    }
}

data class TmdbDetail(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("backdrop_path") val backdropPath: String?,
    @JsonProperty("release_date") val releaseDate: String?,
    @JsonProperty("first_air_date") val firstAirDate: String?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("vote_average") val voteAverage: Double?,
    @JsonProperty("genres") val genres: List<Genre>?,
    @JsonProperty("seasons") val seasons: List<Season>?
)

data class Genre(@JsonProperty("name") val name: String?)

data class Season(
    @JsonProperty("season_number") val seasonNumber: Int,
    @JsonProperty("episode_count") val episodeCount: Int?
)

data class SeasonDetail(
    @JsonProperty("episodes") val episodes: List<TmdbEpisode>?
)

data class TmdbEpisode(
    @JsonProperty("episode_number") val episodeNumber: Int,
    @JsonProperty("name") val name: String?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("still_path") val stillPath: String?
)

data class ExternalIds(
    @JsonProperty("imdb_id") val imdbId: String?
)
