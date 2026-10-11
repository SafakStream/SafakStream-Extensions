package com.safakstream

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchItem(
    @JsonProperty("object_id") 
    val id: Int,
    
    @JsonProperty("object_name") 
    val title: String,
    
    @JsonProperty("object_alternative_name") 
    val trTitle: String,
    
    @JsonProperty("object_poster_url") 
    val poster: String,
    
    @JsonProperty("object_categories") 
    val genres: String? = null, 

    @JsonProperty("object_related_imdb_point") 
    val imdb: Double,
    
    @JsonProperty("object_release_year") 
    val year: Int,
    
    @JsonProperty("used_type") 
    val type: String,
    
    @JsonProperty("used_slug") 
    val slug: String
)
