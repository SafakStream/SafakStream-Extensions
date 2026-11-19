package com.safakstream.ardaspor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class ArdaSporProvider : MainAPI() {
    override var mainUrl = "https://ardaspor40.top"
    override var name = "ArdaSpor"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val home = mutableListOf<HomePageList>()

        val categories = listOf("Futbol", "Basketbol", "Tenis", "TV")
        
        for (cat in categories) {
            val items = document.select("div.match-category[data-category='$cat'] a.match-item").map { element ->
                val title = element.attr("data-title")
                val href = fixUrl(element.attr("href"))
                val image = "https://ardaspor40.top/dimg/89320ardafav.png"
                
                newLiveSearchResponse(
                    name = title,
                    url = href
                ) {
                    this.posterUrl = image
                }
            }
            if (items.isNotEmpty()) {
                home.add(HomePageList(cat, items))
            }
        }
        
        return newHomePageResponse(home)
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(
            name = "Canlı Yayın",
            url = url,
            dataUrl = url
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "ArdaSpor WebView",
                url = data,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = mainUrl
            }
        )
        return true
    }
}
