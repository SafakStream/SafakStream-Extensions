package com.safakstream.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class NewTvPlayerResponse(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("video_link") val video_link: String? = null,
    @JsonProperty("referer") val referer: String? = null
)
