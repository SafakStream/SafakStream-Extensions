package com.safakstream.youtube

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YouTubePlaylistsProvider(sharedPrefs: SharedPreferences?) : YouTubeProvider(sharedPrefs) {
    override var name = "YouTube Playlists"
    override val hasMainPage = false
    override val SEARCH_CONTENT_FILTER = "playlists"

    private fun getPlaylistVideos(videos: List<StreamInfoItem>): List<Episode> {
        return videos.map { video ->
            newEpisode(video.url) {
                this.name = video.name
                this.posterUrl = video.thumbnails.lastOrNull()?.url
                this.runTime = (video.duration / 60).toInt()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val extractor = service.getPlaylistExtractor(url)
        extractor.forceLocalization(Localization(lang))
        extractor.forceContentCountry(ContentCountry(country))
        extractor.fetchPage()

        val playlistInfo = PlaylistInfo.getInfo(extractor)
        val banner = if (playlistInfo.banners.isNotEmpty()) {
            playlistInfo.banners.lastOrNull()?.url
        } else {
            playlistInfo.thumbnails.lastOrNull()?.url
        }

        val eps = playlistInfo.relatedItems.toMutableList()
        var hasNext = playlistInfo.hasNextPage()
        var count = 1
        var nextPage = playlistInfo.nextPage
        while (hasNext && count < 10) {
            val more = PlaylistInfo.getMoreItems(service, url, nextPage)
            eps.addAll(more.items)
            hasNext = more.hasNextPage()
            nextPage = more.nextPage
            count++
        }

        return newTvSeriesLoadResponse(playlistInfo.name, url, TvType.Others, getPlaylistVideos(eps)) {
            this.posterUrl = banner
        }
    }
}
