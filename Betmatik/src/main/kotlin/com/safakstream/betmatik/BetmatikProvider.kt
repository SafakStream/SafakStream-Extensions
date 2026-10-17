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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import org.json.JSONObject

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
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private var cachedBaseUrl: String? = null

    override val mainPage = mainPageOf(
        "matches" to "Canlı Maçlar",
        "channels" to "7/24 Kanallar"
    )

    // ========================================================================
    // BASE URL ÇÖZÜMLEME (domain.php'den)
    // ========================================================================
    private suspend fun resolveBaseUrl(): String {
        cachedBaseUrl?.let { return it }
        return try {
            val response = app.get(
                "$DATA_BASE/domain.php",
                headers = mapOf("User-Agent" to UA, "Referer" to "$mainUrl/")
            ).text
            Log.d(TAG, "domain.php raw: $response")
            val json = JSONObject(response)
            val base = json.optString("baseurl").trimEnd('/')
            if (base.isNotBlank()) {
                cachedBaseUrl = base
                Log.d(TAG, "Resolved baseUrl=$base")
                base
            } else {
                mainUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveBaseUrl error: ${e.message}")
            mainUrl
        }
    }

    // ========================================================================
    // ANA SAYFA
    // ========================================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val endpoint = if (request.data == "channels") "$DATA_BASE/channels.php"
                       else "$DATA_BASE/matches.php"

        val doc = app.get(
            endpoint,
            headers = mapOf(
                "User-Agent" to UA,
                "Referer" to "$mainUrl/",
                "Accept" to "text/html,*/*"
            )
        ).document

        val items = doc.select("a.single-match").mapNotNull { it.toSearchResponse() }
        Log.d(TAG, "getMainPage(${request.data}) -> ${items.size} items")
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
        val category = selectFirst(".date")?.text()?.trim().orEmpty()
        val imgs = select("img")
        val logo1 = imgs.getOrNull(0)?.attr("src")
        val logo2 = imgs.getOrNull(1)?.attr("src")

        val title = when {
            home.isNotBlank() && away.isNotBlank() -> "$home - $away"
            home.isNotBlank() -> home
            else -> event.ifBlank { id }
        }

        // "16:00 | Trendyol Süper Lig" -> temizle
        val cleanEvent = event.replace(Regex("^\\d{1,2}:\\d{2}\\s*\\|\\s*"), "")
        val finalTitle = if (cleanEvent.isNotBlank() && cleanEvent != title) {
            "$title ($cleanEvent)"
        } else title

        val poster = when {
            logo1 != null && logo2 != null -> logo1 // İlk logo (ev sahibi)
            logo1 != null -> logo1
            else -> null
        }

        return newLiveSearchResponse(
            name = finalTitle,
            url = "$mainUrl/channel?id=$id",
            type = TvType.Live
        ) {
            this.posterUrl = poster?.let { if (it.startsWith("http")) it else fixUrl(it) }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        // Hem maçları hem kanalları çekip filtrele
        val results = mutableListOf<SearchResponse>()
        for (endpoint in listOf("$DATA_BASE/matches.php", "$DATA_BASE/channels.php")) {
            try {
                val doc = app.get(
                    endpoint,
                    headers = mapOf("User-Agent" to UA, "Referer" to "$mainUrl/")
                ).document
                doc.select("a.single-match").forEach { el ->
                    val item = el.toSearchResponse()
                    if (item != null && item.name.contains(query, ignoreCase = true)) {
                        results.add(item)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "search error: ${e.message}")
            }
        }
        return results.distinctBy { it.url }
    }

    // ========================================================================
    // DETAY / LOAD
    // ========================================================================
    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("id=", "").trim()
        return newLiveStreamLoadResponse(
            name = id,
            url = url,
            dataUrl = id
        )
    }

    // ========================================================================
    // LINK ÇIKARMA
    // ========================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.trim()
        if (id.isBlank()) return false

        val base = resolveBaseUrl()
        val streamUrl = "$base/$id/mono.m3u8"
        Log.d(TAG, "loadLinks: id=$id base=$base -> $streamUrl")

        callback(
            newExtractorLink(
                source = this.name,
                name = "${this.name} - $id",
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "User-Agent" to UA,
                    "Referer" to "$mainUrl/"
                )
            }
        )
        return true
    }
}
