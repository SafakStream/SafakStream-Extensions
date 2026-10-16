package com.safakstream.cncverse

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

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

    override val mainPage = mainPageOf()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        // Şimdilik boş — provider'lar üzerinden gelecek
        return null
    }

    override suspend fun search(query: String): List<SearchResponse>? {
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
