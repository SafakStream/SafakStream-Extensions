package com.safakstream.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchResult(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("t") val t: String? = null
)
