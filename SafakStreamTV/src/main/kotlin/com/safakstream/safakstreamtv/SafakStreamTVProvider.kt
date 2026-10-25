package com.safakstream.safakstreamtv

import android.content.Context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLDecoder
import java.net.URLEncoder

class SafakStreamTVProvider : MainAPI() {
    override var mainUrl = "https://github.com/SafakStream/SafakStream-Extensions"
    override var name = "SafakStream TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    data class Channel(
        val name: String,
        val logo: String,
        val url: String,
        val group: String
    )

    companion object {
        var appContext: Context? = null
        private var cachedChannels: List<Channel>? = null
    }

    private fun loadChannels(): List<Channel> {
        cachedChannels?.let { return it }

        val ctx = appContext ?: return emptyList()
        val text = try {
            ctx.assets.open("ulusal.m3u").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return emptyList()
        }

        val result = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingLogo: String = ""
        var pendingGroup: String = "DIGER"

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF")) {
                pendingLogo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1) ?: ""
                pendingGroup = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1) ?: "DIGER"
                pendingName = line.substringAfterLast(",").trim().ifBlank { null }
            } else if (!line.startsWith("#") && pendingName != null) {
                result.add(Channel(pendingName!!, pendingLogo, line, pendingGroup))
                pendingName = null
                pendingLogo = ""
                pendingGroup = "DIGER"
            }
        }

        cachedChannels = result
        return result
    }

    override val mainPage = mainPageOf(
        "TUMU" to "Tüm Kanallar",
        "ULUSAL" to "Ulusal Kanallar",
        "HABER" to "Haber Kanalları",
        "SPOR" to "Spor Kanalları",
        "MUZIK" to "Müzik Kanalları",
        "BELGESEL" to "Belgesel Kanalları",
        "DIGER" to "Diğer Kanallar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val all = loadChannels()
        val filtered = when (request.data) {
            "TUMU" -> all
            else -> all.filter { it.group == request.data }
        }
        val items = filtered.map { ch ->
            newLiveSearchResponse(ch.name, stableUrl(ch), TvType.Live) {
                this.posterUrl = ch.logo
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()
        return loadChannels().filter { it.name.lowercase().contains(q) }.map { ch ->
            newLiveSearchResponse(ch.name, stableUrl(ch), TvType.Live) {
                this.posterUrl = ch.logo
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val ch = findChannelByUrl(url) ?: return null
        return newLiveStreamLoadResponse(ch.name, url, url) {
            this.posterUrl = ch.logo
            this.plot = "SafakStream TV - ${ch.group} - Canlı Yayın"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ch = findChannelByUrl(data) ?: return false

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = ch.name,
                url = ch.url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    private fun stableUrl(ch: Channel): String {
        val key = URLEncoder.encode(ch.name, "UTF-8") + "|" + URLEncoder.encode(ch.url, "UTF-8")
        return "$mainUrl#safak-tv-$key"
    }

    private fun findChannelByUrl(url: String): Channel? {
        val marker = "#safak-tv-"
        val pos = url.indexOf(marker)
        if (pos < 0) return null
        val encoded = url.substring(pos + marker.length)
        val parts = encoded.split("|", limit = 2)
        if (parts.size != 2) return null
        val chName = URLDecoder.decode(parts[0], "UTF-8")
        val chUrl = URLDecoder.decode(parts[1], "UTF-8")
        return loadChannels().firstOrNull { it.name == chName && it.url == chUrl }
    }
}
