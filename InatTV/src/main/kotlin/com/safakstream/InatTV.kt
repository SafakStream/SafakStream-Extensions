// SafakStream-Extensions/InatTV/src/main/kotlin/com/safakstream/InatTV.kt

package com.safakstream

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
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
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class InatTV : MainAPI() {
    override var mainUrl              = "https://www.inattvizle494.top"
    override var name                 = "InatTV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
        private const val TAG = "InatTV"
    }

    private val mapper by lazy {
        ObjectMapper().registerModule(KotlinModule.Builder().build()).also {
            it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    private var cachedChannels: List<SportsChannel> = emptyList()
    private var cachedConfig: PlayerConfig? = null

    override val mainPage = mainPageOf(
        "canli-maclar" to "Canli Maclar",
        "tv-kanallari" to "7/24 TV Kanallari"
    )

    // ========================================================================
    // HTML'DEN CHANNEL + CONFIG ÇIKARMA
    // ========================================================================

    private suspend fun loadSite(force: Boolean = false): Pair<List<SportsChannel>, PlayerConfig> {
        if (!force && cachedChannels.isNotEmpty() && cachedConfig != null) {
            return cachedChannels to cachedConfig!!
        }

        val doc = app.get(mainUrl, headers = mapOf("User-Agent" to UA)).document
        val html = doc.html()

        // 1. Kanallar (channel-slider icinde)
        val channels = doc.select("#channel-slider .channel-item").mapNotNull { el ->
            val rawId = el.attr("data-url").removePrefix("#").removeSuffix("-canli-izle")
            if (!Regex("[a-zA-Z0-9_-]{1,80}").matches(rawId)) return@mapNotNull null

            val target = el.attr("data-target").trim()
            val source = el.attr("data-source").trim()
            val title = el.attr("data-name").trim()
            if (title.isBlank()) return@mapNotNull null

            val poster = el.selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else fixUrl(it)
            }

            when (target) {
                "viptv" -> if (Regex("[0-9]{1,12}").matches(source))
                    SportsChannel(rawId, title, "Spor Kanallari", source, target, poster) else null
                "m3u8" -> if (source.startsWith("http"))
                    SportsChannel(rawId, title, "Spor Kanallari", source, target, poster) else null
                "frame" -> SportsChannel(rawId, title, "Spor Kanallari", source, target, poster)
                else -> null
            }
        }.distinctBy { it.id }

        // 2. Config (phptojs blogu)
        val phptojsMatch = Regex("var\\s+phptojs\\s*=\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL).find(html)
        val data = phptojsMatch?.groupValues?.get(1) ?: ""

        val server = Regex("['\"]stream_server_cdn_url['\"]\\s*:\\s*['\"]([^'\"]*)['\"]").find(data)
            ?.groupValues?.get(1) ?: "/"
        val siteName = Regex("['\"]site_name['\"]\\s*:\\s*['\"]([^'\"]*)['\"]").find(data)
            ?.groupValues?.get(1) ?: "inatTV"

        val authUrl = if (server.startsWith("http")) {
            if (server.endsWith("/")) "${server}auth.php" else "$server/auth.php"
        } else {
            "$mainUrl/${server.trimStart('/')}auth.php".replace("//", "/").replace(":/", "://")
        }

        Log.d(TAG, "Channels: ${channels.size}, authUrl: $authUrl, siteName: $siteName")

        cachedChannels = channels
        cachedConfig = PlayerConfig(authUrl, siteName)
        return channels to cachedConfig!!
    }

    // ========================================================================
    // ANA SAYFA
    // ========================================================================

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (channels, _) = loadSite()

        val items: List<SearchResponse> = when (request.data) {
            "canli-maclar" -> {
                val doc = app.get(mainUrl, headers = mapOf("User-Agent" to UA)).document
                doc.select("ul.match-list li[data-source]").mapNotNull { it.toMatchItem() }
            }
            "tv-kanallari" -> channels.map { it.toSearchResponse() }
            else -> emptyList()
        }

        return newHomePageResponse(request.name, items)
    }

    // ========================================================================
    // ELEMENT -> SearchResponse
    // ========================================================================

    private fun Element.toMatchItem(): SearchResponse? {
        val rawName = attr("data-name")
        val name = if (rawName.isNotBlank()) Jsoup.parse(rawName).text()
                   else attr("data-title").substringBefore(" Canli Izle").trim()
        if (name.isBlank()) return null

        val sourcesRaw = attr("data-source")
        val target = attr("data-target").trim()

        // Kaynak formatini belirle
        val player: String
        val targetType: String

        if (sourcesRaw.startsWith("[")) {
            // Mac formati: JSON array
            val sources = try { mapper.readValue<List<Source>>(sourcesRaw) } catch (e: Exception) { emptyList() }
            val first = sources.firstOrNull() ?: return null
            player = first.link ?: return null
            targetType = first.type ?: "viptv"
        } else if (sourcesRaw.isNotBlank()) {
            // Kanal formati: direkt
            player = sourcesRaw
            targetType = target.ifBlank { "viptv" }
        } else {
            return null
        }

        val poster = attr("data-home_logo").ifBlank {
            selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else fixUrl(it)
            }
        }

        // data icinde `||` ile ayir
        val data = "$name||$player||$targetType"

        return newMovieSearchResponse(name, data, TvType.Live) {
            this.posterUrl = poster
        }
    }

    private fun SportsChannel.toSearchResponse(): SearchResponse {
        val data = "$title||$player||$target"
        return newMovieSearchResponse(title, data, TvType.Live) {
            this.posterUrl = poster
        }
    }

    // ========================================================================
    // ARAMA
    // ========================================================================

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val (channels, _) = loadSite()
        val doc = app.get(mainUrl, headers = mapOf("User-Agent" to UA)).document
        val matches = doc.select("ul.match-list li[data-source]").mapNotNull { it.toMatchItem() }
        val all = matches + channels.map { it.toSearchResponse() }

        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        return all.filter {
            it.name.lowercase().contains(q) || (it.url?.lowercase()?.contains(q) == true)
        }
    }

    // ========================================================================
    // LOAD (DETAY)
    // ========================================================================

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.split("||")
        if (parts.size < 3) return null

        val displayName = parts[0].trim()
        val player = parts[1].trim()
        val target = parts[2].trim()

        // Cloudstream URL bekliyor - gercek formatli URL olustur
        val stableUrl = "https://www.inattvizle494.top/watch" +
            "?name=" + URLEncoder.encode(displayName, "UTF-8") +
            "&player=" + URLEncoder.encode(player, "UTF-8") +
            "&target=" + URLEncoder.encode(target, "UTF-8")

        val description = when (target) {
            "viptv" -> "Canli yayin"
            "vip"   -> "Canli yayin"
            "m3u8"  -> "HLS yayin"
            "frame" -> "Gomulu yayin"
            else    -> "Canli yayin"
        }

        return newLiveStreamLoadResponse(
            name = displayName,      // Baslik: "beIN 1"
            url = stableUrl,         // Cloudstream'in gordugu URL
            dataUrl = url            // loadLinks'e gececek data
        ) {
            this.plot = description
        }
    }

    // ========================================================================
    // LOADLINKS - REFERANSIN TAM AKISI
    // ========================================================================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("||")
        if (parts.size < 3) {
            Log.e(TAG, "Gecersiz data: $data")
            return false
        }

        val name = parts[0].trim()
        val player = parts[1].trim()
        val target = parts[2].trim()

        Log.d(TAG, "loadLinks => name=$name, target=$target, player=$player")

        val (_, config) = loadSite()

        val headers = mutableMapOf(
            "User-Agent" to UA,
            "Origin" to mainUrl.trimEnd('/')
        )

        val streamUrl: String

        if (target == "viptv" || target == "vip") {
            val param = if (target == "viptv") "channel" else "id"

            val response = app.post(
                config.authUrl,
                headers = headers + mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01"
                ),
                referer = "$mainUrl/",
                data = mapOf(param to player)
            )

            Log.d(TAG, "auth.php response code=${response.code}")
            Log.d(TAG, "auth.php body=${response.text.take(500)}")

            if (response.code != 200) {
                Log.e(TAG, "auth.php hata kodu: ${response.code}")
                return false
            }

            val session = parseStreamSession(response.text)
            if (session == null) {
                Log.e(TAG, "StreamSession parse null")
                return false
            }

            Log.d(TAG, "session.url=${session.url}, token.len=${session.token.length}")

            if (session.token.isNotEmpty()) {
                headers["usertoken"] = session.token
                headers["pl"] = config.siteName
            }

            streamUrl = session.url
        } else {
            streamUrl = player
        }

        // .js / m3u8 dosyasini cek
        val playlist = try {
            app.get(streamUrl, headers = headers, referer = "$mainUrl/")
        } catch (e: Exception) {
            Log.e(TAG, "playlist get hata: ${e.message}")
            return false
        }

        Log.d(TAG, "playlist code=${playlist.code}, ilk50=${playlist.text.take(50).replace("\n", "\\n")}")

        if (playlist.code != 200 || !playlist.text.trimStart().startsWith("#EXTM3U")) {
            Log.e(TAG, "playlist EXTM3U degil veya hata")
            return false
        }

        // HLS varyantlarini cikar
        val variants = parseHlsVariants(playlist.text, playlist.url, name)
        val maxHeight = variants.maxOfOrNull { it.height } ?: 0
        val qualitySuffix = if (maxHeight > 0) " • ${maxHeight}p" else ""

        Log.d(TAG, "variants=${variants.size}, maxHeight=$maxHeight")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "$name$qualitySuffix",
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
    // YARDIMCI FONKSIYONLAR
    // ========================================================================

    private fun parseStreamSession(json: String): StreamSession? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("ERROR")) {
                Log.e(TAG, "auth.php ERROR: ${obj.optString("ERROR")}")
                return null
            }
            val url = obj.optString("URL", "")
            val token = obj.optString("TOKEN", "")
            if (url.isBlank()) return null
            if (token.length > 2048) return null
            if (token.contains('\n') || token.contains('\r')) return null
            StreamSession(url, token)
        } catch (e: Exception) {
            Log.e(TAG, "StreamSession parse hata: ${e.message}")
            null
        }
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
                val resolved = try {
                    URI(baseUrl).resolve(line).toString()
                } catch (e: Exception) { null }
                if (resolved != null) {
                    variants.add(HlsVariant(resolved, height, bandwidth))
                }
                pending = false
            }
        }

        if (variants.isEmpty()) {
            return listOf(HlsVariant(baseUrl, hint(label), 0L))
        }

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

@JsonIgnoreProperties(ignoreUnknown = true)
data class Source(
    @JsonProperty("ad")   val quality: String? = null,
    @JsonProperty("tip")  val type: String?    = null,
    @JsonProperty("link") val link: String?    = null
)

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
