// SafakStream-Extensions/InatTV/src/main/kotlin/com/safakstream/InatTV.kt

package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

class InatTV : MainAPI() {
    override var mainUrl              = "https://www.inattvizle494.top"
    override var name                 = "InatTV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    companion object {
        private const val TAG = "InatTV"
    }

    override val mainPage = mainPageOf(
        "canli-maclar" to "Canli Maclar",
        "tv-kanallari" to "7/24 TV Kanallari"
    )

    // ========================================================================
    // ANA SAYFA
    // ========================================================================

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val site = DomainResolver.resolve() ?: return newHomePageResponse(request.name, emptyList())
        mainUrl = site.url.trimEnd('/')

        val items: List<SearchResponse> = when (request.data) {
            "canli-maclar" -> {
                val doc = app.get(site.url, headers = mapOf("User-Agent" to DomainResolver.UA)).document
                SportsParser.matches(doc.html(), site.url).map { it.toSearchResponse() }
            }
            "tv-kanallari" -> site.channels.map { it.toSearchResponse() }
            else -> emptyList()
        }

        return newHomePageResponse(request.name, items)
    }

    // ========================================================================
    // ARAMA
    // ========================================================================

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val site = DomainResolver.resolve() ?: return emptyList()
        mainUrl = site.url.trimEnd('/')

        val doc = app.get(site.url, headers = mapOf("User-Agent" to DomainResolver.UA)).document
        val matches = SportsParser.matches(doc.html(), site.url).map { it.toSearchResponse() }
        val channels = site.channels.map { it.toSearchResponse() }
        val all = matches + channels

        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        return all.filter { it.name.lowercase().contains(q) }
    }

    // ========================================================================
    // SPORTSCHANNEL -> SEARCHRESPONSE
    // ========================================================================

    private fun SportsChannel.toSearchResponse(): SearchResponse {
        val stableUrl = buildStableUrl(id, title, player, target)
        return newLiveSearchResponse(title, stableUrl, TvType.Live) {
            this.posterUrl = poster
        }
    }

    // ========================================================================
    // LOAD
    // ========================================================================

    override suspend fun load(url: String): LoadResponse? {
        Log.d(TAG, "load() cagrildi: $url")

        val parsed = parseStableUrl(url) ?: run {
            Log.e(TAG, "parseStableUrl null")
            return null
        }

        val description = when (parsed.target) {
            "viptv", "vip" -> "Canli yayin"
            "m3u8" -> "HLS yayin"
            "frame" -> "Gomulu yayin"
            else -> "Canli yayin"
        }

        return newLiveStreamLoadResponse(
            parsed.title,
            url,
            url
        ) {
            this.plot = description
        }
    }

    // ========================================================================
    // LOADLINKS
    // ========================================================================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(TAG, "loadLinks() cagrildi: $data")

        val parsed = parseStableUrl(data) ?: run {
            Log.e(TAG, "parseStableUrl null (loadLinks)")
            return false
        }

        val name = parsed.title
        val player = parsed.player
        val target = parsed.target

        val site = DomainResolver.resolve() ?: run {
            Log.e(TAG, "DomainResolver null")
            return false
        }

        Log.d(TAG, "loadLinks => name=$name target=$target player=$player domain=${site.url}")

        val headers = mutableMapOf(
            "User-Agent" to DomainResolver.UA,
            "Origin" to site.url.trimEnd('/')
        )

        val streamUrl: String

        if (target == "viptv" || target == "vip") {
            val param = if (target == "viptv") "channel" else "id"

            Log.d(TAG, "auth.php POST => url=${site.config.authUrl}, param=$param=$player")

            val response = try {
                app.post(
                    site.config.authUrl,
                    headers = headers + mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Accept" to "application/json, text/javascript, */*; q=0.01"
                    ),
                    referer = site.url,
                    data = mapOf(param to player)
                )
            } catch (e: Exception) {
                Log.e(TAG, "auth.php POST exception: ${e.message}")
                return false
            }

            Log.d(TAG, "auth.php response code=${response.code}")

            if (response.code != 200) {
                Log.e(TAG, "auth.php hata: ${response.code}")
                return false
            }

            val session = parseStreamSession(response.text) ?: return false
            if (session.token.isNotEmpty()) {
                headers["usertoken"] = session.token
                headers["pl"] = site.config.siteName
            }
            streamUrl = session.url
        } else {
            streamUrl = player
        }

        // .js veya m3u8'i cek
        val playlist = try {
            app.get(streamUrl, headers = headers, referer = site.url)
        } catch (e: Exception) {
            Log.e(TAG, "playlist get hata: ${e.message}")
            return false
        }

        if (playlist.code != 200 || !playlist.text.trimStart().startsWith("#EXTM3U")) {
            Log.e(TAG, "playlist EXTM3U degil (code=${playlist.code})")
            return false
        }

        val variants = parseHlsVariants(playlist.text, playlist.url, name)
        val maxHeight = variants.maxOfOrNull { it.height } ?: 0
        val qualitySuffix = if (maxHeight > 0) " • ${maxHeight}p" else ""

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "$name$qualitySuffix",
                url = playlist.url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = site.url
                this.headers = headers
                this.quality = if (maxHeight > 0) maxHeight else Qualities.Unknown.value
            }
        )

        return true
    }

    // ========================================================================
    // YARDIMCI: STABLE URL
    // ========================================================================

    private fun buildStableUrl(id: String, title: String, player: String, target: String): String {
        val base = DomainResolver.currentUrl().trimEnd('/')
        return "$base/watch" +
            "?id=" + URLEncoder.encode(id, "UTF-8") +
            "&title=" + URLEncoder.encode(title, "UTF-8") +
            "&player=" + URLEncoder.encode(player, "UTF-8") +
            "&target=" + URLEncoder.encode(target, "UTF-8")
    }

    private fun queryParam(url: String, key: String): String? {
        return try {
            val uri = URI(url)
            val rawQuery = uri.rawQuery ?: return null
            rawQuery.split("&")
                .firstOrNull { it.substringBefore("=") == key }
                ?.substringAfter("=")
                ?.let { URLDecoder.decode(it, "UTF-8") }
        } catch (e: Exception) { null }
    }

    private data class ParsedUrl(
        val id: String,
        val title: String,
        val player: String,
        val target: String
    )

    private fun parseStableUrl(url: String): ParsedUrl? {
        val id = queryParam(url, "id") ?: return null
        val title = queryParam(url, "title") ?: return null
        val player = queryParam(url, "player") ?: return null
        val target = queryParam(url, "target") ?: return null
        return ParsedUrl(id, title, player, target)
    }

    // ========================================================================
    // YARDIMCI: STREAM SESSION
    // ========================================================================

    private fun parseStreamSession(json: String): StreamSession? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("ERROR")) return null
            val url = obj.optString("URL", "")
            val token = obj.optString("TOKEN", "")
            if (url.isBlank()) return null
            if (token.length > 2048) return null
            if (token.contains('\n') || token.contains('\r')) return null
            StreamSession(url, token)
        } catch (e: Exception) { null }
    }

    // ========================================================================
    // YARDIMCI: HLS VARYANTLARI
    // ========================================================================

    private fun parseHlsVariants(text: String, baseUrl: String, label: String): List<HlsVariant> {
        val variants = mutableListOf<HlsVariant>()
        var height = 0
        var bandwidth = 0L
        var pending = false

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                height = Regex("RESOLUTION=([0-9]+)x([0-9]+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.get(2)?.toIntOrNull() ?: 0
                bandwidth = Regex("(?:^|,)BANDWIDTH=([0-9]+)")
                    .find(line.substringAfter(":"))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                pending = true
            } else if (pending && line.isNotBlank() && !line.startsWith("#")) {
                val resolved = try { URI(baseUrl).resolve(line).toString() } catch (e: Exception) { null }
                if (resolved != null) variants.add(HlsVariant(resolved, height, bandwidth))
                pending = false
            }
        }

        if (variants.isEmpty()) return listOf(HlsVariant(baseUrl, hint(label), 0L))
        return variants.distinctBy { it.url }.sortedByDescending { it.height }
    }

    private fun hint(label: String): Int {
        val m = Regex("(?i)([0-9]{3,4})\\s*[x×]\\s*([0-9]{3,4})").find(label)
        if (m != null) return minOf(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        if (Regex("(?i)\\b(4k|uhd|2160p?)\\b").containsMatchIn(label)) return 2160
        if (Regex("(?i)\\b(1920|1080p?|fhd|full\\s*hd)\\b").containsMatchIn(label)) return 1080
        if (Regex("(?i)\\b(1280|720p?|hd)\\b").containsMatchIn(label)) return 720
        if (Regex("(?i)\\b(576p?|sd)\\b").containsMatchIn(label)) return 576
        if (Regex("(?i)\\b480p?\\b").containsMatchIn(label)) return 480
        return 0
    }
}

// ========================================================================
// DATA CLASS'LAR
// ========================================================================

data class SportsChannel(
    val id: String,
    val title: String,
    val category: String,
    val player: String,
    val target: String,
    val poster: String? = null
)

data class StreamSession(
    val url: String,
    val token: String
)

data class PlayerConfig(
    val authUrl: String,
    val siteName: String
)

data class HlsVariant(
    val url: String,
    val height: Int,
    val bandwidth: Long
)
