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
        // Sitedeki kanal listesini bulmak için <li> etiketleri içindeki <a> linklerini alıyoruz
        val home = document.select("li > a").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        return newHomePageResponse(HomePageList("Canlı TV Kanalları", home))
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href")
        // Sadece kanal sayfalarına giden linkleri alalım (örneğin /trt1-izle gibi)
        if (!href.startsWith("/") || href == "/" || href.contains("javascript")) return null
        
        // Kanal adı <a> etiketinin içindeki metindir
        val title = this.text().trim()
        if (title.isEmpty()) return null
        
        // Poster için site yapısında genellikle bir resim yok, şimdilik boş bırakıyoruz
        return newMovieSearchResponse(
            name = title,
            url = fixUrl(href),
            type = TvType.Movie
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        // Kanal sayfasındaki başlığı alıyoruz (genellikle h1 etiketindedir)
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
        try {
            val document = app.get(data).document
            val html = document.html()
            // HTML içinde m3u8 linki arayalım (Örn: https://...m3u8)
            val m3u8Regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
            val match = m3u8Regex.find(html)
            
            if (match != null) {
                val m3u8Url = match.value
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = data
                    }
                )
                return true
            }
        } catch (e: Exception) {
            // Hata olursa yutalım
        }
        return false
    }
}
