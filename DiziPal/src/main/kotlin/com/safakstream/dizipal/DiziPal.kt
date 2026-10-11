package com.safakstream.dizipal

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import okhttp3.Interceptor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class DiziPal : MainAPI() {
    override var mainUrl = "https://dizipal1588.com"
    override var name = "DiziPal"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 150L
    override var sequentialMainPageScrollDelay = 150L

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    override val mainPage = mainPageOf(
        "$mainUrl/yabanci-dizi-izle" to "Yeni Diziler",
        "$mainUrl/hd-film-izle" to "Yeni Filmler",
        "$mainUrl/kanal/netflix" to "Netflix",
        "$mainUrl/kanal/exxen" to "Exxen",
        "$mainUrl/kanal/max" to "Max",
        "$mainUrl/kanal/disney" to "Disney+",
        "$mainUrl/kanal/amazon" to "Amazon Prime",
        "$mainUrl/kanal/tod" to "TOD (beIN)",
        "$mainUrl/kanal/tabii" to "Tabii",
        "$mainUrl/kanal/hulu" to "Hulu",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1 && !request.data.contains("/kanal/")) {
            if (request.data.contains("?")) "${request.data}&sayfa=$page"
            else "${request.data}?sayfa=$page"
        } else request.data

        val document = app.get(url, headers = getHeaders(mainUrl), interceptor = interceptor).document
        val home = mutableListOf<SearchResponse>()

        if (request.data.contains("/kanal/") && page == 1) {
            // Kanal sayfası: son bölümler
            home.addAll(document.select("div.new-added-list div.bg-\\[\\#22232a\\]").mapNotNull { it.sonBolumler() })
        } else if (request.data.contains("/yabanci-dizi-izle") || request.data.contains("/hd-film-izle")) {
            home.addAll(document.select("div.new-added-list div.bg-\\[\\#22232a\\]").mapNotNull { it.sonBolumler() })
        } else {
            home.addAll(document.select("div.bg-\\[\\#22232a\\]").mapNotNull { it.diziler() })
        }

        // Kanal için API'den ek sayfa çek
        if (request.data.contains("/kanal/")) {
            try {
                val channelIdFromDoc = document.selectFirst("input[name=channelId]")?.attr("value")
                    ?: Regex("channelId\\s*[:=]\\s*(\\d+)").find(document.html())?.groupValues?.get(1)
                val channelSlug = request.data.substringAfterLast("/")

                val apiUrl = "$mainUrl/bg/getserielistbychannel"
                val apiResponse = app.post(
                    apiUrl,
                    data = mapOf(
                        "cKey" to "c61f91c5141d178450934fe81c0a2029",
                        "cValue" to "MTc4NDQwNzIwMDhkMzJhNTc1YzUwOGU1ZjQwMjdjMjIyOWVjOGVhMTcwNGQyM2FjODM2YTI4YTU0NjUyMjI2ZmVjMzFkYzBkMWQyMWY4YzdiNA==",
                        "curPage" to page.toString(),
                        "channelId" to (channelIdFromDoc ?: "1"),
                        "languageId" to "2,3,4",
                        "slug" to channelSlug,
                    ),
                    headers = mapOf(
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With" to "XMLHttpRequest",
                    ),
                    interceptor = interceptor,
                )

                val rootNode = ObjectMapper().readTree(apiResponse.text)
                val htmlNode = rootNode.at("/data/html")
                if (!htmlNode.isMissingNode) {
                    val parsedDoc = org.jsoup.Jsoup.parse(htmlNode.asText())
                    val apiResults = parsedDoc.select("div.bg-\\[\\#22232a\\]").mapNotNull { it.diziler() }
                    apiResults.forEach { res ->
                        if (home.none { it.url == res.url }) home.add(res)
                    }
                }
            } catch (e: Exception) {
                Log.e("DiziPal", "API Hatası: ${e.message}")
            }
        }

        return newHomePageResponse(request.name, home, home.isNotEmpty())
    }

    private fun Element.sonBolumler(): SearchResponse? {
        val name = this.selectFirst("img")?.attr("alt") ?: return null
        var episode = this.selectFirst("div.episode")?.text()?.trim() ?: ""
        episode = episode.replace(". Sezon ", "x").replace(". Bölüm", "")
        val title = if (episode.isNotEmpty()) "$name $episode" else name
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        return newTvSeriesSearchResponse(title, href.substringBefore("/sezon"), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziler(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("alt") ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun SearchItem.toPostSearchResult(): SearchResponse? {
        val title = this.title ?: return null
        val href = "$mainUrl/${this.slug}"
        val posterUrl = this.poster
        return if (this.type == "series") {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val apiUrl = "$mainUrl/bg/searchcontent"
        val response = app.post(
            apiUrl,
            data = mapOf(
                "cKey" to "c61f91c5141d178450934fe81c0a2029",
                "cValue" to "MTc4NDQwNzIwMDhkMzJhNTc1YzUwOGU1ZjQwMjdjMjIyOWVjOGVhMTcwNGQyM2FjODM2YTI4YTU0NjUyMjI2ZmVjMzFkYzBkMWQyMWY4YzdiNA==",
                "type" to "hepsi",
                "searchterm" to query,
            ),
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
            ),
            interceptor = interceptor,
        )
        val rootNode = ObjectMapper().readTree(response.text)
        val resultArray = rootNode.at("/data/result")
        if (resultArray.isMissingNode || !resultArray.isArray) return emptyList()
        val mapper = ObjectMapper()
        val items: List<SearchItem> = mapper.convertValue(resultArray, mapper.typeFactory.constructCollectionType(List::class.java, SearchItem::class.java))
        return items.mapNotNull { it.toPostSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = getHeaders(mainUrl), interceptor = interceptor).document
        val poster = document.selectFirst("div.page-top img[alt]")?.attr("src")
        val year = document.selectXpath("//div[text()='Yıl']//following-sibling::div").text().trim().toIntOrNull()
        val description = document.selectFirst("div.summary p")?.text()?.trim()
        val tags = document.selectXpath("//div[text()='Kategoriler']//following-sibling::div").text().trim()
            .split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Süre']//following-sibling::div").text())?.value?.toIntOrNull()

        if (url.contains("/series/")) {
            val title = document.selectFirst("div.flex h2")?.text() ?: return null
            val episodeElements = document.select("div.relative.w-full.flex.items-start.gap-4")
            val episodes = episodeElements.mapNotNull { element ->
                val linkElement = element.selectFirst("a[data-dizipal-pageloader]") ?: return@mapNotNull null
                val epHref = fixUrlNull(linkElement.attr("href")) ?: return@mapNotNull null
                val epName = linkElement.selectFirst("h2")?.text()?.trim() ?: "Bölüm"
                val infoText = linkElement.selectFirst("div.text-white.text-sm.opacity-80")?.text()?.trim() ?: ""
                val epSeason = Regex("(\\d+)\\.\\s*Sezon").find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                val epEpisode = Regex("(\\d+)\\.\\s*Bölüm").find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epHref) {
                    this.name = epName
                    this.episode = epEpisode
                    this.season = epSeason
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.duration = duration
            }
        } else {
            val title = document.selectXpath("//div[@class='g-title'][2]/div").text().trim()
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.duration = duration
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = getHeaders(mainUrl), interceptor = interceptor).document
        val encryptedText = doc.selectFirst("div[data-rm-k=true]")?.text() ?: ""
        val iframeUrl = if (encryptedText.isNotEmpty()) {
            decryptDizipalData(encryptedText)
        } else {
            doc.selectFirst("iframe")?.attr("src") ?: ""
        }

        if (iframeUrl.isEmpty()) {
            Log.e("DiziPal", "iframeUrl BOŞ")
            return false
        }

        val finalUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
        DizipalPlayer().getUrl(finalUrl, null, subtitleCallback, callback)
        return true
    }

    private fun getHeaders(baseUrl: String): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
        "Referer" to baseUrl,
    )

    private fun decodeHex(s: String): ByteArray {
        check(s.length % 2 == 0) { "Hex string çift uzunlukta olmalıdır" }
        return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun decryptDizipalData(rawJsonText: String): String {
        return try {
            val passphrase = "3hPn4uCjTVtfYWcjIcoJQ4cL1WWk1qxXI39egLYOmNv6IblA7eKJz68uU3eLzux1biZLCms0quEjTYniGv5z1JcKbNIsDQFSeIZOBZJz4is6pD7UyWDggWWzTLBQbHcQFpBQdClnuQaMNUHtLHTpzCvZy33p6I7wFBvL4fnXBYH84aUIyWGTRvM2G5cfoNf4705tO2kv"

            val ctMatch = Regex("\"ciphertext\"\\s*:\\s*\"([^\"]+)\"").find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val ivMatch = Regex("\"iv\"\\s*:\\s*\"([^\"]+)\"").find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val saltMatch = Regex("\"salt\"\\s*:\\s*\"([^\"]+)\"").find(rawJsonText)?.groupValues?.get(1) ?: return ""

            val salt = decodeHex(saltMatch)
            val iv = decodeHex(ivMatch)
            val ciphertext = Base64.decode(ctMatch, Base64.DEFAULT)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
            val secretKey = factory.generateSecret(spec)
            val secret = SecretKeySpec(secretKey.encoded, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(ciphertext)

            var finalUrl = String(decryptedBytes, Charsets.UTF_8).replace("\\/", "/")
            finalUrl = when {
                finalUrl.startsWith("://") -> "https$finalUrl"
                finalUrl.startsWith("//") -> "https:$finalUrl"
                !finalUrl.startsWith("http") -> "https://$finalUrl"
                else -> finalUrl
            }
            finalUrl
        } catch (e: Exception) {
            Log.e("DiziPal", "Decryption hatası: ${e.message}")
            ""
        }
    }
}
