package com.safakstream

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class DiziPal : MainAPI() {
    // EN GÜNCEL DOMAIN BURADA
    override var mainUrl              = "https://dizipal1592.com"
    override var name                 = "DiziPal"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 150L
    override var sequentialMainPageScrollDelay = 150L

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/yabanci-dizi-izle"                 to "Yeni Diziler",
        "${mainUrl}/hd-film-izle"                      to "Yeni Filmler"
    )

    // --- GÖMÜLÜ JSON'DAN VERİ ÇEKEN YARDIMCI FONKSİYON ---
    private fun parseNextData(html: String, key: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            // Next.js verisini bulmak için regex
            val nextDataRegex = """self\.__next_f\.push\(\[1,"(.*?)"\]\)""".toRegex()
            val allData = nextDataRegex.findAll(html).map { it.groupValues[1] }.joinToString("")
            
            // JSON içindeki ilgili diziyi bul (örneğin "popular": [...] veya "search": [...])
            val mapper = jacksonObjectMapper()
            // Ters bölü işaretlerini (escape) temizle
            val cleanedData = allData.replace("\\\"", "\"")
            
            val jsonStartIndex = cleanedData.indexOf("\"$key\":[")
            if (jsonStartIndex != -1) {
                val jsonArrayStart = cleanedData.indexOf('[', jsonStartIndex)
                var bracketCount = 0
                var jsonArrayEnd = -1
                
                for (i in jsonArrayStart until cleanedData.length) {
                    if (cleanedData[i] == '[') bracketCount++
                    if (cleanedData[i] == ']') bracketCount--
                    if (bracketCount == 0) {
                        jsonArrayEnd = i
                        break
                    }
                }
                
                if (jsonArrayEnd != -1) {
                    val jsonArrayString = cleanedData.substring(jsonArrayStart, jsonArrayEnd + 1)
                    val rootNode: JsonNode = mapper.readTree(jsonArrayString)
                    
                    for (node in rootNode) {
                        val title = node.get("t")?.asText() ?: continue
                        val href = node.get("href")?.asText() ?: continue
                        val poster = node.get("img")?.asText()
                        val isSeries = href.contains("/series/")
                        
                        val fullUrl = "$mainUrl$href"
                        if (isSeries) {
                            results.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) { this.posterUrl = poster })
                        } else {
                            results.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) { this.posterUrl = poster })
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DiziPal", "JSON Parse Hatası: ${e.message}")
        }
        return results
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Site tek sayfa olduğu için ve veriyi JS ile bastığı için ana sayfayı doğrudan çekiyoruz.
        val document = app.get(mainUrl, timeout = 10000, interceptor = interceptor, headers = getHeaders(mainUrl)).text
        val home = parseNextData(document, "popular") // Ana sayfadaki popüler içerikleri çeker
        
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "${mainUrl}/search?q=$query"
        val html = app.get(searchUrl, interceptor = interceptor, headers = getHeaders(mainUrl)).text
        // Arama sonuçları genellikle "search" veya "results" anahtarı altındadır
        var results = parseNextData(html, "search")
        if (results.isEmpty()) results = parseNextData(html, "results")
        if (results.isEmpty()) results = parseNextData(html, "popular") // Eğer özel bir key yoksa varsayılan olarak popular'ı dene
        return results
    }
    
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor, headers = getHeaders(mainUrl)).document

        // --- 1. ORTAK BİLGİLER ---
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: "Bilinmeyen İçerik"

        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")

        val backgroundPoster = poster

        val year = document.selectXpath("//div[text()='Yıl']//following-sibling::div").text().trim().toIntOrNull()
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Süre']//following-sibling::div").text())?.value?.toIntOrNull()
        val tags = document.selectXpath("//div[text()='Kategoriler']//following-sibling::div").text().trim()
            .split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        // --- 2. BÖLÜM LİSTESİ KONTROLÜ ---
        val episodeElements = document.select("div.relative.w-full.flex.items-start.gap-4")
        val hasEpisodes = episodeElements.isNotEmpty() || 
                          document.selectFirst("a[data-dizipal-pageloader]") != null

        if (hasEpisodes && episodeElements.isNotEmpty()) {
            val episodes = episodeElements.mapNotNull { element ->
                val linkElement = element.selectFirst("a[data-dizipal-pageloader]") ?: return@mapNotNull null
                val epHref = fixUrlNull(linkElement.attr("href")) ?: return@mapNotNull null
                val epName = linkElement.selectFirst("h2")?.text()?.trim() ?: "Bölüm"
                val infoText = linkElement.selectFirst("div.text-white.text-sm.opacity-80")?.text()?.trim() ?: ""
                val epSeason = Regex("""(\d+)\.\s*Sezon""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                val epEpisode = Regex("""(\d+)\.\s*Bölüm""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(epHref) {
                    this.name    = epName
                    this.episode = epEpisode
                    this.season  = epSeason
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.duration  = duration
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.duration  = duration
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val encryptedText = doc.selectFirst("div[data-rm-k=true]")?.text() ?: ""
        
        var iframeUrl = if (encryptedText.isNotEmpty()) {
            decryptDizipalData(encryptedText)
        } else {
            doc.selectFirst("iframe")?.attr("src") ?: ""
        }

        if (iframeUrl.isNotEmpty()) {
            if (iframeUrl.startsWith("//")) iframeUrl = "https:$iframeUrl"
            
            DizipalPlayer().getUrl(
                url = iframeUrl,
                referer = data,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        return true
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Hex string çift uzunlukta olmalıdır" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun decryptDizipalData(rawJsonText: String): String {
        return try {
            val passphrase = "3hPn4uCjTVtfYWcjIcoJQ4cL1WWk1qxXI39egLYOmNv6IblA7eKJz68uU3eLzux1biZLCms0quEjTYniGv5z1JcKbNIsDQFSeIZOBZJz4is6pD7UyWDggWWzTLBQbHcQFpBQdClnuQaMNUHtLHTpzCvZy33p6I7wFBvL4fnXBYH84aUIyWGTRvM2G5cfoNf4705tO2kv"

            val ctMatch = """"ciphertext"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val ivMatch = """"iv"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val saltMatch = """"salt"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""

            val salt = saltMatch.decodeHex()
            val iv = ivMatch.decodeHex()
            val ciphertext = android.util.Base64.decode(ctMatch, android.util.Base64.DEFAULT)

            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = javax.crypto.spec.PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
            val secretKey = factory.generateSecret(spec)
            val secret = javax.crypto.spec.SecretKeySpec(secretKey.encoded, "AES")

            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secret, javax.crypto.spec.IvParameterSpec(iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            var finalUrl = String(decryptedBytes, Charsets.UTF_8).replace("\\/", "/")

            if (finalUrl.startsWith("://")) finalUrl = "https$finalUrl"
            else if (finalUrl.startsWith("//")) finalUrl = "https:$finalUrl"
            else if (!finalUrl.startsWith("http")) finalUrl = "https://$finalUrl"

            finalUrl
        } catch (e: Exception) {
            ""
        }
    }

    private fun getHeaders(baseUrl: String): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
            "Referer" to baseUrl
        )
    }
}
