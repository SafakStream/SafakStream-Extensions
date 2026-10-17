package com.safakstream.betmatik

import android.util.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

class BetmatikProvider : MainAPI() {
    override var mainUrl = "https://betmatiktv156.com"
    override var name = "Betmatik"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    companion object {
        private const val TAG = "Betmatik"
        private const val DATA_BASE = "https://data-reality.com"
        private const val UA = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private var cachedBaseUrl: String? = null

    override val mainPage = mainPageOf(
        "matches" to "Canlı Maçlar",
        "channels" to "7/24 Kanallar"
    )

    private suspend fun resolveBaseUrl(): String {
        cachedBaseUrl?.let { return it }
        return try {
            val response = app.get(
                "$DATA_BASE/domain.php",
                headers = mapOf("User-Agent" to UA, "Referer" to "$mainUrl/", "Origin" to mainUrl)
            ).text
            val json = JSONObject(response)
            val base = json.optString("baseurl").trimEnd('/')
            if (base.isNotBlank()) {
                cachedBaseUrl = base
                base
            } else mainUrl
        } catch (e: Exception) {
            Log.e(TAG, "resolveBaseUrl error: ${e.message}")
            mainUrl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val endpoint = if (request.data == "channels") "$DATA_BASE/channels.php" else "$DATA_BASE/matches.php"
        val doc = app.get(endpoint, headers = mapOf("User-Agent" to UA, "Referer" to "$mainUrl/")).document
        val items = doc.select("a.single-match").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = attr("href").trim()
        if (href.isBlank()) return null
        val id = href.substringAfterLast("id=", "").trim()
        if (id.isBlank()) return null

        val home = selectFirst(".teams .home")?.text()?.trim().orEmpty()
        val away = selectFirst(".teams .away")?.text()?.trim().orEmpty()
        val event = selectFirst(".event")?.text()?.trim().orEmpty()
        val imgs = select("img")
        val logo1 = imgs.getOrNull(0)?.attr("src")

        val title = when {
            home.isNotBlank() && away.isNotBlank() -> "$home - $away"
            home.isNotBlank() -> home
            else -> event.ifBlank { id }
        }

        val cleanEvent = event.replace(Regex("^\\d{1,2}:\\d{2}\\s*\\|\\s*"), "")
        val finalTitle = if (cleanEvent.isNotBlank() && cleanEvent != title) "$title ($cleanEvent)" else title
        val poster = logo1?.let { if (it.startsWith("http")) it else fixUrl(it) }
        val encodedTitle = URLEncoder.encode(finalTitle, "UTF-8")
        val url = "$mainUrl/channel?id=$id&title=$encodedTitle"

        return newLiveSearchResponse(name = finalTitle, url = url, type = TvType.Live) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        for (endpoint in listOf("$DATA_BASE/matches.php", "$DATA_BASE/channels.php")) {
            try {
                val doc = app.get(endpoint, headers = mapOf("User-Agent" to UA, "Referer" to "$mainUrl/")).document
                doc.select("a.single-match").forEach { el ->
                    val item = el.toSearchResponse()
                    if (item != null && item.name.contains(query, ignoreCase = true)) results.add(item)
                }
            } catch (e: Exception) { Log.e(TAG, "search error: ${e.message}") }
        }
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfter("id=", "").substringBefore("&").trim()
        val title = url.substringAfter("title=", "").let {
            if (it.isBlank()) id else try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { id }
        }
        return newLiveStreamLoadResponse(name = title, url = url, dataUrl = id)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.trim()
        if (id.isBlank()) return false

        val playerPageUrl = "$mainUrl/channel?id=$id"
        val headers = mapOf("User-Agent" to UA, "Referer" to "$mainUrl/", "Origin" to mainUrl)

        // --- WebViewResolver ile player sayfasını intercept et, gerçek m3u8'i yakala ---
        try {
            Log.d(TAG, "WebViewResolver ile player sayfası açılıyor: $playerPageUrl")
            val m3u8Pattern = Regex(""".*\.m3u8.*""")
            val resolver = WebViewResolver(
                interceptUrl = m3u8Pattern,
                additionalUrls = listOf(m3u8Pattern),
                useOkhttp = false,
                timeout = 30_000L
            )

            val response = app.get(
                playerPageUrl,
                referer = "$mainUrl/",
                headers = headers,
                interceptor = resolver
            )

            val caughtUrl = response.url.toString()
            Log.d(TAG, "Yakalanan URL: $caughtUrl")

            if (caughtUrl.contains(".m3u8", ignoreCase = true)) {
                Log.d(TAG, "m3u8 başarıyla yakalandı: $caughtUrl")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} - $id",
                        url = caughtUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                        this.headers = headers
                    }
                )
                return true
            } else {
                Log.e(TAG, "m3u8 yakalanamadı. Gelen: $caughtUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebViewResolver hatası: ${e.message}")
            e.printStackTrace()
        }

        // --- Fallback: doğrudan URL üret ---
        Log.d(TAG, "WebView başarısız, fallback deneniyor")
        val base = resolveBaseUrl()
        val streamUrl = "$base/$id/mono.m3u8"
        callback(
            newExtractorLink(
                source = this.name,
                name = "${this.name} - $id",
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
                this.headers = headers + ("Accept" to "*/*")
            }
        )
        return true
    }
}
