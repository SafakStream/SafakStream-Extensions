package com.safakstream.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchData(
    @JsonProperty("searchResult") val searchResult: List<SearchResult>? = null
)
