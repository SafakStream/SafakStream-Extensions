package com.safakstream.inattv

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
    override val hasQuickSearch = false
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
    private suspend fun channelToSearchResponse(channel: SportsChannel): SearchResponse {
        val brand = ChannelBranding.forChannel(channel)
        return newLiveSearchResponse(
            name = brand.title,
            url = stableUrl(channel),
            type = TvType.Live
        ) {
            this.posterUrl = artwork.poster(channel)
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val snapshot = domains.resolve() ?: return newHomePageResponse(request.name, emptyList())
        val items = snapshot.channels.map { channelToSearchResponse(it) }
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
            .map { channelToSearchResponse(it) }
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
            ) { }

        val brand = ChannelBranding.forChannel(channel)

        return newLiveStreamLoadResponse(
            name = brand.title,
            url = stableUrl(channel),
            dataUrl = channel.player,
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
            // dataUrl direkt URL ise (m3u8) yükle
            if (data.startsWith("http://") || data.startsWith("https://")) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = data,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = DomainResolver.GATEWAY
                        this.quality = Qualities.Unknown.value
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
