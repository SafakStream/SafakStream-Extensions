package com.safakstream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MovieListResponse(
    @JsonProperty("success") val success: Boolean? = false,
    @JsonProperty("movies") val movies: List<MovieItem>? = null,
    @JsonProperty("series") val series: List<SeriesItem>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResponse(
    @JsonProperty("success") val success: Boolean? = false,
    @JsonProperty("results") val results: List<SearchItem>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchItem(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("imdb_rating") val imdbRating: Double? = null,
    @JsonProperty("tmdb_id") val tmdbId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MovieItem(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("duration") val duration: Int? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("backdrop_url") val backdropUrl: String? = null,
    @JsonProperty("imdb_rating") val imdbRating: Double? = null,
    @JsonProperty("tmdb_id") val tmdbId: String? = null,
    @JsonProperty("parts") val parts: String? = null,
    @JsonProperty("genres") val genres: List<Any>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeriesItem(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("start_year") val startYear: Int? = null,
    @JsonProperty("end_year") val endYear: Int? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("backdrop_url") val backdropUrl: String? = null,
    @JsonProperty("imdb_rating") val imdbRating: Double? = null,
    @JsonProperty("tmdb_id") val tmdbId: String? = null,
    @JsonProperty("total_seasons") val totalSeasons: Int? = null,
    @JsonProperty("total_episodes") val totalEpisodes: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodesResponse(
    @JsonProperty("success") val success: Boolean? = false,
    @JsonProperty("data") val data: EpisodesData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodesData(
    @JsonProperty("episodes") val episodes: List<EpisodeItem>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodeItem(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("series_id") val seriesId: Long? = null,
    @JsonProperty("season_id") val seasonId: Long? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("title_tr") val titleTr: String? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("series_title") val seriesTitle: String? = null,
    @JsonProperty("series_slug") val seriesSlug: String? = null,
    @JsonProperty("series_poster_url") val seriesPosterUrl: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MoviePart(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("quality") val quality: String? = null,
)
