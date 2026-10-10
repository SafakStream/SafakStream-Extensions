package com.safakstream.youtube

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class YouTubeChannelProvider(sharedPrefs: SharedPreferences?) : YouTubeProvider(sharedPrefs) {
    override var name = "YouTube Channels"
    override val hasMainPage = false
    override val SEARCH_CONTENT_FILTER = "channels"

    private fun getChannelVideos(channel: ChannelInfo): List<Episode> {
        val tabsLinkHandlers = channel.tabs
        val tabs = tabsLinkHandlers.map { ChannelTabInfo.getInfo(service, it) }
        val videoTab = tabs.first { it.name == "videos" }
        val videos = videoTab.relatedItems.mapNotNull {
            newEpisode(it.url) {
                this.name = it.name
                this.posterUrl = it.thumbnails.lastOrNull()?.url
            }
        }
        return videos.reversed()
    }

    override suspend fun load(url: String): LoadResponse? {
        val extractor = service.getChannelExtractor(url)
        extractor.forceLocalization(Localization(lang))
        extractor.forceContentCountry(ContentCountry(country))
        extractor.fetchPage()

        val channelInfo = ChannelInfo.getInfo(extractor)
        val avatars = try { channelInfo.avatars.lastOrNull()?.url } catch (e: Exception) { null }
        val banners = try { channelInfo.banners.lastOrNull()?.url } catch (e: Exception) { null }
        val tags = mutableListOf("Subscribers: ${formatThousands(channelInfo.subscriberCount)}")

        return newTvSeriesLoadResponse(channelInfo.name, url, TvType.Others, getChannelVideos(channelInfo)) {
            this.posterUrl = avatars
            this.backgroundPosterUrl = banners
            this.tags = tags
        }
    }
}
