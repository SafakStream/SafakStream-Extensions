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
        // Resmi ve stabil m3u8 linkleri
        val hardcodedLinks = mapOf(
            "TRT 1" to "https://tv-trt1.medya.trt.com.tr/master_1080.m3u8",
            "Beyaz TV" to "https://beyaztv.daioncdn.net/beyaztv/beyaztv.m3u8?app=fcd5c66b-da9d-44ba-a410-4f34805c397d&ce=3",
            "Kanal 7" to "https://kanal7-live.daioncdn.net/kanal7/kanal7.m3u8",
            "Star TV" to "https://dogus.daioncdn.net/startv/startv.m3u8?ce=3&app=a20ac41e-bdc3-4aa1-934d-26b484480ac9&ppid=35c70907b4a59202569ff0e6f8d1a9f7",
            "Kanal D" to "https://streams.uzunmuhalefet.com/stream/6bdc8fb5-3e82-4b44-a0b1-6ab9fdc35d3f.m3u8",
            "Now TV" to "https://streams.uzunmuhalefet.com/stream/f9368c57-16e5-44a2-a559-46377a476b04.m3u8"
        )

        val document = app.get(data).document
        val pageTitle = document.selectFirst("h1")?.text()?.trim() ?: ""
        val pageUrl = data.lowercase()

        var found = false
        for ((key, link) in hardcodedLinks) {
            if (pageTitle.contains(key, ignoreCase = true) || pageUrl.contains(key.replace(" ", "").lowercase())) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link,
                        type = ExtractorLinkType.M3U8
                    ) {
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
