package com.safakstream.hdfilmcehennemi

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import org.json.JSONObject

class Hdfilmcehennemi : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Son Eklenenler",
        "$mainUrl/category/film-izle-2/" to "Filmler",
        "$mainUrl/yabancidiziizle-7/" to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val html = app.get(request.data, headers = headers).text
            val document = Jsoup.parse(html)
            val items = document.select("a.poster").mapNotNull { parsePosterElement(it) }.distinctBy { it.url }
            Log.d(name, "getMainPage ${request.name}: ${items.size} öğe")
            newHomePageResponse(request.name, items)
        } catch (e: Exception) {
            Log.e(name, "getMainPage error: ${e.message}")
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun parsePosterElement(element: Element): SearchResponse? {
        val href = element.attr("href").ifBlank { return null }
        val title = element.selectFirst("strong.poster-title")?.text()?.trim()?.ifBlank { null } ?: return null
        val posterUrl = element.selectFirst("img")?.let { img ->
            img.attr("src").ifBlank { img.attr("data-src") }
        }?.ifBlank { null }
        val year = element.selectFirst("div.poster-meta span")?.text()?.toIntOrNull()
        val imdb = element.selectFirst("span.imdb")?.text()?.trim()?.toDoubleOrNull()

        val isSeries = href.contains("/dizi/", ignoreCase = true)
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
                this.score = imdb?.let { Score.from10(it) }
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
                this.score = imdb?.let { Score.from10(it) }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val html = app.get(url, headers = headers + mapOf("Referer" to mainUrl)).text
            val document = Jsoup.parse(html)

            val title = document.selectFirst("h1")?.text()?.trim()
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.substringBefore(" |")
                ?: return null

            val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
            val poster = document.selectFirst("img[data-src*=list/poster]")?.attr("data-src")
                ?: document.selectFirst("img[data-src*=thumb/poster]")?.attr("data-src")
                ?: document.selectFirst("img[src*=list/poster]")?.attr("src")
                ?: document.selectFirst("img[src*=thumb/poster]")?.attr("src")
            val year = Regex("""\b(19|20)\d{2}\b""").find(description ?: "")?.value?.toIntOrNull()

            val isSeries = url.contains("/dizi/", ignoreCase = true)

            if (isSeries) {
                val episodes = mutableListOf<Episode>()
                document.select("a[href*=/sezon-][href*=/bolum-]").forEach { a ->
                    val epUrl = a.absUrl("href").ifBlank { a.attr("href") }
                    if (epUrl.isBlank() || !epUrl.startsWith("http")) return@forEach
                    val sezonMatch = Regex("""/sezon-(\d+)""").find(epUrl)
                    val bolumMatch = Regex("""/bolum-(\d+)""").find(epUrl)
                    val season = sezonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val episode = bolumMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    episodes.add(newEpisode(epUrl) {
                        this.name = "S$season B$episode"
                        this.season = season
                        this.episode = episode
                        this.posterUrl = poster
                    })
                }
                val distinct = episodes.distinctBy { it.data }
                Log.d(name, "load series: $title, ${distinct.size} bölüm")
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, distinct) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                }
            }

            Log.d(name, "load movie: $title")
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } catch (e: Exception) {
            Log.e(name, "load error: ${e.message}")
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val html = app.get(data, headers = headers + mapOf("Referer" to mainUrl)).text
            val document = Jsoup.parse(html)
            val iframeSrc = document.selectFirst("iframe[data-src]")?.attr("data-src")?.ifBlank { null }
                ?: document.selectFirst("iframe[src]")?.attr("src")?.ifBlank { null }
                ?: run {
                    Log.e(name, "loadLinks: iframe bulunamadi")
                    return false
                }

            val iframeUrl = when {
                iframeSrc.startsWith("//") -> "https:$iframeSrc"
                iframeSrc.startsWith("/") -> "$mainUrl$iframeSrc"
                iframeSrc.startsWith("http") -> iframeSrc
                else -> "$mainUrl/$iframeSrc"
            }
            Log.d(name, "loadLinks: iframe=$iframeUrl")

            val iframeHtml = app.get(iframeUrl, headers = headers + mapOf("Referer" to data)).text
            val hostname = Regex("""^https?://([^/]+)""").find(iframeUrl)?.groupValues?.get(1) ?: ""

            val videoUrl = extractVideoUrl(iframeHtml, hostname)
            if (videoUrl != null) {
                Log.d(name, "loadLinks: video=$videoUrl")
                callback.invoke(newExtractorLink(
                    source = name,
                    name = "HDFilmCehennemi",
                    url = videoUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = iframeUrl
                    this.headers = headers
                    this.quality = Qualities.P1080.value
                })
            } else {
                Log.e(name, "loadLinks: video URL bulunamadi")
            }

            extractSubtitles(iframeHtml, iframeUrl, subtitleCallback)

            videoUrl != null
        } catch (e: Exception) {
            Log.e(name, "loadLinks error: ${e.message}")
            false
        }
    }

    private fun extractVideoUrl(iframeHtml: String, hostname: String): String? {
        val d8nMatch = Regex("""d8n\("([^"]+)"\.split\("~"\)\)""").find(iframeHtml)
        if (d8nMatch != null) {
            val parts = d8nMatch.groupValues[1].split("~")
            val decoded = decode(parts, addHostname = true, hostname = hostname)
            if (decoded.startsWith("http")) return decoded
        }

        val ty0Match = Regex("""ty0\("([^"]+)"\.split\("~"\)\)""").find(iframeHtml)
        if (ty0Match != null) {
            val parts = ty0Match.groupValues[1].split("~")
            val decoded = decode(parts, addHostname = false, hostname = "")
            if (decoded.startsWith("http")) return decoded
        }

        return Regex(""""contentUrl"\s*:\s*"([^"]+)"""").find(iframeHtml)?.groupValues?.get(1)
    }

    private suspend fun extractSubtitles(
        iframeHtml: String,
        iframeUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val trackRegex = Regex("""\{"file":"([^"]+)","kind":"captions","label":"([^"]+)"""")
        trackRegex.findAll(iframeHtml).forEach { match ->
            var url = match.groupValues[1].replace("\\/", "/")
            val label = match.groupValues[2]
            if (url.startsWith("//")) url = "https:$url"
            else if (url.startsWith("/")) {
                val base = Regex("""^https?://[^/]+""").find(iframeUrl)?.value ?: ""
                url = "$base$url"
            }
            Log.d(name, "subtitle: $label -> $url")
            subtitleCallback.invoke(newSubtitleFile(label, url))
        }
    }

    private fun decode(parts: List<String>, addHostname: Boolean, hostname: String): String {
        val xbd25 = parts.toMutableList()
        val djq5 = xbd25.size - 2
        val nzq21 = djq5 % 7
        val a07 = 8 + (djq5 % 5)

        val x3y = xbd25.removeAt(a07)
        var too3s = xbd25.removeAt(nzq21)
        if (addHostname) too3s += hostname

        var ipb0 = xbd25.joinToString("")
        if (too3s.length > 4096) {
            ipb0 = String(Base64.decode(ipb0, Base64.DEFAULT), Charsets.ISO_8859_1)
        }

        var ke7 = 0
        var ox1 = 0
        for (i in too3s.indices) {
            val c = too3s[i].code
            ke7 = (ke7 * 37 + c) % 241
            ox1 = (ox1 + ((c shl 1) xor i)) and 255
        }

        val w44 = (ke7 * 3 + ox1) % 256
        val ki46x = (ox1 % 11) + 5
        var w3scp = ((ox1 * 251 + ke7) % 65519) + 1

        for (i in x3y.indices.reversed()) {
            when (val ch = x3y[i]) {
                '7' -> ipb0 = String(Base64.decode(ipb0, Base64.DEFAULT), Charsets.ISO_8859_1)
                '3' -> ipb0 = ipb0.reversed()
                else -> {
                    val mod = (((ch.code - 96) % 26) + 26) % 26
                    val osm4 = (26 - mod) % 26
                    val sb = StringBuilder(ipb0.length)
                    for (c in ipb0) {
                        val cc = c.code
                        val r69e = when {
                            cc in 65..90 -> 65
                            cc in 97..122 -> 97
                            else -> { sb.append(c); continue }
                        }
                        sb.append((((cc - r69e + osm4) % 26) + r69e).toChar())
                    }
                    ipb0 = sb.toString()
                }
            }
        }

        if (x3y.length > 2048) ipb0 = ipb0.reversed()

        val len = ipb0.length
        val g83 = IntArray(len)
        for (i in (len - 1) downTo 1) {
            w3scp = (w3scp * 97 + 41) % 65519
            g83[i] = w3scp % (i + 1)
        }

        val xdr8 = ipb0.toCharArray()
        for (i in 1 until len) {
            val b = g83[i]
            val tmp = xdr8[i]
            xdr8[i] = xdr8[b]
            xdr8[b] = tmp
        }

        var h43 = w44
        val sb = StringBuilder()
        for (c in xdr8) {
            val cc = c.code
            h43 = (h43 * 5 + ki46x) % 256
            sb.append((cc xor h43).toChar())
            h43 = (h43 + cc) % 256
        }
        return sb.toString()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val response = app.get(
                "$mainUrl/search?q=$encoded",
                headers = headers + mapOf(
                    "Referer" to mainUrl,
                    "X-Requested-With" to "fetch"
                )
            ).text
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return emptyList()
            val sb = StringBuilder("<html><body>")
            for (i in 0 until results.length()) {
                sb.append(results.optString(i, ""))
            }
            sb.append("</body></html>")
            val document = Jsoup.parse(sb.toString())
            document.select("a.search-result").mapNotNull { a ->
                val href = a.attr("href").ifBlank { return@mapNotNull null }
                val title = a.selectFirst("h4.title")?.text()?.trim()
                    ?: a.selectFirst("img")?.attr("alt")?.trim()
                    ?: return@mapNotNull null
                val poster = a.selectFirst("img")?.attr("src")?.ifBlank { null }
                val year = a.selectFirst("span.year")?.text()?.toIntOrNull()
                val imdb = a.selectFirst("span.imdb")?.text()?.trim()?.toDoubleOrNull()
                val type = a.selectFirst("span.type")?.text()?.trim()
                val isSeries = type.equals("Dizi", ignoreCase = true) || href.contains("/dizi/")
                if (isSeries) {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = poster
                        this.year = year
                        this.score = imdb?.let { Score.from10(it) }
                    }
                } else {
                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = poster
                        this.year = year
                        this.score = imdb?.let { Score.from10(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(name, "search error: ${e.message}")
            emptyList()
        }
    }
}
