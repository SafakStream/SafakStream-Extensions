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
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
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
        private const val API_BASE = "https://data-reality.com"
        private const val UA = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    // Cloudflare engelini aşmak için gerekli header'lar
    private fun streamHeaders(): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-site"
    )

    private var cachedBaseUrl: String? = null

    override val mainPage = mainPageOf(
        "$API_BASE/matches.php" to "Canlı Maçlar",
        "$API_BASE/channels.php" to "7/24 Kanallar"
    )

    // ========================================================================
    // BASE URL (domain.php)
    // ========================================================================
    private suspend fun fetchBaseUrl(): String? {
        cachedBaseUrl?.let { return it }
        return try {
            val res = app.get("$API_BASE/domain.php", headers = mapOf("User-Agent" to UA))
            if (res.code != 200) {
                Log.e(TAG, "domain.php code=${res.code}")
                return null
            }
            val json = JSONObject(res.text)
            val base = json.optString("baseurl", "")
            if (base.isBlank()) return null
            cachedBaseUrl = base
            Log.d(TAG, "baseUrl=$base")
            base
        } catch (e: Exception) {
            Log.e(TAG, "domain.php hata: ${e.message}")
            null
        }
    }

    // ========================================================================
    // ANA SAYFA
    // ========================================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = try {
            app.get(request.data, headers = mapOf("User-Agent" to UA))
        } catch (e: Exception) {
            Log.e(TAG, "getMainPage hata: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }

        if (res.code != 200) return newHomePageResponse(request.name, emptyList())

        val doc = Jsoup.parse(res.text)
        val items: List<SearchResponse> = doc.select("a.single-match").mapNotNull { el ->
            el.toSearchResponse()
        }

        Log.d(TAG, "getMainPage ${request.name}: ${items.size} item")
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = attr("href")
        if (!href.contains("channel?id=")) return null
        val channelId = href.substringAfter("channel?id=").substringBefore("&")
        if (channelId.isBlank()) return null

        val home = selectFirst(".home")?.text()?.trim().orEmpty()
        val away = selectFirst(".away")?.text()?.trim().orEmpty()
        val event = selectFirst(".event")?.text()?.trim().orEmpty()

        // Kanal ise sadece home adı, maç ise "Home - Away"
        val title = when {
            home.isNotBlank() && away.isNotBlank() -> "$home - $away"
            home.isNotBlank() -> home
            event.isNotBlank() -> event
            else -> return null
        }

        // --- POSTER DÜZELTMESİ ---
        // 1) Tüm img'leri topla, göreceli yolları tam URL'ye çevir
        // 2) "img/tv.png" gibi genel placeholder'ları atla
        // 3) Kalan ilk gerçek görseli (genelde takım/kanal logosu) kullan
        val allImgs = select("img").mapNotNull { img ->
            val src = img.attr("src").trim()
            if (src.isBlank()) return@mapNotNull null
            when {
                src.startsWith("http://") || src.startsWith("https://") -> src
                src.startsWith("//") -> "https:$src"
                else -> runCatching { fixUrl(src) }.getOrNull()
            }
        }
        val poster = allImgs.firstOrNull { url ->
            !url.contains("img/tv.png", ignoreCase = true)
        } ?: allImgs.firstOrNull()

        val stableUrl = buildStableUrl(channelId, title)
        return newLiveSearchResponse(title, stableUrl, TvType.Live) {
            this.posterUrl = poster
        }
    }

    // ========================================================================
    // ARAMA
    // ========================================================================
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        val all = mutableListOf<SearchResponse>()
        for (endpoint in listOf("$API_BASE/matches.php", "$API_BASE/channels.php")) {
            try {
                val res = app.get(endpoint, headers = mapOf("User-Agent" to UA))
                if (res.code == 200) {
                    Jsoup.parse(res.text).select("a.single-match").forEach { el ->
                        val item = el.toSearchResponse()
                        if (item != null && item.name.contains(q, ignoreCase = true)) {
                            all.add(item)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return all.distinctBy { it.url }
    }

    // ========================================================================
    // LOAD (DETAY)
    // ========================================================================
    override suspend fun load(url: String): LoadResponse? {
        val parsed = parseStableUrl(url) ?: return null
        return newLiveStreamLoadResponse(parsed.title, url, url) {
            this.plot = "Canlı yayın - Betmatik TV"
        }
    }

    // ========================================================================
    // LOADLINKS (M3U8)
    // ========================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseStableUrl(data) ?: return false
        val base = fetchBaseUrl() ?: return false

        val streamUrl = "${base}${parsed.channelId}/mono.m3u8"
        Log.d(TAG, "streamUrl=$streamUrl")

        try {
            val check = app.get(streamUrl, headers = streamHeaders(), allowRedirects = true)
            val text = check.text.trimStart()
            if (check.code != 200 || !text.startsWith("#EXTM3U")) {
                Log.e(TAG, "m3u8 kontrol başarısız code=${check.code} ilk30=${text.take(30)}")
                return false
            }
            Log.d(TAG, "m3u8 OK, ilk satır=${text.lineSequence().firstOrNull()}")
        } catch (e: Exception) {
            Log.e(TAG, "m3u8 check hata: ${e.message}")
            return false
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = parsed.title,
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.headers = streamHeaders()
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    // ========================================================================
    // YARDIMCI FONKSİYONLAR
    // ========================================================================
    private fun buildStableUrl(channelId: String, title: String): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "$mainUrl/__betmatik__?id=${enc(channelId)}&title=${enc(title)}"
    }

    private fun parseStableUrl(url: String): ParsedUrl? {
        if (!url.contains("__betmatik__")) return null
        return try {
            val q = URI(url).rawQuery ?: return null
            val map = q.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to
                    URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
            val id = map["id"] ?: return null
            val title = map["title"] ?: id
            ParsedUrl(id, title)
        } catch (e: Exception) {
            null
        }
    }

    data class ParsedUrl(val channelId: String, val title: String)
}
