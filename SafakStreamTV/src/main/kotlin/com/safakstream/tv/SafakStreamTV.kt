package com.safakstream.tv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

@CloudstreamPlugin
class SafakStreamTVPlugin : Plugin() {
    override fun load(context: android.content.Context) {
        registerMainAPI(SafakStreamTV())
    }
}

class SafakStreamTV : MainAPI() {
    override var mainUrl = "https://www.canlitv.you"
    override var name = "SafakStream TV"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasDownloadSupport = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/tr").document
        val home = document.select("li > a").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        return newHomePageResponse(HomePageList("Canlı TV Kanalları", home))
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href")
        if (!href.startsWith("/") || href == "/" || href.contains("javascript")) return null
        
        val title = this.text().trim()
        if (title.isEmpty()) return null
        
        val parent = this.parent()
        val imgElement = this.selectFirst("img") ?: parent?.selectFirst("img")
        
        val poster = imgElement?.attr("src")?.takeIf { it.isNotEmpty() } 
                  ?: imgElement?.attr("data-src")?.takeIf { it.isNotEmpty() } 
                  ?: ""

        return newMovieSearchResponse(
            name = title,
            url = fixUrl(href),
            type = TvType.Movie
        ) {
            this.posterUrl = fixUrlNull(poster)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: "Canlı Yayın"
        
        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            dataUrl = url
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Güncel ve çalışan sabit linkler
        val hardcodedLinks = mapOf(
            "TRT 1" to "https://tv-trt1.medya.trt.com.tr/master.m3u8",
            "ATV" to "https://mttwmcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_360p.m3u8",
            "Show TV" to "https://mttfbjine.turknet.ercdn.net/bpeytmnqyp/showtv/showtv_720p.m3u8"
        )

        val document = app.get(data).document
        val pageTitle = document.selectFirst("h1")?.text()?.trim() ?: ""
        val pageUrl = data.lowercase()

        var found = false
        for ((key, link) in hardcodedLinks) {
            // Kanal ismini hem başlıkta hem de URL'de arıyoruz
            if (pageTitle.contains(key, ignoreCase = true) || pageUrl.contains(key.replace(" ", "").lowercase())) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link,
                        type = ExtractorLinkType.M3U8
                    ) {
                        // Sunucunun kabul etmesi için gerekli başlıklar
                        this.referer = "https://www.canlitv.you/"
                        this.headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                            "Origin" to "https://www.canlitv.you"
                        )
                    }
                )
                found = true
                break
            }
        }
        return found
    }
}
