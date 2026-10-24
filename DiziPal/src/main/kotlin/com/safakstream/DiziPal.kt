package com.safakstream

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
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import com.lagradost.cloudstream3.newSubtitleFile

class DiziPal : MainAPI() {
    override var mainUrl              = "https://dizipal1822.com"
    override var name                 = "DiziPal"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val appCKeyPassword = "3hPn4uCjTVtfYWcjIcoJQ4cL1WWk1qxXI39egLYOmNv6IblA7eKJz68uU3eLzux1biZLCms0quEjTYniGv5z1JcKbNIsDQFSeIZOBZJz4is6pD7UyWDggWWzTLBQbHcQFpBQdClnuQaMNUHtLHTpzCvZy33p6I7wFBvL4fnXBYH84aUIyWGTRvM2G5cfoNf4705tO2kv"

    override val mainPage = mainPageOf("${mainUrl}/" to "Ana Sayfa")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        val document = app.get(request.data, headers = mapOf("User-Agent" to userAgent)).document
        val items = mutableListOf<SearchResponse>()
        for (li in document.select("li[data-u]")) {
            val type = li.attr("data-k"); val title = li.attr("data-t"); val url = li.attr("data-u"); val poster = li.attr("data-p")
            if (url.isBlank()) continue
            val response = if (type.equals("Film", true)) newMovieSearchResponse(title, url, TvType.Movie) else newTvSeriesSearchResponse(title, url, TvType.TvSeries)
            response.posterUrl = poster
            if (items.none { it.url == url }) items.add(response)
        }
        if (items.isEmpty()) {
            for (a in document.select("a.trend-rank-card")) {
                val href = a.attr("href"); val title = a.attr("title").removeSuffix(" izle").trim()
                if (href.isBlank() || title.isBlank()) continue
                val isMovie = href.contains("/film/")
                val response = if (isMovie) newMovieSearchResponse(title, href, TvType.Movie) else newTvSeriesSearchResponse(title, href, TvType.TvSeries)
                if (items.none { it.url == href }) items.add(response)
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post("${mainUrl}/bg/searchcontent", headers = mapOf("User-Agent" to userAgent, "X-Requested-With" to "XMLHttpRequest", "Referer" to "$mainUrl/"), data = mapOf("searchterm" to query))
        val text = response.text.trim(); if (text.isBlank()) return emptyList()
        val results = mutableListOf<SearchResponse>()
        try {
            val root = JSONObject(text); val data = root.optJSONObject("data") ?: return emptyList(); val arr = data.optJSONArray("result") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i); val title = obj.optString("object_name"); if (title.isBlank()) continue
                val slug = obj.optString("used_slug").replace("\\/", "/"); val usedType = obj.optString("used_type"); val poster = obj.optString("object_poster_url")
                if (slug.isBlank()) continue
                val fullUrl = if (slug.startsWith("http")) slug else "$mainUrl/$slug"
                val searchResp = if (usedType.equals("Movies", true)) newMovieSearchResponse(title, fullUrl, TvType.Movie) else newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries)
                searchResp.posterUrl = poster; results.add(searchResp)
            }
        } catch (e: Exception) {}
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content") ?: document.selectFirst("meta[name=description]")?.attr("content")
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" | ")?.substringBefore(" İzle")?.trim() ?: document.selectFirst("h1")?.text()?.trim() ?: return null
        if (url.contains("/film/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) { this.posterUrl = poster; this.plot = description }
        } else {
            val episodes = mutableListOf<Episode>()
            for (a in document.select("a[href*='-sezon/'][href*='-bolum']")) {
                val href = a.attr("href").let { if (it.startsWith("http")) it else "$mainUrl$it" }
                if (!href.contains("-sezon/")) continue
                val epName = a.attr("aria-label").ifBlank { a.text().trim() }.ifBlank { null } ?: continue
                val seasonMatch = Regex("(\\d+)-sezon").find(href); val episodeMatch = Regex("(\\d+)-bolum").find(href)
                episodes.add(newEpisode(href) { this.name = epName; this.season = seasonMatch?.groupValues?.get(1)?.toIntOrNull(); this.episode = episodeMatch?.groupValues?.get(1)?.toIntOrNull() })
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinctBy { it.data }) { this.posterUrl = poster; this.plot = description }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            val document = app.get(data, headers = mapOf("User-Agent" to userAgent)).document
            val encryptedJson = document.selectFirst("div[data-rm-k]")?.text() ?: return false
            if (encryptedJson.isBlank()) return false
            val jsonObj = JSONObject(encryptedJson)
            val decrypted = decryptData(jsonObj.getString("ciphertext"), jsonObj.getString("iv"), jsonObj.getString("salt"), appCKeyPassword)
            val iframeUrl = if (decrypted.startsWith("//")) "https:$decrypted" else decrypted
            val iframeHtml = app.get(iframeUrl, headers = mapOf("User-Agent" to userAgent, "Referer" to "$mainUrl/")).text
            val playList = Regex("""openPlayer\('([^']+)'""").find(iframeHtml)?.groupValues?.get(1) ?: return false
            val iframeBase = Regex("""^(https?://[^/]+)""").find(iframeUrl)?.groupValues?.get(1) ?: return false
            val sourceJson = app.get("$iframeBase/source2.php?v=$playList", headers = mapOf("User-Agent" to userAgent, "Referer" to "$iframeBase/")).text
            val sourceObj = JSONObject(sourceJson); val playlistArray = sourceObj.optJSONArray("playlist") ?: return false
            if (playlistArray.length() == 0) return false
            val sourcesArray = playlistArray.getJSONObject(0).optJSONArray("sources") ?: return false
            if (sourcesArray.length() == 0) return false
            val m3u8Url = sourcesArray.getJSONObject(0).getString("file")
            callback.invoke(newExtractorLink(source = this.name, name = this.name, url = m3u8Url, type = ExtractorLinkType.M3U8) { this.referer = "$iframeBase/"; this.headers = mapOf("User-Agent" to userAgent, "Referer" to "$iframeBase/") })

            // Altyazıları indir ve HAM VERİ olarak ver (newSubtitleFile)
            val subRegex = Regex(""""file":"([^"]+?\.vtt[^"]*?)","label":"([^"]+?)"""")
            for (match in subRegex.findAll(iframeHtml)) {
                val subUrl = match.groupValues[1].replace("\\/", "/")
                val subLabel = match.groupValues[2].replace("\\u0130", "İ").replace("\\u0131", "ı").replace("\\u00fc", "ü").replace("\\u00e7", "ç").replace("\\u015f", "ş").replace("\\u011f", "ğ").replace("\\u00f6", "ö")
                try {
                    val subBytes = app.get(subUrl, headers = mapOf("User-Agent" to userAgent, "Referer" to "$iframeBase/")).body.bytes()
                    val subContent = String(subBytes, Charsets.UTF_8)
                    subtitleCallback.invoke(newSubtitleFile(subLabel, subContent))
                } catch (e: Exception) {}
            }
            return true
        } catch (e: Exception) { return false }
    }

    private fun decryptData(ciphertextB64: String, ivHex: String, saltHex: String, password: String): String {
        val salt = hexToBytes(saltHex); val iv = hexToBytes(ivHex); val ciphertext = android.util.Base64.decode(ciphertextB64, android.util.Base64.DEFAULT)
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 999, 256)
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
    private fun hexToBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
