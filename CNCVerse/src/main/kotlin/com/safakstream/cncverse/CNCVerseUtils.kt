package com.safakstream.cncverse

import android.content.Context
import android.content.Intent
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.safakstream.cncverse.entities.SearchData
import com.safakstream.cncverse.ui.VerifyActivity
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.getContext

object CNCVerseUtils {

    private val mapper = jacksonObjectMapper()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"

    const val NET22 = "https://net22.cc"
    const val NET52 = "https://net52.cc"

    private const val IMG_CDN = "https://imgcdn.kim"
    private const val NETFLIX_POSTER = "$IMG_CDN/poster/v"
    private const val HOTSTAR_POSTER = "$IMG_CDN/hs/h"
    private const val PRIME_POSTER = "$IMG_CDN/pv/h"

    var apiBase: String = NET52
        private set

    fun <T> parseJson(json: String, clazz: Class<T>): T? {
        return try {
            mapper.readValue(json, clazz)
        } catch (e: Exception) {
            null
        }
    }

    fun netflixPoster(id: String): String = "$NETFLIX_POSTER/$id.jpg"
    fun hotstarPoster(id: String): String = "$HOTSTAR_POSTER/$id.jpg"
    fun primePoster(id: String): String = "$PRIME_POSTER/$id.jpg"

    /**
     * Verify Activity'sini başlatır. Kullanıcı reCAPTCHA'yı çözer,
     * cookie'ler SharedPreferences'a kaydedilir.
     */
    fun startVerify(context: Context) {
        val intent = Intent(context, VerifyActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Cookie'leri context'ten oku (uygulama çalışırken).
     */
    private fun currentCookies(): Map<String, String> {
        val ctx = getContext() ?: return emptyMap()
        return CNCVerseCookieStore.getCookies(ctx)
    }

    private fun cookieHeader(): String {
        return currentCookies().entries.joinToString("; ") { "${it.key}=${it.value}" }
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
            api.newMovieSearchResponse(title, id, TvType.Movie) {
                this.posterUrl = netflixPoster(id)
            }
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

    fun baseHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to UA,
            "Referer" to "$apiBase/",
            "Accept" to "application/json, text/plain, */*"
        )
        val cookies = currentCookies()
        if (cookies.isNotEmpty()) {
            headers["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
        return headers
    }
}
