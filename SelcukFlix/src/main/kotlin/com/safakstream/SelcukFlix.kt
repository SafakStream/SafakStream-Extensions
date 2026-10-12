package com.safakstream

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class SelcukFlix : MainAPI() {
    override var mainUrl              = "https://selcukflix.app"
    override var name                 = "SelcukFlix"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 150L
    override var sequentialMainPageScrollDelay = 150L

    override val mainPage = mainPageOf(
        "${mainUrl}/dizi-izle"              to "Yeni Diziler",
        "${mainUrl}/film-izle"              to "Yeni Filmler",
        "${mainUrl}/trend"                  to "Trend",
        "${mainUrl}/kesfet"                 to "Keşfet",
        "${mainUrl}/seri-filmler"           to "Seri Filmler",
        "${mainUrl}/yabanci-dizi-izle"      to "Yabancı Diziler"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}?sayfa=$page" else request.data
        val document = app.get(url, headers = headers, referer = mainUrl).document

        val home = document.select("a[href^='dizi/'], a[href^='/dizi/'], a[href^='film/'], a[href^='/film/']")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Sadece içinde img olan kartları al
        val img = this.selectFirst("img") ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null

        // Dizi detay sayfası linkleri sadece (sezon/bolum içermeyen)
        if (href.contains("/sezon-") || href.contains("/bolum-")) return null

        val alt = img.attr("alt").trim()
        val title = alt.removeSuffix(" izle").removeSuffix(" full hd izle").trim()
        if (title.isEmpty()) return null

        val posterUrl = fixUrlNull(img.attr("data-src").ifBlank { img.attr("src") })

        val isMovie = href.contains("/film/")
        val tvType  = if (isMovie) TvType.Movie else TvType.TvSeries

        return if (isMovie) {
            newMovieSearchResponse(title, href, tvType) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, tvType) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val resp = app.post(
                "${mainUrl}/api/bg/searchContent",
                headers = headers + mapOf(
                    "Accept" to "application/json, text/plain, */*",
                    "Content-Type" to "application/json",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Origin" to mainUrl,
                    "Referer" to "$mainUrl/"
                ),
                data = mapOf("searchterm" to query)
            ).text

            val decoded = SelcukFlixDecryptor.decryptResponse(resp)
            val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            val root = mapper.readTree(decoded)
            val results = root.get("result") ?: return emptyList()

            results.mapNotNull { node ->
                val slug  = node.get("used_slug")?.asText() ?: return@mapNotNull null
                val title = node.get("used_title")?.asText()?.ifBlank { null }
                    ?: node.get("original_title")?.asText()?.ifBlank { null }
                    ?: node.get("culture_title")?.asText()?.ifBlank { null }
                    ?: return@mapNotNull null
                val poster = node.get("poster_url")?.asText()?.ifBlank { null }
                    ?: node.get("object_poster_url")?.asText()?.ifBlank { null }
                val typeId = node.get("type_id")?.asInt() ?: 1
                val url = fixUrl(slug)
                val isMovie = slug.startsWith("film/") || typeId == 2

                if (isMovie) {
                    newMovieSearchResponse(title, url, TvType.Movie) { this.posterUrl = poster }
                } else {
                    newTvSeriesSearchResponse(title, url, TvType.TvSeries) { this.posterUrl = poster }
                }
            }
        } catch (e: Exception) {
            Log.e("SelcukFlix", "Search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = headers, referer = mainUrl).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.removeSuffix(" izle")
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: return null

        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img[alt]")?.attr("src")

        val year = Regex("""(19|20)\d{2}""").find(title)?.value?.toIntOrNull()
            ?: document.selectFirst("a[href^='/yil/']")?.text()?.toIntOrNull()

        // Bölüm listesi
        val episodeElements = document.select("a[href*='/sezon-'][href*='/bolum-']")
            .mapNotNull { el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                // Sezon ve bölüm numaralarını URL'den çıkar
                val m = Regex("""/sezon-(\d+)/bolum-(\d+)""").find(epHref) ?: return@mapNotNull null
                val season = m.groupValues[1].toIntOrNull() ?: 1
                val episode = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val epName = el.selectFirst("h2, h3, span")?.text()?.trim()
                    ?: "$season. Sezon $episode. Bölüm"
                newEpisode(epHref) {
                    this.name = epName
                    this.season = season
                    this.episode = episode
                }
            }
            .distinctBy { it.data }

        return if (episodeElements.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeElements) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("SelcukFlix", "loadLinks çağrıldı: $data")

        val document = try {
            app.get(data, headers = headers, referer = mainUrl).document
        } catch (e: Exception) {
            Log.e("SelcukFlix", "Sayfa yükleme hatası: ${e.message}")
            return false
        }
        val html = document.html()
        Log.d("SelcukFlix", "HTML uzunluk: ${html.length}")

        // __NEXT_DATA__ bul
        val nextDataMatch = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
        if (nextDataMatch == null) {
            Log.e("SelcukFlix", "__NEXT_DATA__ bulunamadı")
            return false
        }

        val json = nextDataMatch.groupValues[1]
        Log.d("SelcukFlix", "__NEXT_DATA__ uzunluk: ${json.length}")

        // secureData'yı çek
        val secureMatch = Regex(""""secureData"\s*:\s*"([^"]+)"""").find(json)
        if (secureMatch == null) {
            Log.e("SelcukFlix", "secureData bulunamadı")
            return false
        }
        val secureLen = secureMatch.groupValues[1].length
        Log.d("SelcukFlix", "secureData bulundu, uzunluk: $secureLen")

        // Decode et
        val decoded = SelcukFlixDecryptor.decryptSecureData(secureMatch.groupValues[1])
        if (decoded.isEmpty()) {
            Log.e("SelcukFlix", "secureData decode başarısız")
            return false
        }
        Log.d("SelcukFlix", "Decoded uzunluk: ${decoded.length}")

        // iframe src'lerini bul — hem " hem \" hem de escape'siz varyantlar
        val iframeUrls = mutableSetOf<String>()

        // 1) Doğrudan src="..." (escape'siz)
        Regex("""<iframe[^>]*src="([^"]+)"""").findAll(decoded).forEach {
            iframeUrls.add(it.groupValues[1])
        }

        // 2) src=\"...\" (escape'li) — Kotlin raw string'de \ = iki backslash
        Regex("""src=\\?"([^"\\]+)"""").findAll(decoded).forEach {
            iframeUrls.add(it.groupValues[1])
        }

        // 3) pichive.online geçen tüm URL'leri direkt bul (en garantili yol!)
        Regex("""(//[a-z0-9.-]*pichive\.online/iframe\.php\?v=[a-f0-9]+)""").findAll(decoded).forEach {
            iframeUrls.add(it.groupValues[1])
        }

        // 4) Generic iframe.php?v= de yakala
        Regex("""(//[a-z0-9.-]+/iframe\.php\?v=[a-zA-Z0-9]+)""").findAll(decoded).forEach {
            iframeUrls.add(it.groupValues[1])
        }

        val iframes = iframeUrls
            .map { it.replace("\\", "") }
            .map { if (it.startsWith("//")) "https:$it" else if (it.startsWith("/")) "$mainUrl$it" else it }
            .filter { it.contains("iframe.php") }
            .distinct()

        Log.d("SelcukFlix", "Bulunan iframe sayısı: ${iframes.size}")
        iframes.forEach { Log.d("SelcukFlix", "  iframe: $it") }

        if (iframes.isEmpty()) {
            Log.e("SelcukFlix", "Hiç iframe bulunamadı!")
            return false
        }

        iframes.forEach { iframeUrl ->
            Log.d("SelcukFlix", "İşleniyor iframe: $iframeUrl")
            try {
                PichiveExtractor().getUrl(iframeUrl, data, subtitleCallback, callback)
                Log.d("SelcukFlix", "Extractor tamamlandı: $iframeUrl")
            } catch (e: Exception) {
                Log.e("SelcukFlix", "Extractor hatası: ${e.message}", e)
            }
        }

        return iframes.isNotEmpty()
    }
