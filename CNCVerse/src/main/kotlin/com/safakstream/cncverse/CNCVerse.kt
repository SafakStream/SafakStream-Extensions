package com.safakstream.cncverse

import android.content.Context
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.delay

class CNCVerse : MainAPI() {
    override var mainUrl = "https://net52.cc"
    override var name = "CNC Verse"
    override val hasMainPage = false
    override var lang = "en"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        return null
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val ctx = getContext() as? Context

        // Cookie yoksa → Verify Activity aç, cookie gelene kadar bekle
        if (ctx != null && !CNCVerseCookieStore.isValid(ctx)) {
            CNCVerseUtils.startVerify(ctx)

            // 60 saniye boyunca cookie'yi bekle
            repeat(120) {
                delay(500)
                if (CNCVerseCookieStore.isValid(ctx)) {
                    return CNCVerseUtils.searchNetflix(this, query)
                }
            }
            return null
        }

        return CNCVerseUtils.searchNetflix(this, query)
    }

    override suspend fun load(url: String): LoadResponse? {
        return CNCVerseUtils.loadDetail(this, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return CNCVerseUtils.loadVideo(this, data, callback, subtitleCallback)
    }
}
