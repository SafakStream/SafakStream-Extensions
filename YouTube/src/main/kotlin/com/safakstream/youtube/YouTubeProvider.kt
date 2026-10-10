package com.safakstream.youtube

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.loadExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

open class YouTubeProvider(
    private val sharedPrefs: SharedPreferences?
) : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val supportedTypes = setOf(TvType.Others)
    override var lang = "it"
    override val hasMainPage = true
    override val sequentialMainPage = true

    var country: String = "IT"
    val SEARCH_CONTENT_FILTER = "videos"

    val service = ServiceList.YouTube

    init {
        val language = sharedPrefs?.getString("language", "it") ?: "it"
        country = sharedPrefs?.getString("country", "IT") ?: "IT"
        lang = if (language.isBlank()) "it" else language
        if (country.isBlank()) country = "IT"

        NewPipe.setupLocalization(Localization(lang), ContentCountry(country))
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isTrendingEnabled = sharedPrefs?.getBoolean("trending", true) ?: true
        val sections = mutableListOf<HomePageList>()

        if (isTrendingEnabled) {
            getTrendingVideoUrls(page)?.let { sections.add(it) }
        }

        val savedPlaylistsJson = sharedPrefs?.getString("savedLists", null)
        val savedPlaylists = if (savedPlaylistsJson.isNullOrBlank()) {
            // Varsayılan veya boş liste
            emptyList()
        } else {
            // JSON parse işlemi (basitleştirildi)
            emptyList() // Bu kısmı daha sonra dolduracağız
        }

        if (savedPlaylists.isNotEmpty()) {
            val list = savedPlaylists.map { /* Playlistleri yükle */ }
            // Bu kısım orijinal kodda karmaşık, şimdilik es geçiyoruz.
        }

        if (sections.isEmpty()) {
            sections.add(HomePageList("There are no sections to load. Go to the settings to add them", emptyList()))
        }

        return newHomePageResponse(sections, true)
    }

    fun getTrendingVideoUrls(page: Int): HomePageList? {
        val kiosks = service.kioskList
        val trendingsUrl = kiosks.defaultKioskExtractor.url
        val infoItem = KioskInfo.getInfo(service, trendingsUrl)

        val videos = if (page == 1) {
            infoItem.relatedItems.toMutableList()
        } else {
            mutableListOf()
        }

        if (page > 1) {
            var hasNext = infoItem.hasNextPage()
            if (!hasNext) return null
            var count = 1
            var nextPage = infoItem.nextPage
            while (count < page && hasNext) {
                val more = KioskInfo.getMoreItems(service, trendingsUrl, nextPage)
                if (count == page - 1) {
                    videos.addAll(more.items)
                }
                hasNext = more.hasNextPage()
                nextPage = more.nextPage
                count++
            }
        }

        val searchResponses = videos
            .filter { !it.isShortFormContent }
            .map {
                newMovieSearchResponse(it.name, it.url, TvType.Others) {
                    this.posterUrl = it.thumbnails.lastOrNull()?.url
                }
            }
        return HomePageList("Trending", searchResponses, true)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val handlerFactory = service.searchQHFactory
        val pageResults = mutableListOf<InfoItem>()
        var hasNextPage = true

        if (page < 2) {
            SEARCH_HANDLER = handlerFactory.fromQuery(query, listOf(SEARCH_CONTENT_FILTER), null)
            val extractor = service.getSearchExtractor(SearchQueryHandler(SEARCH_HANDLER as ListLinkHandler))
            extractor.forceLocalization(Localization(lang))
            extractor.forceContentCountry(ContentCountry(country))
            extractor.fetchPage()

            val searchInfo = SearchInfo.getInfo(extractor)
            pageResults.addAll(searchInfo.relatedItems)
            SEARCH_PAGE = searchInfo.nextPage
            hasNextPage = searchInfo.hasNextPage()
        }

        if (page > 1) {
            val more = SearchInfo.getMoreItems(service, SEARCH_HANDLER, SEARCH_PAGE)
            pageResults.addAll(more.items)
            hasNextPage = more.hasNextPage()
            SEARCH_PAGE = more.nextPage
        }

        val finalResults = pageResults.mapNotNull {
            when (it.infoType) {
                InfoItem.InfoType.STREAM -> newMovieSearchResponse(it.name, it.url, TvType.Others) {
                    this.posterUrl = it.thumbnails.lastOrNull()?.url
                }
                InfoItem.InfoType.PLAYLIST, InfoItem.InfoType.CHANNEL -> newTvSeriesSearchResponse(it.name, it.url, TvType.Others) {
                    this.posterUrl = it.thumbnails.lastOrNull()?.url
                }
                else -> null
            }
        }

        return newSearchResponseList(finalResults, hasNextPage)
    }

    fun playlistToSearchResponseList(url: String, page: Int?): HomePageList? {
        val extractor = service.getPlaylistExtractor(url)
        extractor.forceLocalization(Localization(lang))
        extractor.forceContentCountry(ContentCountry(country))
        extractor.fetchPage()

        val playlistInfo = PlaylistInfo.getInfo(extractor)
        val videos = if (page == 1) {
            playlistInfo.relatedItems.toMutableList()
        } else {
            mutableListOf()
        }

        if (page != null && page > 1) {
            var hasNext = playlistInfo.hasNextPage()
            if (!hasNext) return null
            var count = 1
            var nextPage = playlistInfo.nextPage
            while (count < page && hasNext) {
                val more = PlaylistInfo.getMoreItems(service, url, nextPage)
                if (count == page - 1) {
                    videos.addAll(more.items)
                }
                hasNext = more.hasNextPage()
                nextPage = more.nextPage
                count++
            }
        }

        val searchResponses = videos.map {
            newMovieSearchResponse(it.name, it.url, TvType.Others) {
                this.posterUrl = it.thumbnails.lastOrNull()?.url
            }
        }
        return HomePageList("${playlistInfo.uploaderName}: ${playlistInfo.name}", searchResponses, true)
    }

    fun channelToSearchResponseList(url: String, page: Int?): HomePageList? {
        val extractor = service.getChannelExtractor(url)
        extractor.forceLocalization(Localization(lang))
        extractor.forceContentCountry(ContentCountry(country))
        extractor.fetchPage()

        val channelInfo = ChannelInfo.getInfo(extractor)
        val tabsLinkHandlers = channelInfo.tabs
        val tabs = tabsLinkHandlers.map { ChannelTabInfo.getInfo(service, it) }
        val videoTab = tabs.first { it.name == "videos" }

        val videos = if (page == 1) {
            videoTab.relatedItems.toMutableList()
        } else {
            mutableListOf()
        }

        if (page != null && page > 1) {
            var hasNext = videoTab.hasNextPage()
            if (!hasNext) return null
            var count = 1
            var nextPage = videoTab.nextPage
            while (count < page && hasNext) {
                val videoTabHandler = tabsLinkHandlers.first { it.url.endsWith("/videos") }
                val more = ChannelTabInfo.getMoreItems(service, videoTabHandler, nextPage)
                if (count == page - 1) {
                    videos.addAll(more.items)
                }
                hasNext = more.hasNextPage()
                nextPage = more.nextPage
                count++
            }
        }

        val searchResponses = videos.map {
            newMovieSearchResponse(it.name, it.url, TvType.Others) {
                this.posterUrl = it.thumbnails.lastOrNull()?.url
            }
        }
        return HomePageList(channelInfo.name, searchResponses, true)
    }

    override suspend fun load(url: String): LoadResponse? {
        val extractor = service.getStreamExtractor(url)
        extractor.fetchPage()
        val videoInfo = StreamInfo.getInfo(extractor)
        val views = "👀: ${formatThousands(videoInfo.viewCount)}"
        val likes = "👍: ${formatThousands(videoInfo.likeCount)}"
        val length = videoInfo.duration / 60

        return newMovieLoadResponse(videoInfo.name, url, TvType.Others, url) {
            this.posterUrl = videoInfo.thumbnails.lastOrNull()?.url
            this.plot = "${views}\n${likes}"
            this.duration = length.toInt()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, null, subtitleCallback, callback)
        return true
    }

    protected fun formatThousands(bigNumber: Long): String {
        return String.format(Locale.US, "%,d", bigNumber).replace(',', ' ')
    }

    companion object {
        const val MAIN_URL = "https://www.youtube.com"
        var SEARCH_HANDLER: SearchQueryHandler? = null
        var SEARCH_PAGE: Page? = null
    }
}
