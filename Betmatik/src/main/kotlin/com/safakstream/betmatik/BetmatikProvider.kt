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
import com.lagradost.cloudstream3.network.CloudflareKiller
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

    // ========================================================================
    // LINK ÇIKARMA - m3u8 içeriğini indir, alt segmenti bul, ExoPlayer'a ver
    // ========================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.trim()
        if (id.isBlank()) return false

        val headers = mapOf(
            "User-Agent" to UA,
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl
        )

        try {
            val base = resolveBaseUrl()
            val streamUrl = "$base/$id/mono.m3u8"
            Log.d(TAG, "Ana m3u8 URL'i: $streamUrl")

            // CloudflareKiller ile m3u8 metnini indir (403'ü aşmak için)
            val m3u8Response = app.get(
                streamUrl,
                headers = headers,
                interceptor = CloudflareKiller()
            ).text

            Log.d(TAG, "m3u8 içeriği (ilk 5 satır): ${m3u8Response.lines().take(5).joinToString(" | ")}")

            // Eğer içerikte #EXT-X-STREAM-INF varsa master playlist'tir, alt linki çek
            var finalStreamUrl = streamUrl
            if (m3u8Response.contains("#EXT-X-STREAM-INF")) {
                val lines = m3u8Response.lines()
                var bestVariantUrl: String? = null
                var maxBandwidth = -1

                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.contains("#EXT-X-STREAM-INF")) {
                        val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        // Alt satırda URL var
                        if (i + 1 < lines.size && !lines[i + 1].startsWith("#")) {
                            val variantUrl = lines[i + 1].trim()
                            if (bandwidth > maxBandwidth) {
                                maxBandwidth = bandwidth
                                bestVariantUrl = variantUrl
                            }
                        }
                    }
                }

                if (bestVariantUrl != null) {
                    // Göreceli URL ise base ile birleştir
                    finalStreamUrl = if (bestVariantUrl.startsWith("http")) {
                        bestVariantUrl
                    } else {
                        val urlObj = java.net.URI(streamUrl)
                        "${urlObj.scheme}://${urlObj.host}${if (bestVariantUrl.startsWith("/")) "" else "/"}$bestVariantUrl"
                    }
                    Log.d(TAG, "Alt m3u8 bulundu: $finalStreamUrl (bw=$maxBandwidth)")
                }
            }

            Log.d(TAG, "ExoPlayer'a verilecek URL: $finalStreamUrl")

            callback(
                newExtractorLink(
                    source = this.name,
                    name = "${this.name} - $id",
                    url = finalStreamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
            return true
        } catch (e: Exception) {
            Log.e(TAG, "loadLinks hatası: ${e.message}")
            e.printStackTrace()
        }

        // Fallback: en azından doğrudan URL ver
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
                this.headers = headers
            }
        )
        return true
    }
}
