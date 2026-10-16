package com.safakstream.cncverse.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * CNC Verse / NetMirror (NewTV) backend entity'leri.
 * Bytecode'dan çıkarılan alan isimleri kullanılmıştır.
 */

// ============================================================
// ARAMA
// ============================================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchData(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("type") val type: Int? = null,
    @JsonProperty("head") val head: String? = null,
    @JsonProperty("searchResult") val searchResult: List<SearchResult>? = null,
    @JsonProperty("error") val error: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResult(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("t") val title: String? = null,
    @JsonProperty("y") val year: String? = null,
    @JsonProperty("r") val runtime: String? = null
)

// ============================================================
// DETAY (post.php)
// ============================================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class PostData(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("error") val error: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("year") val year: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("runtime") val runtime: String? = null,
    @JsonProperty("rating") val rating: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("genre") val genre: List<PostCategory>? = null,
    @JsonProperty("cast") val cast: List<String>? = null,
    @JsonProperty("director") val director: List<String>? = null,
    @JsonProperty("episodes") val episodes: List<Episode>? = null,
    @JsonProperty("seasons") val seasons: List<Season>? = null,
    @JsonProperty("suggest") val suggest: List<Suggest>? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("tmdb") val tmdb: String? = null,
    @JsonProperty("imdb") val imdb: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PostCategory(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("id") val id: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Episode(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
    @JsonProperty("epimg") val epimg: String? = null,
    @JsonProperty("runtime") val runtime: String? = null,
    @JsonProperty("description") val description: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Season(
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("episodes") val episodes: List<Episode>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Suggest(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("t") val title: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("y") val year: String? = null
)

// ============================================================
// BÖLÜMLER (episodes.php)
// ============================================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodesData(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("error") val error: String? = null,
    @JsonProperty("episodes") val episodes: List<Episode>? = null,
    @JsonProperty("seasons") val seasons: List<Season>? = null
)

// ============================================================
// OYNATMA (player.php)
// ============================================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayList(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("error") val error: String? = null,
    @JsonProperty("playlist") val playlist: List<PlayListItem>? = null,
    @JsonProperty("sources") val sources: List<Source>? = null,
    @JsonProperty("tracks") val tracks: List<Tracks>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayListItem(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("size") val size: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("type") val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Source(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("size") val size: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Tracks(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("kind") val kind: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("lang") val lang: String? = null
)

// ============================================================
// ANA SAYFA (home?app=1)
// ============================================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class MainPage(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("error") val error: String? = null,
    @JsonProperty("posts") val posts: List<PostData>? = null
)

// ============================================================
// NEWTV TOKEN / OTP
// ============================================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class NewTvOtpResponse(
    @JsonProperty("otp") val otp: String? = null,
    @JsonProperty("token_hash") val tokenHash: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("error") val error: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NewTvTokenResponse(
    @JsonProperty("token_hash") val tokenHash: String? = null,
    @JsonProperty("token") val token: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("error") val error: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NewTvPlayerResponse(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("video_link") val videoLink: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("error") val error: String? = null
)
