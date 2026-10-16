package com.safakstream.netmirror.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchData(
    @JsonProperty("head") val head: String?,
    @JsonProperty("searchResult") val searchResult: List<SearchResult>?,
    @JsonProperty("nextPage") val nextPage: Int?
)

data class SearchResult(
    @JsonProperty("id") val id: String?,
    @JsonProperty("t") val t: String?,
    @JsonProperty("i") val i: String?,
    @JsonProperty("type") val type: String?
)

data class EpisodesData(
    @JsonProperty("episodes") val episodes: List<EpItem>?,
    @JsonProperty("nextPage") val nextPage: Int?,
    @JsonProperty("nextPageSeason") val nextPageSeason: String?
)

data class EpItem(
    @JsonProperty("complate") val complate: String?,
    @JsonProperty("ep") val ep: String?,
    @JsonProperty("id") val id: String?,
    @JsonProperty("t") val t: String?,
    @JsonProperty("img") val img: String?
)

data class PlayList(
    @JsonProperty("sources") val sources: List<StreamSource>?,
    @JsonProperty("tracks") val tracks: List<SubTrack>?
)

data class StreamSource(
    @JsonProperty("file") val file: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("type") val type: String?
)

data class SubTrack(
    @JsonProperty("kind") val kind: String?,
    @JsonProperty("file") val file: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("default") val default: String?
)
