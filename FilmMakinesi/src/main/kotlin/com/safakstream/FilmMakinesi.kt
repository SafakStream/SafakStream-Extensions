package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class FilmMakinesi : MainAPI() {
    override var mainUrl = "https://filmmakinesi.to"
    override var name = "FilmMakinesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) mainUrl else "$mainUrl/filmler-1/sayfa/$page/"
        val document = app.get(url).document

        val home = mutableListOf<HomePageList>()

        val movies = document.select("div.film-list div.item-relative > a.item").mapNotNull { it.toSearchResult() }
        if (movies.isNotEmpty()) {
            home.add(HomePageList("Son Filmler", movies))
        }

        val series = document.select("div.tv--section a.item").mapNotNull { it.toSearchResult() }
        if (series.isNotEmpty()) {
            home.add(HomePageList("Son Diziler", series))
        }

        return newHomePageResponse(home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = attr("href").takeIf { it.isNotBlank() } ?: return null
        val title = attr("data-title").takeIf { it.isNotBlank() }
            ?: selectFirst("div.item-title")?.text()
            ?: return null
        val posterUrl = selectFirst("img.thumbnail")?.attr("src")
        val year = selectFirst("div.item-footer .info span")?.text()?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/arama/?s=$query").document
        return document.select("div.film-list div.item-relative > a.item, div.tv--section a.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.title")?.text()?.substringBefore(" izle")?.trim() ?: return null
        val poster = document.selectFirst("div.cover img.cover-img")?.attr("src")
        val year = document.selectFirst("h1.title span.date a")?.text()?.toIntOrNull()
        val plot = document.selectFirst("div.description")?.text()?.trim()
        val imdb = document.selectFirst("div.imdb b")?.text()?.toDoubleOrNull()
        val duration = document.selectFirst("div.time")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val tags = document.select("div.type a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.score = imdb?.let { Score.from10(it) }
            this.duration = duration
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val videoLinks = document.select("div.video-parts a.btn")

        for (link in videoLinks) {
            val videoUrl = link.attr("data-video_url").takeIf { it.isNotBlank() } ?: continue
            val name = link.text().trim()

            try {
                // Embed sayfasını çek
                val embedPage = app.get(videoUrl, referer = data).text

                // Regex ile gerçek video linkini bul (m3u8 veya mp4)
                val m3u8Regex = Regex("""(https?://[^\s"']+?\.m3u8[^\s"']*)""")
                val mp4Regex = Regex("""(https?://[^\s"']+?\.mp4[^\s"']*)""")

                val m3u8Match = m3u8Regex.find(embedPage)?.value
                val mp4Match = mp4Regex.find(embedPage)?.value

                if (m3u8Match != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = name,
                            url = m3u8Match,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = videoUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                } else if (mp4Match != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = name,
                            url = mp4Match,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = videoUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            } catch (e: Exception) {
                // Embed sayfası açılamazsa (404 vs.) bu kaynağı atla
                continue
            }
        }
        return true
    }
}
