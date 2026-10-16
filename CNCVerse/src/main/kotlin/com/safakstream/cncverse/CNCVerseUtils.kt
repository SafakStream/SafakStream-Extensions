package com.safakstream.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.safakstream.cncverse.entities.SearchData
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object CNCVerseUtils {

    private val mapper = jacksonObjectMapper()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"

    const val NET22 = "https://net22.cc"
    const val NET52 = "https://net52.cc"

    var apiBase: String = NET52
        private set

    fun <T> parseJson(json: String, clazz: Class<T>): T? {
        return try {
            mapper.readValue(json, clazz)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchNetflix(api: MainAPI, query: String): List<SearchResponse>? {
        val url = "$apiBase/mobile/search.php?s=$query"
        val responseText = app.get(url, headers = baseHeaders()).text

        val data: SearchData = try {
            mapper.readValue(responseText)
        } catch (e: Exception) {
            return null
        }

        return data.searchResult?.mapNotNull { result ->
            val id = result.id ?: return@mapNotNull null
            val title = result.title ?: return@mapNotNull null
            api.newMovieSearchResponse(title, id, TvType.Movie) {}
        }
    }

    suspend fun loadDetail(api: MainAPI, url: String): LoadResponse? {
        return null
    }

    suspend fun loadVideo(
        api: MainAPI,
        data: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        return false
    }

    fun baseHeaders(): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Referer" to "$apiBase/",
        "Accept" to "application/json, text/plain, */*"
    )
}
