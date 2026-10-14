package com.safakstream.inattv

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
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
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

class InatTV(
    private val domains: DomainResolver,
    private val artwork: ChannelArtwork
) : MainAPI() {

    override var mainUrl = DomainResolver.GATEWAY
    override var name = "İnat TV"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val mainPage = mainPageOf(
        "all" to "Canlı Spor"
    )

    /**
     * Kanal için kalıcı URL üretir: inatgiris.com/turkspor?id=X&title=Y
     */
    private fun stableUrl(channel: SportsChannel): String {
        val id = URLEncoder.encode(channel.id, "UTF-8")
        val title = URLEncoder.encode(channel.title, "UTF-8")
        return "${DomainResolver.GATEWAY}turkspor?id=$id&title=$title"
    }

    /**
     * SportsChannel'ı LiveSearchResponse'a çevirir
     */
    private suspend fun SportsChannel.toSearchResponse(): SearchResponse {
        val brand = ChannelBranding.forChannel(this)
        return newLiveSearchResponse(
            name = brand.title,
            url = stableUrl(this),
            type = TvType.Live
        ) {
            this.posterUrl = artwork.poster(this@toSearchResponse)
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val snapshot = domains.resolve() ?: return newHomePageResponse(request.name, emptyList())
        val items = snapshot.channels.map { it.toSearchResponse() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val snapshot = domains.resolve() ?: return emptyList()
        val q = query.trim().lowercase()
        return snapshot.channels
            .filter {
                it.title.lowercase().contains(q) ||
                it.id.lowercase().contains(q)
            }
            .map { it.toSearchResponse() }
    }

    /**
     * URL'den kanalı bulur. URL formatı: inatgiris.com/turkspor?id=X&title=Y
     */
    private suspend fun currentChannel(url: String): SportsChannel? {
        val id = SportsParser.queryParam(url, "id") ?: return null
        val snapshot = domains.resolve() ?: return null
        return snapshot.channels.firstOrNull { it.id == id }
    }

    override suspend fun load(url: String): LoadResponse {
        val channel = currentChannel(url)
            ?: return newLiveStreamLoadResponse(
                name = "Bilinmeyen Kanal",
                url = url,
                dataUrl = url,
                referer = DomainResolver.GATEWAY
            ) { }

        val brand = ChannelBranding.forChannel(channel)

        return newLiveStreamLoadResponse(
            name = brand.title,
            url = stableUrl(channel),
            dataUrl = channel.player, // stream URL'i veya viptv ID'si
            referer = DomainResolver.GATEWAY
        ) {
            this.posterUrl = artwork.poster(channel)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            // dataUrl = "viptv" ise player = sayısal ID; stream URL'i auth ile alınır
            // "m3u8" ise player zaten direkt URL
            // Şimdilik direkt URL olarak yükleyelim
            if (data.startsWith("http://") || data.startsWith("https://")) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = data
                    ) {
                        this.referer = DomainResolver.GATEWAY
                        this.quality = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                    }
                )
                return true
            }
            return false
        } catch (t: Throwable) {
            return false
        }
    }
}
