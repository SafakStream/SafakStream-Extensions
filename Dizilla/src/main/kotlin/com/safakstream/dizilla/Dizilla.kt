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
        "$mainUrl/turkce-dublaj-dizi-izle" to "Türkçe Dublaj Diziler",
        "$mainUrl/yerli-dizi-izle" to "Yerli Diziler",
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/anime-izle" to "Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Ana sayfa linklerinde sayfalama genelde ?page= veya /sayfa/ şeklindedir, şimdilik ilk sayfayı çekelim
        val url = if (page == 1) request.data else "${request.data}?page=$page"
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
        
        // JSON kaçışlı verileri temizle
        val title = Regex("\\\\\"title\\\\\":\\\\\"(.*?)\\\\\"").find(document)?.groupValues?.get(1) ?: "Bilinmeyen Başlık"
        val description = Regex("\\\\\"description\\\\\":\\\\\"(.*?)\\\\\"").find(document)?.groupValues?.get(1) ?: ""
        val poster = Regex("\\\\\"poster\\\\\":\\\\\"(.*?)\\\\\"").find(document)?.groupValues?.get(1)?.replace("\\\\", "") ?: ""
        
        // Eğer link film ise
        if (url.contains("/film/") || url.contains("/filmler/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }

        // Dizi ise bölümleri çek
        val episodes = mutableListOf<Episode>()
        val addedUrls = mutableSetOf<String>()
        
        // Sitedeki tüm bölüm linklerini yakala (Örn: \"/carrie-1-sezon-1-bolum\")
        val episodeRegex = Regex("\\\\\"href\\\\\":\\\\\"(/[a-zA-Z0-9-]+-\\\\d+-sezon-\\\\d+-bolum[^\"\\\\]*)\\\\\"")
        val matches = episodeRegex.findAll(document)
        
        var counter = 1
        matches.forEach { match ->
            var epUrl = match.groupValues[1]
            if (epUrl.startsWith("/")) epUrl = "$mainUrl$epUrl"
            
            if (addedUrls.add(epUrl)) {
                val epName = epUrl.substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }
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
        
        // Bölüm sayfasındaki iframe kaynağını bul
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
        
        // Mükemmel Regex: Başlık, Afiş ve Linki (/dizi/ veya /film/) sırasıyla yakalar
        val itemRegex = Regex("\\\\\"title\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"poster\\\\\":\\\\\"(.*?)\\\\\".*?\\\\\"href\\\\\":\\\\\"(/dizi/|/film/)([^\"\\\\]*)\\\\\"")
        
        itemRegex.findAll(html).forEach { match ->
            val title = match.groupValues[1]
            val poster = match.groupValues[2].replace("\\\\", "") 
            val typePath = match.groupValues[3] // /dizi/ veya /film/
            val slug = match.groupValues[4]
            
            val fullUrl = "$mainUrl$typePath$slug"
            val isMovie = typePath == "/film/"
            val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
            
            // Aynı içeriği iki kere eklemeyi önle
            if (items.none { it.url == fullUrl }) {
                if (isMovie) {
                    items.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                        this.posterUrl = poster
                    })
                } else {
                    items.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                    })
                }
            }
        }
        return items
    }
}
