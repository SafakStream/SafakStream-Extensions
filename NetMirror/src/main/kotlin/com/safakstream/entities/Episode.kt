package com.safakstream.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class NetMirrorEpisode(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("t") val t: String? = null,
    @JsonProperty("ep") val ep: String? = null,
    @JsonProperty("s") val s: String? = null,
    @JsonProperty("time") val time: String? = null
)
