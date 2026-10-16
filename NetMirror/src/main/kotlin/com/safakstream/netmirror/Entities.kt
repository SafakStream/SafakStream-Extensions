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

data class PostData(
    @JsonProperty("status") val status: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("year") val year: String?,
    @JsonProperty("desc") val desc: String?,
    @JsonProperty("cast") val cast: String?,
    @JsonProperty("director") val director: String?,
    @JsonProperty("creator") val creator: String?,
    @JsonProperty("writer") val writer: String?,
    @JsonProperty("genre") val genre: String?,
    @JsonProperty("runtime") val runtime: String?,
    @JsonProperty("hdsd") val hdsd: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("season") val season: List<Season>?,
    @JsonProperty("episodes") val episodes: List<EpItem>?
)

data class Season(
    @JsonProperty("s") val s: String?,
    @JsonProperty("id") val id: String?,
    @JsonProperty("ep") val ep: String?,
    @JsonProperty("sele") val sele: String?
)

data class EpisodesData(
    @JsonProperty("episodes") val episodes: List<EpItem>?,
    @JsonProperty("d_lang") val dLang: String?,
    @JsonProperty("nextPage") val nextPage: Int?
)

data class EpItem(
    @JsonProperty("id") val id: String?,
    @JsonProperty("t") val t: String?,
    @JsonProperty("s") val s: String?,
    @JsonProperty("ep") val ep: String?,
    @JsonProperty("ep_desc") val epDesc: String?,
    @JsonProperty("complate") val complate: String?,
    @JsonProperty("time") val time: String?
)

// Cloudstream'in PlayListItem'i ile çakışmasın diye NMPlayListItem
data class NMPlayListItem(
    @JsonProperty("title") val title: String?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("image2") val image2: String?,
    @JsonProperty("sources") val sources: List<NMStreamSource>?,
    @JsonProperty("tracks") val tracks: List<NMSubTrack>?
)

data class NMStreamSource(
    @JsonProperty("file") val file: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("default") val default: String?
)

data class NMSubTrack(
    @JsonProperty("kind") val kind: String?,
    @JsonProperty("file") val file: String?,
    @JsonProperty("label") val label: String?,
    @JsonProperty("default") val default: String?
)
