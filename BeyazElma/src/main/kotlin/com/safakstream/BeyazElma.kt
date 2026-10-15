// SafakStream-Extensions/BeyazElma/src/main/kotlin/com/safakstream/BeyazElma.kt

package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import org.json.JSONObject

class BeyazElma : MainAPI() {
    override var mainUrl = "https://beyazelma78.com"
    override var name = "BeyazElma"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.LiveStream,
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

    /**
     * Ana sayfadaki maç kartlarını parse eder.
     * Kart yapısı: <a href="/mac/{slug}"> ... </a>
     */
    private fun parseMatches(document: org.jsoup.nodes.Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        // Ana sayfadaki maç kartlarının hepsi <a href="/mac/..."> şeklinde
        document.select("a[href^=/mac/]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isEmpty()) return@forEach

            // Takım isimleri
            val home = a.selectFirst(".site-fixture-side--home .site-fixture-name")?.text()?.trim()
            val away = a.selectFirst(".site-fixture-side--away .site-fixture-name")?.text()?.trim()

            // Eğer kartta takım isimleri yoksa (örn. "Bugünkü Diğer Maçlar" listesi)
            // o zaman link içindeki metni kullan
            val title = if (!home.isNullOrEmpty() && !away.isNullOrEmpty()) {
                "$home - $away"
            } else {
                a.selectFirst("p.text-xs")?.text()?.trim()
                    ?: a.text().trim().takeIf { it.isNotEmpty() }
                    ?: return@forEach
            }

            // Lig bilgisi
            val league = a.selectFirst(".site-fixture-league")?.text()?.trim()
                ?: a.selectFirst("p.text-\\[10px\\]")?.text()?.trim()

            // Saat
            val time = a.selectFirst(".site-fixture-time")?.text()?.trim()

            // Logo
            val poster = a.selectFirst("img.site-fixture-logo")?.attr("src")?.let {
                if (it.startsWith("http")) it else "$mainUrl$it"
            }

            out.add(
                newLiveStreamSearchResponse(href) {
                    this.name = title
                    this.posterUrl = poster
                }
            )
        }
        return out.distinctBy { it.url }
    }

    /**
     * /kanallar sayfasındaki kanal kartlarını parse eder.
     * Kart yapısı: <a href="/kanal/{slug}"> ... </a>
     */
    private fun parseChannels(document: org.jsoup.nodes.Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        document.select("a[href^=/kanal/]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isEmpty()) return@forEach

            // Kanal adı - genelde link içindeki görselin altında yazar
            val name = a.selectFirst("span, p, div")?.text()?.trim()
                ?: a.attr("title").trim()
                ?: a.text().trim()

            if (name.isEmpty()) return@forEach

            // Kanal logosu
            val poster = a.selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else "$mainUrl$it"
            }

            out.add(
                newLiveStreamSearchResponse(href) {
                    this.name = name
                    this.posterUrl = poster
                }
            )
        }
        return out.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Maç ve kanal detay sayfaları için basit bir LiveStream response döneriz.
        // loadLinks zaten URL üzerinden çalışacağı için burada detay gerekmez.
        val document = app.get(url).document
        val title = document.selectFirst("title")?.text()?.trim() ?: "BeyazElma"
        return newLiveStreamLoadResponse(title, url) {
            this.posterUrl = null
        }
    }

    /**
     * 1. Detay sayfasından /api/embed?u=XXX linklerini çeker
     * 2. Her biri için embed sayfasına gider, "slug" değerini alır
     * 3. /api/play/{slug} çağırır, JSON'dan m3u8 URL'lerini alır
     * 4. Cloudstream'e ExtractorLink olarak verir
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val pageUrl = if (data.startsWith("http")) data else "$mainUrl$data"

        // 1. Detay sayfasını çek
        val document = app.get(pageUrl).document

        // 2. /api/embed?u=XXX linklerini al (aynı olanları tekilleştir)
        val embedPaths = document.select("link[href*=/api/embed?u=], script, [data-stream-url]")
            .mapNotNull { it.attr("href").takeIf { h -> h.contains("/api/embed?u=") } }
            .plus(
                // HTML içinde düz metin olarak geçen /api/embed?u=... linklerini de yakala
                Regex("""/api/embed\?u=[A-Za-z0-9_\-]+""")
                    .findAll(document.html())
                    .map { it.value }
                    .toList()
            )
            .distinct()

        if (embedPaths.isEmpty()) return false

        // 3. Her embed için: embed sayfası -> slug -> /api/play/{slug} -> m3u8
        for (embedPath in embedPaths) {
            try {
                val embedFullUrl = if (embedPath.startsWith("http")) embedPath else "$mainUrl$embedPath"
                val embedHtml = app.get(embedFullUrl).text

                // StreamPlayer.mount('#player', {"slug":"tr-bein-sports-2-hd", ...})
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
                val sources = mutableListOf<Pair<String, String>>() // (url, label)

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
                // Bir embed başarısız olursa diğerini dene
                continue
            }
        }

        return found
    }
}
