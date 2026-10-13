package com.safakstream.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class PostData(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("cast") val cast: String? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("match") val match: String? = null,
    @JsonProperty("runtime") val runtime: String? = null,
    @JsonProperty("suggest") val suggest: List<Suggest>? = null,
    @JsonProperty("episodes") val episodes: List<NetMirrorEpisode>? = null,
    @JsonProperty("season") val season: List<Season>? = null,
    @JsonProperty("nextPageShow") val nextPageShow: Int? = null,
    @JsonProperty("nextPageSeason") val nextPageSeason: String? = null,
    @JsonProperty("desc") val desc: String? = null,
    @JsonProperty("year") val year: String? = null,
    @JsonProperty("ua") val ua: String? = null
)
