package com.safakstream

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
    override var mainUrl              = "https://dizipal1592.com"
    override var name                 = "DiziPal"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    // CloudFlare Koruması Atlatma Ayarları
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
        "${mainUrl}/hd-film-izle"                      to "Yeni Filmler",
        "${mainUrl}/kanal/netflix"                     to "Netflix",
        "${mainUrl}/kanal/exxen"                       to "Exxen",
        "${mainUrl}/kanal/max"                         to "Max",
        "${mainUrl}/kanal/disney"                      to "Disney+",
        "${mainUrl}/kanal/amazon"                      to "Amazon Prime",
        "${mainUrl}/kanal/tod"                         to "TOD (beIN)",
        "${mainUrl}/kanal/tabii"                       to "Tabii",
        "${mainUrl}/kanal/hulu"                        to "Hulu"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1 && !request.data.contains("/kanal/")) {
            if (request.data.contains("?")) "${request.data}&sayfa=$page" else "${request.data}?sayfa=$page"
        } else {
            request.data
        }

        val document = app.get(
            url, timeout = 10000, interceptor = interceptor, headers = getHeaders(mainUrl)
        ).document

        val home = mutableListOf<SearchResponse>()

        if (!request.data.contains("/kanal/") || page == 1) {
            if (request.data.contains("/yabanci-dizi-izle") || request.data.contains("/hd-film-izle")) {
                home.addAll(document.select("div.new-added-list div.bg-\\[\\#22232a\\]").mapNotNull { it.sonBolumler() })
            } else {
                home.addAll(document.select("div.bg-\\[\\#22232a\\]").mapNotNull { it.diziler() })
            }
        }

        if (request.data.contains("/kanal/")) {
            val channelIdFromDoc = document.selectFirst("input[name=channelId]")?.attr("value")
                ?: Regex("""channelId\s*[:=]\s*(\d+)""").find(document.html())?.groupValues?.get(1)
            val channelSlug = request.data.substringAfterLast("/")

            try {
                val apiResponse = app.post(
                    "${mainUrl}/bg/getserielistbychannel",
                    headers = mapOf(
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With" to "XMLHttpRequest"
                    ),
                    referer = request.data,
                    data = mapOf(
                        "cKey"       to "c61f91c5141d178450934fe81c0a2029",
                        "cValue"     to "MTc4NDQwNzIwMDhkMzJhNTc1YzUwOGU1ZjQwMjdjMjIyOWVjOGVhMTcwNGQyM2FjODM2YTI4YTU0NjUyMjI2ZmVjMzFkYzBkMWQyMWY4YzdiNA==",
                        "curPage"    to page.toString(),
                        "channelId"  to (channelIdFromDoc ?: "1"),
                        "languageId" to "2,3,4",
                        "slug"       to channelSlug
                    )
                )

                val mapper = jacksonObjectMapper()
                val rootNode = mapper.readTree(apiResponse.text)
                
                val htmlNode = rootNode.at("/data/html")
                if (!htmlNode.isMissingNode) {
                    val htmlContent = htmlNode.asText()
                    val parsedDoc = Jsoup.parse(htmlContent)
                    val apiResults = parsedDoc.select("div.bg-\\[\\#22232a\\]").mapNotNull { it.diziler() }

                    apiResults.forEach { res ->
                        if (home.none { it.url == res.url }) {
                            home.add(res)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DiziPal", "API Hatası: ${e.message}")
            }
        }

        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    private fun Element.sonBolumler(): SearchResponse? {
        val name      = this.selectFirst("img")?.attr("alt") ?: return null
        val episode   = this.selectFirst("div.episode")?.text()?.trim()
            ?.replace(". Sezon ", "x")?.replace(". Bölüm", "") ?: ""
        val title     = if (episode.isNotEmpty()) "$name $episode" else name

        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href.substringBefore("/sezon"), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst("img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun SearchItem.toPostSearchResult(): SearchResponse {
        val title     = this.title
        val href      = "${mainUrl}/${this.slug}"
        val posterUrl = this.poster

        return if (this.type == "series") {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val responseRaw = app.post(
            "${mainUrl}/bg/searchcontent",
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/",
            data = mapOf(
                "cKey" to "c61f91c5141d178450934fe81c0a2029",
                "cValue" to "MTc4NDQwNzIwMDhkMzJhNTc1YzUwOGU1ZjQwMjdjMjIyOWVjOGVhMTcwNGQyM2FjODM2YTI4YTU0NjUyMjI2ZmVjMzFkYzBkMWQyMWY4YzdiNA==",
                "type" to "hepsi", 
                "searchterm" to query
            )
        )

        val mapper = jacksonObjectMapper()
        val rootNode = mapper.readTree(responseRaw.text)
        val resultArrayNode = rootNode.at("/data/result")

        if (resultArrayNode.isMissingNode || !resultArrayNode.isArray) {
            return emptyList()
        }

        val searchItems: List<SearchItem> = mapper.readValue(resultArrayNode.traverse())
        return searchItems.map { it.toPostSearchResult() }
    }
    
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

        override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor, headers = getHeaders(mainUrl)).document

        // --- 1. ORTAK BİLGİLER ---
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.trim()
            ?: "Bilinmeyen İçerik"

        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: document.selectFirst("div.summary p")?.text()?.trim()

        // Küçük Poster (Afiş)
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("div.page-top img[alt]")?.attr("src")
            ?: document.selectFirst("img[itemprop=image]")?.attr("src")

        // Büyük poster = Küçük poster (Aynı görseli kullan)
        val backgroundPoster = poster

        // --- 2. YIL, SÜRE, ETİKETLER ---
        val year = document.selectXpath("//div[text()='Yıl']//following-sibling::div").text().trim().toIntOrNull()
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Süre']//following-sibling::div").text())?.value?.toIntOrNull()
        val tags = document.selectXpath("//div[text()='Kategoriler']//following-sibling::div").text().trim()
            .split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        // --- 3. BÖLÜM LİSTESİ KONTROLÜ ---
        val episodeElements = document.select("div.relative.w-full.flex.items-start.gap-4")
        val hasEpisodes = episodeElements.isNotEmpty() || 
                          document.selectFirst("a[data-dizipal-pageloader]") != null ||
                          document.selectFirst("div.episode") != null

        // --- 4. DİZİ Mİ FİLM Mİ? ---
        if (hasEpisodes && episodeElements.isNotEmpty()) {
            // --- DİZİ YÜKLEME ---
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
            // --- FİLM YÜKLEME ---
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
        Log.d("DiziPal", "--> loadLinks ÇAĞRILDI. Gelen URL: $data")
        val doc = app.get(data).document
        
        val encryptedText = doc.selectFirst("div[data-rm-k=true]")?.text() ?: ""
        Log.d("DiziPal", "--> Şifreli metin uzunluğu: ${encryptedText.length}")
        
        var iframeUrl = if (encryptedText.isNotEmpty()) {
            Log.d("DiziPal", "--> Şifreli veri bulundu, decrypt işlemine geçiliyor...")
            decryptDizipalData(encryptedText)
        } else {
            Log.w("DiziPal", "--> DİKKAT: Şifreli veri DOM'da YOK! Fallback iframe aranıyor...")
            doc.selectFirst("iframe")?.attr("src") ?: ""
        }

        Log.d("DiziPal", "--> Elde edilen Ham Iframe URL: $iframeUrl")

        if (iframeUrl.isNotEmpty()) {
            if (iframeUrl.startsWith("//")) {
                iframeUrl = "https:$iframeUrl"
            }
            Log.d("DiziPal", "--> Extractor'a gönderilen Final URL: $iframeUrl")
            
            DizipalPlayer().getUrl(
                url = iframeUrl,
                referer = data,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        } else {
            Log.e("DiziPal", "--> HATA: iframeUrl tamamen BOŞ. Video linki bulunamadı!")
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

            val ctMatch = """"ciphertext"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) 
                ?: return "".also { Log.e("DiziPal", "--> HATA: Regex 'ciphertext' değerini bulamadı!") }
                
            val ivMatch = """"iv"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) 
                ?: return "".also { Log.e("DiziPal", "--> HATA: Regex 'iv' değerini bulamadı!") }
                
            val saltMatch = """"salt"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) 
                ?: return "".also { Log.e("DiziPal", "--> HATA: Regex 'salt' değerini bulamadı!") }

            Log.d("DiziPal", "--> Regex başarılı. Key türetiliyor...")

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

            Log.d("DiziPal", "--> AES Çözümleme Başarılı. İlk Çıktı: $finalUrl")

            if (finalUrl.startsWith("://")) {
                finalUrl = "https$finalUrl"
            } else if (finalUrl.startsWith("//")) {
                finalUrl = "https:$finalUrl"
            } else if (!finalUrl.startsWith("http")) {
                finalUrl = "https://$finalUrl"
            }

            finalUrl
        } catch (e: Exception) {
            Log.e("DiziPal", "--> HATA: Decryption sırasında Exception fırlatıldı! Mesaj: ${e.message}")
            e.printStackTrace()
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
