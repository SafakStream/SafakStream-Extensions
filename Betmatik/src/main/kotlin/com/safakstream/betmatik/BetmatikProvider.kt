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

    // ========================================================================
    // ÇÖZÜM: TrGoals mantığı — app.get ile m3u8'i çek, playlist.url ver
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

        val headers = mutableMapOf(
            "User-Agent" to UA,
            "Origin" to mainUrl.trimEnd('/'),
            "Referer" to "$mainUrl/"
        )

        // 1. app.get ile m3u8 içeriğini çek (Cloudflare'i Cloudstream'in OkHttp'si çözecek)
        val playlist = try {
            app.get(streamUrl, headers = headers, referer = "$mainUrl/")
        } catch (e: Exception) {
            Log.e(TAG, "playlist hata: ${e.message}")
            return false
        }

        Log.d(TAG, "playlist code=${playlist.code} url=${playlist.url}")
        Log.d(TAG, "playlist ilk100=${playlist.text.take(100).replace("\n", "\\n")}")

        // 2. Gerçekten m3u8 mi kontrol et
        if (playlist.code != 200 || !playlist.text.trimStart().startsWith("#EXTM3U")) {
            Log.e(TAG, "playlist #EXTM3U değil veya hata kodu: ${playlist.code}")

            // Fallback: CloudflareKiller dene
            try {
                val cfPlaylist = app.get(streamUrl, headers = headers, referer = "$mainUrl/",
                    interceptor = com.lagradost.cloudstream3.network.CloudflareKiller())
                Log.d(TAG, "CF playlist code=${cfPlaylist.code} url=${cfPlaylist.url}")
                if (cfPlaylist.code == 200 && cfPlaylist.text.trimStart().startsWith("#EXTM3U")) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} - $id",
                            url = cfPlaylist.url,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = Qualities.Unknown.value
                            this.headers = headers
                        }
                    )
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "CF playlist hatası: ${e.message}")
            }
            return false
        }

        // 3. Master playlist ise en yüksek kaliteli alt linki bul
        val finalUrl = if (playlist.text.contains("#EXT-X-STREAM-INF")) {
            val lines = playlist.text.lines()
            var bestUrl: String? = null
            var maxBw = -1L
            var pending = false
            var pendingBw = 0L

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                    pendingBw = Regex("BANDWIDTH=(\\d+)").find(trimmed)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    pending = true
                } else if (pending && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    if (pendingBw > maxBw) {
                        maxBw = pendingBw
                        bestUrl = try {
                            URI(playlist.url).resolve(trimmed).toString()
                        } catch (e: Exception) { trimmed }
                    }
                    pending = false
                }
            }
            bestUrl ?: playlist.url
        } else {
            playlist.url
        }

        Log.d(TAG, "finalUrl=$finalUrl")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "${this.name} - $id",
                url = finalUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
                this.headers = headers
            }
        )
        return true
    }
}
