package com.safakstream.sinewix

import com.fasterxml.jackson.annotation.JsonProperty

data class SinewixItem(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("backdrop_path_tv") val backdropPathTv: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("direct_link") val directLink: String? = null,
    @JsonProperty("videos") val videos: List<SinewixVideo>? = null,
    @JsonProperty("seasons") val seasons: List<SinewixSeason>? = null
)

data class SinewixVideo(
    @JsonProperty("link") val link: String? = null
)

data class SinewixSeason(
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episodes") val episodes: List<SinewixEpisode>? = null
)

data class SinewixEpisode(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("still_path_tv") val stillPathTv: String? = null,
    @JsonProperty("videos") val videos: List<SinewixVideo>? = null
)

data class SinewixYeniBolum(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("show_name") val showName: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("direct_link") val directLink: String? = null
)

data class SinewixResponse(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("data") val data: List<SinewixItem>? = null,
    @JsonProperty("search") val searchResponse: List<SinewixItem>? = null
)

data class SinewixYeniBolumResponse(
    @JsonProperty("data") val data: List<SinewixYeniBolum>? = null
)
