package com.safakstream.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.safakstream.cncverse.entities.SearchData
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * CNC Verse yardımcı fonksiyonları.
 * - API base çözümleme (net22 ↔ net52 rotasyonu)
 * - Cloudflare bypass (WebView + reCAPTCHA)
 * - usertoken alma
 * - Header'ları oluşturma
 * - JSON parse (Jackson)
 */
object CNCVerseUtils {

    private val mapper = jacksonObjectMapper()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"

    const val NET22 = "https://net22.cc"
    const val NET52 = "https://net52.cc"

    // Aktif API base — resolveApiUrl() ile güncellenecek
    var apiBase: String = NET52
        private set

    /**
     * JSON string'i data class'a çevirir.
     */
    fun <T> parseJson(json: String, clazz: Class<T>): T? {
        return try {
            mapper.readValue(json, clazz)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Netflix arama.
     */
    suspend fun searchNetflix(api: MainAPI, query: String): List<SearchResponse>? {
        val url = "$apiBase/mobile/search.php?s=$query"
        val response = api.app.get(url, headers = baseHeaders()).text
        val data = try {
            mapper.readValue<SearchData>(response)
        } catch (e: Exception) {
            return null
        }
        return data.searchResult?.mapNotNull { result ->
            val id = result.id ?: return@mapNotNull null
            val title = result.title ?: return@mapNotNull null
            newMovieSearchResponse(title, id, TvType.Movie) {}
        }
    }

    /**
     * Detay yükleme (post.php).
     */
    suspend fun loadDetail(api: MainAPI, url: String): LoadResponse? {
        // TODO: Faz 3'te yazılacak
        return null
    }

    /**
     * Video linki yükleme (player.php).
     */
    suspend fun loadVideo(
        api: MainAPI,
        data: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        // TODO: Faz 5'te yazılacak
        return false
    }

    /**
     * Tüm istekler için ortak header'lar.
     */
    fun baseHeaders(): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Referer" to "$apiBase/",
        "Accept" to "application/json, text/plain, */*"
    )
}
