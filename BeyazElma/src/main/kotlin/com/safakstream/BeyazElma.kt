// SafakStream-Extensions/BeyazElma/src/main/kotlin/com/safakstream/BeyazElma.kt

package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class BeyazElma : MainAPI() {
    override var mainUrl = "https://beyazelma78.com"
    override var name = "BeyazElma"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Live,
        TvType.Others,
    )

    companion object {
        const val EMBED_URL = "https://embed.beyazelma78.com"
    }

    override val mainPage = mainPageOf(
        "" to "Canlı Maçlar",
        "kanallar" to "Canlı Kanallar",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isEmpty()) "$mainUrl/" else "$mainUrl/${request.data}"
        val document = app.get(url).document
        val items = when (request.data) {
            "kanallar" -> parseChannels(document)
            else -> parseMatches(document)
        }
        return newHomePageResponse(
            listOf(HomePageList(request.name, items)),
            hasNext = false
        )
    }

    private fun parseMatches(document: org.jsoup.nodes.Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        document.select("a[href^=/mac/]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isEmpty()) return@forEach

            val home = a.selectFirst(".site-fixture-side--home .site-fixture-name")?.text()?.trim()
            val away = a.selectFirst(".site-fixture-side--away .site-fixture-name")?.text()?.trim()

            val title = if (!home.isNullOrEmpty() && !away.isNullOrEmpty()) {
                "$home - $away"
            } else {
                a.selectFirst("p.text-xs")?.text()?.trim()
                    ?: a.text().trim().takeIf { it.isNotEmpty() }
                    ?: return@forEach
            }

            val poster = a.selectFirst("img.site-fixture-logo")?.attr("src")?.let {
                if (it.startsWith("http")) it else "$mainUrl$it"
            }

            out.add(
                newMovieSearchResponse(
                    name = title,
                    url = href,
                    type = TvType.Live,
                ) {
                    this.posterUrl = poster
                }
            )
        }
        return out.distinctBy { it.url }
    }

    private fun parseChannels(document: org.jsoup.nodes.Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        document.select("a.site-channel-row, a.site-channel-card, a[href^=/kanal/]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isEmpty() || !href.startsWith("/kanal/")) return@forEach

            // Kanal adı: .site-channel-row-name class'ında
            val name = a.selectFirst(".site-channel-row-name")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: a.selectFirst("img")?.attr("alt")?.trim()
                    ?.removeSuffix(" logosu")
                    ?.takeIf { it.isNotEmpty() }
                ?: a.attr("title").trim().takeIf { it.isNotEmpty() }
                ?: return@forEach

            // Logo: img src
            val poster = a.selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else "$mainUrl$it"
            }

            out.add(
                newMovieSearchResponse(
                    name = name,
                    url = href,
                    type = TvType.Live,
                ) {
                    this.posterUrl = poster
                }
            )
        }
        return out.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("title")?.text()?.trim() ?: "BeyazElma"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Live,
            dataUrl = url,
        ) {
            this.posterUrl = poster
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val q = query.trim()
        if (q.isEmpty()) return results

        // 1. Tüm kanalları çek ve filtrele (statik liste, hızlı)
        try {
            val channelsDoc = app.get("$mainUrl/kanallar").document
            val channels = parseChannels(channelsDoc)
            results.addAll(channels.filter {
                it.name.contains(q, ignoreCase = true)
            })
        } catch (e: Exception) { }

        // 2. Bugünün maçlarını çek ve filtrele
        try {
            val matchesDoc = app.get("$mainUrl/").document
            val matches = parseMatches(matchesDoc)
            results.addAll(matches.filter {
                it.name.contains(q, ignoreCase = true)
            })
        } catch (e: Exception) { }

        return results.distinctBy { it.url }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val pageUrl = if (data.startsWith("http")) data else "$mainUrl$data"

        val document = app.get(pageUrl).document

        // /api/embed?u=XXX linklerini bul
        val embedPaths = Regex("""/api/embed\?u=[A-Za-z0-9_\-]+""")
            .findAll(document.html())
            .map { it.value }
            .distinct()
            .toList()

        if (embedPaths.isEmpty()) return false

        for (embedPath in embedPaths) {
            try {
                val embedFullUrl = if (embedPath.startsWith("http")) embedPath else "$mainUrl$embedPath"
                val embedHtml = app.get(embedFullUrl).text

                val slug = Regex(""""slug"\s*:\s*"([^"]+)"""")
                    .find(embedHtml)?.groupValues?.get(1)
                    ?: continue

                val apiUrl = "$EMBED_URL/api/play/$slug"
                val json = app.get(
                    apiUrl,
                    referer = "$EMBED_URL/",
                    headers = mapOf("Accept" to "application/json")
                ).text

                val obj = JSONObject(json)
                val sources = mutableListOf<Pair<String, String>>()

                if (obj.has("primary")) {
                    sources.add(obj.getString("primary") to "BeyazElma")
                }
                if (obj.has("fallbacks")) {
                    val arr = obj.getJSONArray("fallbacks")
                    for (i in 0 until arr.length()) {
                        sources.add(arr.getString(i) to "BeyazElma (yedek)")
                    }
                }

                for ((m3u8, label) in sources) {
                    if (m3u8.isBlank()) continue
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "$label - $slug",
                            url = m3u8,
                            type = ExtractorLinkType.M3U8,
                        ) {
                            this.referer = "$EMBED_URL/"
                            this.quality = Qualities.Unknown.value
                            this.headers = mapOf(
                                "Referer" to "$EMBED_URL/",
                                "Origin" to EMBED_URL,
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
                            )
                        }
                    )
                    found = true
                }
            } catch (e: Exception) {
                continue
            }
        }

        return found
    }
}
