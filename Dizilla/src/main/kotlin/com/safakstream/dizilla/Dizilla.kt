package com.safakstream.dizilla

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

@CloudstreamPlugin
class DizillaPlugin : Plugin() {
    override fun load(context: android.content.Context) {
        registerMainAPI(Dizilla())
    }
}

class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla.now"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/tr-dublaj-diziler" to "Türkçe Dublaj Diziler",
        "$mainUrl/yerli-diziler" to "Yerli Diziler",
        "$mainUrl/yabanci-diziler" to "Yabancı Diziler",
        "$mainUrl/filmler" to "Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"
        val document = app.get(url).text
        val home = parseItems(document)
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/arama?q=${URLEncoder.encode(query, "UTF-8")}"
        val response = app.get(url).text
        return parseItems(response)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).text
        
        val title = Regex("\\\\\"title\\\\\":\\\\\"(.*?)\\\\\"").find(document)?.groupValues?.get(1) ?: "Bilinmeyen Başlık"
        val description = Regex("\\\\\"description\\\\\":\\\\\"(.*?)\\\\\"").find(document)?.groupValues?.get(1) ?: ""
        val poster = Regex("\\\\\"poster\\\\\":\\\\\"(.*?)\\\\\"").find(document)?.groupValues?.get(1) ?: ""
        
        if (url.contains("/film/") || url.contains("/filmler/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }

        val episodes = mutableListOf<Episode>()
        val addedUrls = mutableSetOf<String>()
        val episodeRegex = Regex("(\\\\\"/.*?sezon-.*?bolum\\\\\")")
        val matches = episodeRegex.findAll(document)
        
        var counter = 1
        matches.forEach { match ->
            var epUrl = match.groupValues[1].replace("\\\"", "")
            if (epUrl.startsWith("/")) epUrl = "$mainUrl$epUrl"
            
            val epName = epUrl.substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }
            
            if (addedUrls.add(epUrl)) {
                episodes.add(newEpisode(epUrl) {
                    this.name = epName
                    this.episode = counter
                })
                counter++
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).text
        
        val iframeRegex = Regex("\\\\\"src\\\\\":\\\\\"(https://.*?iframe\\\\.php\\\\?v=.*?)\\\\\"")
        val iframeMatch = iframeRegex.find(document)
        
        if (iframeMatch != null) {
            val iframeUrl = iframeMatch.groupValues[1]
            loadExtractor(iframeUrl, mainUrl, subtitleCallback, callback)
        }
        return true
    }

    private fun parseItems(html: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        val itemRegex = Regex("\\\\\"title\\\\\":\\\\\"(.*?)\\\\\",\\\\\"slug\\\\\":\\\\\"(.*?)\\\\\",.*?\\\\\"poster\\\\\":\\\\\"(.*?)\\\\\"")
        
        itemRegex.findAll(html).forEach { match ->
            val title = match.groupValues[1]
            val slug = match.groupValues[2]
            val poster = match.groupValues[3]
            
            val fullUrl = if (slug.startsWith("http")) slug else "$mainUrl/$slug"
            
            items.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                this.posterUrl = poster
            })
        }
        return items
    }
}
