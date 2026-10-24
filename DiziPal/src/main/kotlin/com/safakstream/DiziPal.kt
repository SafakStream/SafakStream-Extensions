package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziPal : MainAPI() {
    override var mainUrl = "https://dizipal1592.com"
    override var name = "DiziPal"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())
            if (doc.html().contains("Just a moment")) return cloudflareKiller.intercept(chain)
            return response
        }
    }

    override val mainPage = mainPageOf("$mainUrl" to "Ana Sayfa")

    // --- KESİN ÇÖZÜM: Next.js Gömülü Verisini (RSC) Ayıklama ---
    private fun parseNextData(html: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            // HTML içindeki tüm script etiketlerini al
            val scripts = Jsoup.parse(html).select("script").map { it.data() }.joinToString(" ")
            
            // Next.js verisini bul: id'si dizi veya film ile başlayan JSON objelerini ara
            val regex = """\{"id":"(dizi|film)\d+".*?\}""".toRegex()
            
            regex.findAll(scripts).forEach { match ->
                val json = match.value
                val title = Regex(""""t":"([^"]+)"""").find(json)?.groupValues?.get(1)
                val href = Regex(""""href":"([^"]+)"""").find(json)?.groupValues?.get(1)
                val poster = Regex(""""img":"([^"]+)"""").find(json)?.groupValues?.get(1)

                if (title != null && href != null) {
                    val fullUrl = "$mainUrl$href"
                    if (href.contains("/series/")) {
                        results.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) { this.posterUrl = poster })
                    } else if (href.contains("/movies/")) {
                        results.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) { this.posterUrl = poster })
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DiziPal", "RSC Parse Hatası: ${e.message}")
        }
        return results.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val html = app.get(mainUrl, interceptor = interceptor, headers = getHeaders()).text
        val home = parseNextData(html)
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?q=$query"
        val html = app.get(searchUrl, interceptor = interceptor, headers = getHeaders()).text
        return parseNextData(html)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor, headers = getHeaders()).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "Bilinmeyen"
        val desc = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        
        // Detay sayfasındaki veriler için HTML (Jsoup) kullanıyoruz, bu kısım genelde çalışır.
        val year = document.selectXpath("//div[text()='Yıl']//following-sibling::div").text().trim().toIntOrNull()
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Süre']//following-sibling::div").text())?.value?.toIntOrNull()
        val tags = document.selectXpath("//div[text()='Kategoriler']//following-sibling::div").text().trim().split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        val episodes = document.select("div.relative.w-full.flex.items-start.gap-4").mapNotNull { el ->
            val link = el.selectFirst("a[data-dizipal-pageloader]") ?: return@mapNotNull null
            val epHref = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            newEpisode(epHref) { this.name = link.selectFirst("h2")?.text()?.trim() ?: "Bölüm" }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.backgroundPosterUrl = poster; this.year = year; this.plot = desc; this.tags = tags; this.duration = duration
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.backgroundPosterUrl = poster; this.year = year; this.plot = desc; this.tags = tags; this.duration = duration
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data).document
        val enc = doc.selectFirst("div[data-rm-k=true]")?.text() ?: ""
        var iframeUrl = if (enc.isNotEmpty()) decryptDizipalData(enc) else doc.selectFirst("iframe")?.attr("src") ?: ""
        if (iframeUrl.startsWith("//")) iframeUrl = "https:$iframeUrl"
        if (iframeUrl.isNotEmpty()) DizipalPlayer().getUrl(iframeUrl, data, subtitleCallback, callback)
        return true
    }

    private fun String.decodeHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun decryptDizipalData(rawJsonText: String): String {
        return try {
            val pass = "3hPn4uCjTVtfYWcjIcoJQ4cL1WWk1qxXI39egLYOmNv6IblA7eKJz68uU3eLzux1biZLCms0quEjTYniGv5z1JcKbNIsDQFSeIZOBZJz4is6pD7UyWDggWWzTLBQbHcQFpBQdClnuQaMNUHtLHTpzCvZy33p6I7wFBvL4fnXBYH84aUIyWGTRvM2G5cfoNf4705tO2kv"
            val ct = """"ciphertext"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val iv = """"iv"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val salt = """"salt"\s*:\s*"([^"]+)"""".toRegex().find(rawJsonText)?.groupValues?.get(1) ?: return ""
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            val spec = javax.crypto.spec.PBEKeySpec(pass.toCharArray(), salt.decodeHex(), 999, 256)
            val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key.encoded, "AES"), javax.crypto.spec.IvParameterSpec(iv.decodeHex()))
            var finalUrl = String(cipher.doFinal(android.util.Base64.decode(ct, android.util.Base64.DEFAULT)), Charsets.UTF_8).replace("\\/", "/")
            if (finalUrl.startsWith("://")) finalUrl = "https$finalUrl" else if (finalUrl.startsWith("//")) finalUrl = "https:$finalUrl" else if (!finalUrl.startsWith("http")) finalUrl = "https://$finalUrl"
            finalUrl
        } catch (e: Exception) { "" }
    }

    private fun getHeaders() = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )
}
