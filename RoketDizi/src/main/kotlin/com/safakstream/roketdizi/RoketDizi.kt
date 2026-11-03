package com.safakstream.roketdizi

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class RoketDizi : MainAPI() {
    override var mainUrl = "https://roketdizi.life"
    override var name = "RoketDizi"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Popüler Diziler",
        "$mainUrl/" to "Güncel Bölümler",
        "$mainUrl/" to "Trend Filmler",
        "$mainUrl/" to "Son Eklenen Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val html = app.get(request.data, headers = headers, referer = mainUrl).text
            val doc = Jsoup.parse(html)
            
            // İlgili h2'yi bul
            val h2 = doc.select("h2").firstOrNull { it.text().equals(request.name, ignoreCase = true) }
            if (h2 == null) {
                Log.e("RoketDizi", "getMainPage '${request.name}' h2 bulunamadı")
                return newHomePageResponse(request.name, emptyList())
            }
            
            // En yakın <section class="rk-band"> bul
            val section = h2.parents().firstOrNull { it.tagName() == "section" }
            if (section == null) {
                Log.e("RoketDizi", "getMainPage '${request.name}' section bulunamadı")
                return newHomePageResponse(request.name, emptyList())
            }
            
            val items = mutableListOf<SearchResponse>()
            section.select("a[href^=/dizi/], a[href^=/film/]").forEach { a ->
                val href = a.attr("href")
                // Bölüm linkleriyse dizi linkine çevir
                val normalizedHref = if (href.contains("/bolum-") || href.contains("/sezon-")) {
                    href.substringBefore("/sezon-")
                } else href
                if (normalizedHref.startsWith("/dizi/") || normalizedHref.startsWith("/film/")) {
                    val item = a.toSearchResult(normalizedHref)
                    if (item != null) items.add(item)
                }
            }
            
            Log.d("RoketDizi", "getMainPage ${request.name}: ${items.size} öğe")
            newHomePageResponse(request.name, items.distinctBy { it.url })
        } catch (e: Exception) {
            Log.e("RoketDizi", "getMainPage error: ${e.message}")
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun Element.toSearchResult(customHref: String? = null): SearchResponse? {
        val href = (customHref ?: this.attr("href")).trim()
        if (href.isEmpty() || href.startsWith("#")) return null
        if (href.contains("/sezon-") || href.contains("/bolum-")) return null
        if (!href.startsWith("/dizi/") && !href.startsWith("/film/")) return null
        
        val img = this.selectFirst("img")
        val title = img?.attr("alt")?.replace(Regex("\\s+izle.*$", RegexOption.IGNORE_CASE), "")?.trim()
            ?: this.selectFirst("[class*=rk-card-t]")?.text()?.trim()
            ?: return null
        if (title.isEmpty()) return null
        
        val poster = img?.attr("src")?.ifBlank { null } ?: img?.attr("data-src")?.ifBlank { null }
        val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"
        val isMovie = href.startsWith("/film/")
        
        return if (isMovie) {
            newMovieSearchResponse(title, fullUrl, TvType.Movie) { this.posterUrl = poster }
        } else {
            newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) { this.posterUrl = poster }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$mainUrl/arama-yap?s=$encoded"
            val html = app.get(url, headers = headers, referer = mainUrl).text
            val doc = Jsoup.parse(html)
            
            val items = mutableListOf<SearchResponse>()
            val links = doc.select("a[href^=/dizi/], a[href^=/film/]")
            links.forEach { a ->
                val href = a.attr("href")
                if (!href.contains("/sezon-") && !href.contains("/bolum-")) {
                    val item = a.toSearchResult()
                    if (item != null) items.add(item)
                }
            }
            Log.d("RoketDizi", "search '$query': ${items.size} sonuç")
            items.distinctBy { it.url }
        } catch (e: Exception) {
            Log.e("RoketDizi", "search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val html = app.get(url, headers = headers, referer = mainUrl).text
            val doc = Jsoup.parse(html)
            
            val title = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.replace(Regex("\\s+izle.*$", RegexOption.IGNORE_CASE), "")?.trim()
                ?: return null
            
            val description = doc.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }
                ?: doc.selectFirst("meta[name=description]")?.attr("content")?.ifBlank { null }
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
            val year = Regex("(19|20)\\d{2}").find(title)?.value?.toIntOrNull()
            
            // Bölüm linkleri
            val epLinks = doc.select("a[href*=/sezon-][href*=/bolum-]")
                .mapNotNull { it.attr("href").takeIf { h -> Regex("/sezon-\\d+/bolum-\\d+").containsMatchIn(h) } }
                .distinct()
            
            // Ek olarak: sezon linkleri (tüm sezonları bul)
            val seasonLinks = doc.select("a[href*=/sezon-]")
                .mapNotNull { it.attr("href").takeIf { h -> Regex("/sezon-\\d+$").containsMatchIn(h) } }
                .distinct()
            
            val allEpisodes = mutableListOf<Episode>()
            val seen = mutableSetOf<String>()
            
            fun addEpisode(href: String) {
                if (!seen.add(href)) return
                val m = Regex("/sezon-(\\d+)/bolum-(\\d+)").find(href) ?: return
                val season = m.groupValues[1].toIntOrNull() ?: return
                val ep = m.groupValues[2].toIntOrNull() ?: return
                val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"
                allEpisodes.add(newEpisode(fullUrl) {
                    this.name = "$season. Sezon $ep. Bölüm"
                    this.season = season
                    this.episode = ep
                    this.posterUrl = poster
                })
            }
            
            epLinks.forEach { addEpisode(it) }
            
            // Ekstra sezonlara git
            seasonLinks.forEach { seasonUrl ->
                try {
                    val seasonHtml = app.get(if (seasonUrl.startsWith("http")) seasonUrl else "$mainUrl$seasonUrl", headers = headers, referer = url).text
                    val seasonDoc = Jsoup.parse(seasonHtml)
                    seasonDoc.select("a[href*=/sezon-][href*=/bolum-]").forEach { a ->
                        addEpisode(a.attr("href"))
                    }
                } catch (e: Exception) {
                    Log.e("RoketDizi", "season fetch error: ${e.message}")
                }
            }
            
            val sortedEps = allEpisodes.sortedWith(compareBy({ it.season }, { it.episode }))
            Log.d("RoketDizi", "load: $title, ${sortedEps.size} episodes")
            
            if (sortedEps.isNotEmpty()) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEps) {
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
        } catch (e: Exception) {
            Log.e("RoketDizi", "load error: ${e.message}")
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
            Log.d("RoketDizi", "loadLinks: $data")
            val html = app.get(data, headers = headers, referer = mainUrl).text
            val playerJson = extractPlayerJson(html)
            if (playerJson == null) {
                Log.e("RoketDizi", "player JSON yok")
                return false
            }
            
            // Kaynakları topla: ana sayfa + dublaj linkleri
            val toFetch = mutableSetOf<String>()
            toFetch.add(data)
            
            val sources = playerJson.optJSONArray("sources")
            if (sources != null) {
                for (i in 0 until sources.length()) {
                    val src = sources.optJSONObject(i) ?: continue
                    val href = src.optString("href", "")
                    if (href.isNotEmpty()) {
                        val full = if (href.startsWith("http")) href else "$mainUrl$href"
                        if (full != data) toFetch.add(full)
                    }
                }
            }
            
            var success = false
            for (fetchUrl in toFetch) {
                try {
                    val pageHtml = if (fetchUrl == data) html else app.get(fetchUrl, headers = headers, referer = mainUrl).text
                    val pj = if (fetchUrl == data) playerJson else extractPlayerJson(pageHtml)
                    if (pj == null) continue
                    
                    val enc = pj.optJSONObject("enc") ?: continue
                    val key = pj.optString("k", "")
                    if (key.isEmpty()) continue
                    
                    val iframeUrl = decryptCipher(enc, key)
                    if (iframeUrl == null || !iframeUrl.contains("pichive")) {
                        Log.e("RoketDizi", "decrypt sonuç geçersiz: $iframeUrl")
                        continue
                    }
                    
                    Log.d("RoketDizi", "iframe: ${iframeUrl.take(100)}")
                    val fullIframe = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                    try {
                        RoketDiziPichiveExtractor().getUrl(fullIframe, data, subtitleCallback, callback)
                        success = true
                    } catch (e: Exception) {
                        Log.e("RoketDizi", "extractor error: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e("RoketDizi", "fetch error: ${e.message}")
                }
            }
            success
        } catch (e: Exception) {
            Log.e("RoketDizi", "loadLinks error: ${e.message}")
            false
        }
    }

    private fun extractPlayerJson(html: String): JSONObject? {
        try {
            val unescaped = html.replace("\\\"", "\"").replace("\\\\", "\\")
            val idx = unescaped.indexOf("\"player\":")
            if (idx < 0) return null
            val start = unescaped.indexOf('{', idx)
            if (start < 0) return null
            var depth = 0
            var end = -1
            for (i in start until unescaped.length) {
                when (unescaped[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) { end = i + 1; break }
                    }
                }
            }
            if (end < 0) return null
            return JSONObject(unescaped.substring(start, end))
        } catch (e: Exception) {
            Log.e("RoketDizi", "extractPlayerJson error: ${e.message}")
            return null
        }
    }

    private fun decryptCipher(enc: JSONObject, key: String): String? {
        return try {
            val saltHex = enc.getString("salt")
            val ivHex = enc.getString("iv")
            val ctB64 = enc.getString("ciphertext")
            
            val saltBytes = hexToBytes(saltHex)
            val ivBytes = hexToBytes(ivHex)
            val ctBytes = Base64.decode(ctB64, Base64.DEFAULT)
            
            val spec = PBEKeySpec(key.toCharArray(), saltBytes, 999, 256)
            val kdf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val secretKey = SecretKeySpec(kdf.generateSecret(spec).encoded, "AES")
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBytes))
            String(cipher.doFinal(ctBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("RoketDizi", "decrypt error: ${e.message}")
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
