package com.safakstream.inattv

import com.lagradost.cloudstream3.ErrorLoadingException
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

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

    private fun stableUrl(channel: SportsChannel): String {
        val id = URLEncoder.encode(channel.id, "UTF-8")
        val title = URLEncoder.encode(channel.title, "UTF-8")
        return "${DomainResolver.GATEWAY}turkspor?id=$id&title=$title"
    }

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

    private suspend fun currentChannel(url: String): SportsChannel? {
        val id = SportsParser.queryParam(url, "id") ?: return null
        val snapshot = domains.resolve() ?: return null
        return snapshot.channels.firstOrNull { it.id == id }
    }

    override suspend fun load(url: String): LoadResponse {
        val channel = currentChannel(url) ?: return newLiveStreamLoadResponse(
            name = "Bilinmeyen Kanal",
            url = url,
            dataUrl = url
        ) { }

        val brand = ChannelBranding.forChannel(channel)

        return newLiveStreamLoadResponse(
            name = brand.title,
            url = url,
            dataUrl = stableUrl(channel)
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
        val channel = currentChannel(data) ?: return false
        val snapshot = domains.resolve() ?: return false

        // === M3U8 direkt akış ===
        if (channel.target == "m3u8") {
            val streamUrl = SportsParser.httpsUrl(channel.player) ?: return false
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "${ChannelBranding.forChannel(channel).title} (m3u8)",
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = snapshot.url
                    this.headers = mapOf("User-Agent" to ua)
                    this.quality = Qualities.Unknown.value
                }
            )
            return true
        }

        // === viptv akış ===
        if (channel.target != "viptv") return false

        val headers = mutableMapOf(
            "User-Agent" to ua,
            "Origin" to snapshot.url.trimEnd('/'),
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json, text/javascript, */*; q=0.01"
        )

        try {
            // 1) authUrl'ye POST: channel=player
            val playlist = app.post(
                snapshot.config.authUrl,
                headers = headers,
                data = mapOf("channel" to channel.player)
            )

            val session = SportsParser.streamSession(playlist.text)
                ?: throw ErrorLoadingException("Kaynak bu kanal için yayın adresi vermedi. Maç saatinde veya WARP ile tekrar deneyin.")

            // 2) Token varsa headers'a ekle
            if (session.token.isNotEmpty()) {
                headers["usertoken"] = session.token
                headers["pl"] = snapshot.config.siteName
            }

            // 3) session.url'e GET → gerçek m3u8 URL'i
            val stream = app.get(session.url, headers = headers).text.trim()

            if (stream.isBlank()) {
                throw ErrorLoadingException("Yayın adresi boş döndü.")
            }

            // 4) Stream URL'ini m3u8 olarak ver
            val finalUrl = SportsParser.httpsUrl(stream) ?: stream
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = ChannelBranding.forChannel(channel).title,
                    url = finalUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = snapshot.url
                    this.headers = headers
                    this.quality = Qualities.Unknown.value
                }
            )
            return true

        } catch (e: ErrorLoadingException) {
            throw e
        } catch (t: Throwable) {
            return false
        }
    }
}
