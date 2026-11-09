package com.safakstream.trgoals

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
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

class TrGoalsProvider : MainAPI() {
    override var mainUrl = "https://www.trgoals183.top"
    override var name = "TrGoals"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    companion object {
        private const val TAG = "TrGoals"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private var cachedAuthUrl: String? = null
    private var cachedSiteName: String? = null
    private var cachedChannels: List<SportsChannel> = emptyList()

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Canlı Maçlar",
        "$mainUrl/#tv-kanallari" to "TV Kanalları"
    )

    // ========================================================================
    // CONFIG (phptojs + kanallar)
    // ========================================================================
    private suspend fun loadSite(): PlayerConfig {
        if (cachedAuthUrl != null && cachedSiteName != null && cachedChannels.isNotEmpty()) {
            return PlayerConfig(cachedAuthUrl!!, cachedSiteName!!)
        }

        val doc = app.get(mainUrl, headers = mapOf("User-Agent" to UA)).document
        val html = doc.html()

        // Kanallar
        cachedChannels = doc.select("#channel-slider .channel-item").mapNotNull { el ->
            val target = el.attr("data-target").trim()
            val source = el.attr("data-source").trim()
            val title = el.attr("data-name").trim()
            if (title.isBlank()) return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else fixUrl(it)
            }
            when (target) {
                "viptv" -> if (Regex("[0-9]{1,12}").matches(source))
                    SportsChannel(title, source, target, poster) else null
                "m3u8" -> if (source.startsWith("http"))
                    SportsChannel(title, source, target, poster) else null
                else -> null
            }
        }.distinctBy { it.player }

        // phptojs config
        val phptojsMatch = Regex("var\\s+phptojs\\s*=\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL).find(html)
        val data = phptojsMatch?.groupValues?.get(1) ?: ""

        val server = Regex("['\"]stream_server_cdn_url['\"]\\s*:\\s*['\"]([^'\"]*)['\"]").find(data)
            ?.groupValues?.get(1) ?: "/"
        val siteName = Regex("['\"]site_name['\"]\\s*:\\s*['\"]([^'\"]*)['\"]").find(data)
            ?.groupValues?.get(1) ?: "TrGoals"

        val authUrl = if (server.startsWith("http")) {
            server.trimEnd('/') + "/auth.php"
        } else {
            "$mainUrl/${server.trimStart('/')}auth.php".replace("//", "/").replace(":/", "://")
        }

        cachedAuthUrl = authUrl
        cachedSiteName = siteName
        Log.d(TAG, "authUrl=$authUrl siteName=$siteName channels=${cachedChannels.size}")
        return PlayerConfig(authUrl, siteName)
    }

    // ========================================================================
    // ANA SAYFA
    // ========================================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        loadSite()

        val items: List<SearchResponse> = when {
            request.name.contains("Maç", ignoreCase = true) -> {
                val doc = app.get(mainUrl, headers = mapOf("User-Agent" to UA)).document
                doc.select("ul.match-list li[data-source]").mapNotNull { it.toMatchItem() }
            }
            else -> cachedChannels.map { it.toSearchResponse() }
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        loadSite()
        val doc = app.get(mainUrl, headers = mapOf("User-Agent" to UA)).document
        val matches = doc.select("ul.match-list li[data-source]").mapNotNull { it.toMatchItem() }
        val all = matches + cachedChannels.map { it.toSearchResponse() }
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()
        return all.filter {
            it.name.contains(q, ignoreCase = true) || (it.url.orEmpty().contains(q, ignoreCase = true))
        }
    }

    // ========================================================================
    // ELEMENT -> SearchResponse
    // ========================================================================
    private fun Element.toMatchItem(): SearchResponse? {
        val rawName = attr("data-name")
        val name = if (rawName.isNotBlank()) Jsoup.parse(rawName).text()
        else attr("data-title").substringBefore(" Canlı İzle").trim()
        if (name.isBlank()) return null

        val sourcesRaw = attr("data-source")
        val target = attr("data-target").trim()

        val first = try {
            val arr = JSONArray(sourcesRaw)
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            Source(o.optString("ad"), o.optString("tip"), o.optString("link"))
        } catch (e: Exception) {
            if (sourcesRaw.isNotBlank())
                Source(null, target.ifBlank { "viptv" }, sourcesRaw)
            else return null
        }

        val player = first.link ?: return null
        val targetType = if (first.type.isNullOrBlank()) "viptv" else first.type

        val poster = attr("data-home_logo").ifBlank {
            selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else fixUrl(it)
            }
        }

        val id = attr("data-url").removePrefix("#").removeSuffix("-canli-izle")
            .ifBlank { name.replace(" ", "-").lowercase() }

        val stableUrl = buildStableUrl(id, name, player, targetType)
        return newLiveSearchResponse(name, stableUrl, TvType.Live) {
            this.posterUrl = poster
        }
    }

    private fun SportsChannel.toSearchResponse(): SearchResponse {
        val stableUrl = buildStableUrl(player, title, player, target)
        return newLiveSearchResponse(title, stableUrl, TvType.Live) {
            this.posterUrl = poster
        }
    }

    // ========================================================================
    // LOAD (DETAY)
    // ========================================================================
    override suspend fun load(url: String): LoadResponse? {
        Log.d(TAG, "load() url=$url")
        val parsed = parseStableUrl(url) ?: return null
        val description = when (parsed.target) {
            "viptv", "vip" -> "Canlı yayın"
            "m3u8" -> "HLS yayın"
            "frame" -> "Gömülü yayın"
            else -> "Canlı yayın"
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
        Log.d(TAG, "loadLinks() data=$data")
        val parsed = parseStableUrl(data) ?: return false
        val config = loadSite()

        val headers = mutableMapOf(
            "User-Agent" to UA,
            "Origin" to mainUrl.trimEnd('/'),
            "Referer" to "$mainUrl/"
        )

        val streamUrl: String

        if (parsed.target == "viptv" || parsed.target == "vip") {
            val param = if (parsed.target == "viptv") "channel" else "id"
            val response = app.post(
                config.authUrl,
                headers = headers + mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01"
                ),
                referer = "$mainUrl/",
                data = mapOf(param to parsed.player)
            )
            Log.d(TAG, "auth.php code=${response.code} body=${response.text.take(300)}")
            if (response.code != 200) return false

            val session = parseStreamSession(response.text) ?: return false
            Log.d(TAG, "session.url=${session.url} token.len=${session.token.length}")
            if (session.token.isNotEmpty()) {
                headers["usertoken"] = session.token
                headers["pl"] = config.siteName
            }
            streamUrl = session.url
        } else {
            streamUrl = parsed.player
        }

        // .js veya m3u8 dosyasını çek
        val playlist = try {
            app.get(streamUrl, headers = headers, referer = "$mainUrl/")
        } catch (e: Exception) {
            Log.e(TAG, "playlist hata: ${e.message}")
            return false
        }

        Log.d(TAG, "playlist code=${playlist.code} ilk50=${playlist.text.take(50).replace("\n", "\\n")}")
        if (playlist.code != 200 || !playlist.text.trimStart().startsWith("#EXTM3U")) {
            Log.e(TAG, "playlist EXTM3U değil veya hata")
            return false
        }

        val variants = parseHlsVariants(playlist.text, playlist.url, parsed.title)
        val maxHeight = variants.maxOfOrNull { it.height } ?: 0
        val qualitySuffix = if (maxHeight > 0) " • ${maxHeight}p" else ""

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "${parsed.title}$qualitySuffix",
                url = playlist.url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.headers = headers
                this.quality = if (maxHeight > 0) maxHeight else Qualities.Unknown.value
            }
        )
        return true
    }

    // ========================================================================
    // YARDIMCI FONKSİYONLAR
    // ========================================================================
    private fun parseStreamSession(json: String): StreamSession? = try {
        val obj = JSONObject(json)
        if (obj.has("ERROR")) {
            Log.e(TAG, "auth ERROR: ${obj.optString("ERROR")}")
            null
        } else {
            val url = obj.optString("URL", "")
            val token = obj.optString("TOKEN", "")
            if (url.isBlank() || token.length > 2048 ||
                token.contains('\n') || token.contains('\r')) null
            else StreamSession(url, token)
        }
    } catch (e: Exception) {
        Log.e(TAG, "session parse hata: ${e.message}")
        null
    }

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

    private fun buildStableUrl(id: String, title: String, player: String, target: String): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "$mainUrl/__trgoals__?id=${enc(id)}&title=${enc(title)}&player=${enc(player)}&target=${enc(target)}"
    }

    private fun parseStableUrl(url: String): ParsedUrl? {
        if (!url.contains("__trgoals__")) return null
        return try {
            val q = URI(url).rawQuery ?: return null
            val map = q.split("&").associate {
                val (k, v) = it.split("=", limit = 2)
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }
            ParsedUrl(
                map["id"] ?: return null,
                map["title"] ?: return null,
                map["player"] ?: return null,
                map["target"] ?: return null
            )
        } catch (e: Exception) { null }
    }

    // ========================================================================
    // DATA CLASS'LAR
    // ========================================================================
    data class Source(val quality: String?, val type: String?, val link: String?)
    data class SportsChannel(val title: String, val player: String, val target: String, val poster: String?)
    data class StreamSession(val url: String, val token: String)
    data class PlayerConfig(val authUrl: String, val siteName: String)
    data class HlsVariant(val url: String, val height: Int, val bandwidth: Long)
    data class ParsedUrl(val id: String, val title: String, val player: String, val target: String)
}
