package com.safakstream.streamingcommunity

import com.fasterxml.jackson.annotation.JsonProperty

// --- InertiaResponse ---
data class InertiaResponse(
    @JsonProperty("props") val props: Props,
    @JsonProperty("url") val url: String,
    @JsonProperty("version") val version: String
)

// --- Props ---
data class Props(
    @JsonProperty("scws_url") val scwsUrl: String,
    @JsonProperty("cdn_url") val cdnUrl: String,
    @JsonProperty("title") val title: TitleProp?,
    @JsonProperty("loadedSeason") val loadedSeason: Season?,
    @JsonProperty("sliders") val sliders: List<Slider>?,
    @JsonProperty("genres") val genres: List<Genre>?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("browseMoreApiRoute") val browseMoreApiRoute: String?,
    @JsonProperty("titles") val titles: List<Title>?
)

// --- Title ---
data class Title(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("slug") val slug: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("images") val images: List<PosterImage>
) {
    val poster: String?
        get() = images.firstOrNull { it.type == "poster" }?.filename
}

// --- TitleProp ---
data class TitleProp(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("slug") val slug: String,
    @JsonProperty("plot") val plot: String?,
    @JsonProperty("quality") val quality: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("score") val score: String?,
    @JsonProperty("release_date") val releaseDate: String?,
    @JsonProperty("status") val status: String?,
    @JsonProperty("age") val age: Int?,
    @JsonProperty("runtime") val runtime: Int?,
    @JsonProperty("tmdb_id") val tmdbId: Int?,
    @JsonProperty("imdb_id") val imdbId: String?,
    @JsonProperty("seasons_count") val seasonsCount: Int?,
    @JsonProperty("scws_id") val scwsId: Int?,
    @JsonProperty("trailers") val trailers: List<Trailer>?,
    @JsonProperty("seasons") val seasons: List<Season>?,
    @JsonProperty("images") val images: List<PosterImage>,
    @JsonProperty("genres") val genres: List<Genre>,
    @JsonProperty("main_actors") val mainActors: List<MainActor>?
) {
    val backgroundImageId: String?
        get() = images.firstOrNull { it.type == "background" }?.filename

    val posterImageId: String?
        get() = images.firstOrNull { it.type == "poster" }?.filename
}

// --- Diğer DTO'lar ---
data class PosterImage(
    @JsonProperty("type") val type: String,
    @JsonProperty("filename") val filename: String
)

data class Season(
    @JsonProperty("id") val id: Int,
    @JsonProperty("number") val number: Int,
    @JsonProperty("episodes") val episodes: List<Episode>?
)

data class Episode(
    @JsonProperty("id") val id: Int,
    @JsonProperty("number") val number: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("cover") val cover: String,
    @JsonProperty("plot") val plot: String?,
    @JsonProperty("duration") val duration: Int?
)

data class Slider(
    @JsonProperty("name") val name: String,
    @JsonProperty("label") val label: String,
    @JsonProperty("titles") val titles: List<Title>
)

data class Genre(
    @JsonProperty("name") val name: String
)

data class Trailer(
    @JsonProperty("youtube_url") val youtubeUrl: String?
)

data class MainActor(
    @JsonProperty("name") val name: String
)

data class LoadData(
    @JsonProperty("url") val url: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("tmdb_id") val tmdbId: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null
)

data class SearchResponse(
    @JsonProperty("data") val data: List<Title>?
)

data class GenreRequest(
    @JsonProperty("nameIT") val nameIT: String,
    @JsonProperty("nameEN") val nameEN: String,
    @JsonProperty("id") val id: Int
)

data class SliderFetchRequestSlider(
    @JsonProperty("name") val name: String,
    @JsonProperty("label") val label: String? = null
)
