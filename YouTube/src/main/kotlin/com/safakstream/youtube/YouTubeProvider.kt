package com.safakstream.youtube

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.InfoItem
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
import java.util.Locale

// --- REKLAM İÇİN EKLENEN KÜTÜPHANELER ---
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.cloudstream3.CommonActivity

open class YouTubeProvider(
    private val sharedPrefs: SharedPreferences?
) : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val supportedTypes = setOf(TvType.Others)
    override var lang = "tr"
    override val hasMainPage = true
    override var sequentialMainPage = true

    var country: String = "TR"

    open val SEARCH_CONTENT_FILTER = "videos"

    val service = ServiceList.YouTube

    init {
        val language = sharedPrefs?.getString("language", "tr") ?: "tr"
        country = sharedPrefs?.getString("country", "TR") ?: "TR"
        lang = if (language.isBlank()) "tr" else language
        if (country.isBlank()) country = "TR"

        NewPipe.setupLocalization(Localization(lang), ContentCountry(country))
    }

    // ========================================================================
    // MODERN REKLAM / DESTEK FONKSİYONU
    // ========================================================================
    fun showSupportDialog(context: Context) {
        val prefs = context.getSharedPreferences("SafakStreamOrtakHafiza", Context.MODE_PRIVATE)
        val lastShown = prefs.getLong("son_reklam_zamani", 0)
        val currentTime = System.currentTimeMillis()

        // 60 Dakika = 60 * 60 * 1000 = 3.600.000 ms
        if (currentTime - lastShown > 3600000L) { 
            
            val dialog = Dialog(context)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val mainLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(70, 70, 70, 70)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#212429"))
                    cornerRadius = 60f
                    setStroke(2, Color.parseColor("#33363D"))
                }
                background = bg
            }

            val title = TextView(context).apply {
                text = "SafakStream'i Destekle ❤️"
                setTextColor(Color.WHITE)
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            }
            mainLayout.addView(title)

            val subtitle = TextView(context).apply {
                text = "Tamamen Ücretsiz Eklentiler"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 15)
            }
            mainLayout.addView(subtitle)

            val desc = TextView(context).apply {
                text = "Bu eklentiler sizin için tamamen ücretsizdir ve her zaman öyle kalacaktır.\n\nEmeğime destek olmak için bir reklam izleyebilirsiniz. Her küçük katkı benim için çok değerlidir, teşekkürler!"
                setTextColor(Color.parseColor("#C5C5C5"))
                textSize = 14f
                setLineSpacing(15f, 1.1f)
                setPadding(0, 0, 0, 60)
            }
            mainLayout.addView(desc)

            val buttonLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val btnLater = TextView(context).apply {
                text = "DAHA SONRA"
                setTextColor(Color.parseColor("#7A9CFF"))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(30, 30, 30, 30)
                val bg = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(3, Color.parseColor("#5C85FF"))
                    cornerRadius = 100f
                }
                background = bg
                setOnClickListener { dialog.dismiss() }
            }
            
            val laterParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            laterParams.marginEnd = 20
            buttonLayout.addView(btnLater, laterParams)

            val btnWatchAd = TextView(context).apply {
                text = "REKLAM İZLE\nVE DESTEK OL"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(30, 20, 30, 20)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#22449B"))
                    cornerRadius = 100f
                }
                background = bg
                setOnClickListener {
                    val adUrl = "https://link-center.net/10612612/WsR8DGfNtSq0"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adUrl))
                    context.startActivity(intent)
                    dialog.dismiss()
                }
            }
            
            val adParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            buttonLayout.addView(btnWatchAd, adParams)

            mainLayout.addView(buttonLayout)

            dialog.setContentView(mainLayout)
            dialog.show()
            
            prefs.edit().putLong("son_reklam_zamani", currentTime).apply()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        
        // --- REKLAM TETİKLEYİCİSİ ---
        CommonActivity.activity?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                showSupportDialog(ctx)
            }
        }
        // ---------------------------

        val isTrendingEnabled = sharedPrefs?.getBoolean("trending", true) ?: true
        val sections = mutableListOf<HomePageList>()

        if (isTrendingEnabled) {
            getTrendingVideoUrls(page)?.let { sections.add(it) }
        }

        if (sections.isEmpty()) {
            sections.add(HomePageList("Ayarlardan bölüm ekleyin", emptyList()))
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
            .map { item ->
                newMovieSearchResponse(item.name, item.url, TvType.Others) {
                    this.posterUrl = item.thumbnails.lastOrNull()?.url
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

        val finalResults = pageResults.mapNotNull { item ->
            when (item.infoType) {
                InfoItem.InfoType.STREAM -> newMovieSearchResponse(item.name, item.url, TvType.Others) {
                    this.posterUrl = item.thumbnails.lastOrNull()?.url
                }
                InfoItem.InfoType.PLAYLIST, InfoItem.InfoType.CHANNEL -> newTvSeriesSearchResponse(item.name, item.url, TvType.Others) {
                    this.posterUrl = item.thumbnails.lastOrNull()?.url
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

        val searchResponses = videos.map { item ->
            newMovieSearchResponse(item.name, item.url, TvType.Others) {
                this.posterUrl = item.thumbnails.lastOrNull()?.url
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

        val searchResponses = videos.map { item ->
            newMovieSearchResponse(item.name, item.url, TvType.Others) {
                this.posterUrl = item.thumbnails.lastOrNull()?.url
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
