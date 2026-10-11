package com.safakstream.dizipal

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import org.json.JSONObject
import org.json.JSONArray
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DizipalProvider : MainAPI() {
    override var mainUrl = "https://dizipal1588.com"
    override var name = "Dizipal"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "tr"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.Anime
    )

    companion object {
        private const val PASSPHRASE = "3hPn4uCjTVtfYWcjIcoJQ4cL1WWk1qxXI39egLYOmNv6IblA7eKJz68uU3eLzux1biZLCms0quEjTYniGv5z1JcKbNIsDQFSeIZOBZJz4is6pD7UyWDggWWzTLBQbHcQFpBQdClnuQaMNUHtLHTpzCvZy33p6I7wFBvL4fnXBYH84aUIyWGTRvM2G5cfoNf4705tO2kv"
    }

    override val mainPage = mainPageOf(
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/hd-film-izle" to "Filmler",
        "$mainUrl/anime" to "Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}/page/$page"
        val document = app.get(url).document
        val items = document.select("a[data-dizipal-pageloader]")
            .filter { it.selectFirst("img.lazyload, img[data-src]") != null }
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href").takeIf { it.isNotBlank() } ?: return null
        val titleFromAttr = this.attr("title").removeSuffix(" izle").trim()
        val titleFromAlt = this.selectFirst("img[alt]")?.attr("alt")?.removeSuffix(" izle")?.trim()
        val titleFromSlug = href.substringAfterLast("/")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val title = titleFromAttr.takeIf { it.isNotBlank() && !it.contains("Dizipal", ignoreCase = true) }
            ?: titleFromAlt?.takeIf { it.isNotBlank() && !it.contains("Dizipal", ignoreCase = true) }
            ?: titleFromSlug
        val poster = this.selectFirst("img[data-src]")?.attr("data-src")
            ?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
        val type = when {
            href.contains("/movies/") -> TvType.Movie
            href.contains("/anime/") -> TvType.Anime
            else -> TvType.TvSeries
        }
        return newMovieSearchResponse(title, fixUrl(href), type) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url).document
        return document.select("a[data-dizipal-pageloader]")
            .filter { it.selectFirst("img.lazyload, img[data-src]") != null }
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val jsonLdScripts = document.select("script[type=application/ld+json]")
        var jsonObj: JSONObject? = null
        for (script in jsonLdScripts) {
            try {
                val parsed = JSONObject(script.data())
                val parsedType = parsed.optString("@type")
                if (parsedType == "TVSeries" || parsedType == "Movie" || parsedType == "TVEpisode") {
                    jsonObj = parsed
                    break
                }
            } catch (e: Exception) { }
        }

        if (jsonObj == null) return null
        val json = jsonObj

        val title = json.optString("name").ifBlank {
            document.selectFirst("h1")?.text() ?: return null
        }
        val description = json.optString("description").takeIf { it.isNotBlank() }
        val poster = json.optString("image").takeIf { it.isNotBlank() }
        val year = json.optString("datePublished").take(4).toIntOrNull()

        val type = json.optString("@type")
        val tvType = when (type) {
            "TVSeries" -> TvType.TvSeries
            "Movie" -> TvType.Movie
            else -> TvType.TvSeries
        }

        val episodes = mutableListOf<Episode>()
        if (tvType == TvType.TvSeries) {
            val seasonsRaw = json.opt("containsSeason")
            val seasonsList = mutableListOf<JSONObject>()
            when (seasonsRaw) {
                is JSONArray -> {
                    for (i in 0 until seasonsRaw.length()) {
                        seasonsRaw.optJSONObject(i)?.let { seasonsList.add(it) }
                    }
                }
                is JSONObject -> seasonsList.add(seasonsRaw)
            }
            for ((i, season) in seasonsList.withIndex()) {
                val seasonNum = season.optInt("seasonNumber", i + 1)
                val epsRaw = season.opt("episode")
                val epsList = mutableListOf<JSONObject>()
                when (epsRaw) {
                    is JSONArray -> {
                        for (j in 0 until epsRaw.length()) {
                            epsRaw.optJSONObject(j)?.let { epsList.add(it) }
                        }
                    }
                    is JSONObject -> epsList.add(epsRaw)
                }
                for ((j, ep) in epsList.withIndex()) {
                    val epName = ep.optString("name").ifBlank { "$seasonNum. Sezon ${j + 1}. Bölüm" }
                    val epUrl = ep.optString("url")
                    val epNum = ep.optInt("episodeNumber", j + 1)
                    if (epUrl.isNotBlank()) {
                        episodes.add(
                            newEpisode(epUrl) {
                                this.name = epName
                                this.episode = epNum
                                this.season = seasonNum
                            }
                        )
                    }
                }
            }
        }

        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Yöntem 1: Basit iframe (doluysa)
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")
        if (!iframeSrc.isNullOrBlank() && iframeSrc.length > 10 && !iframeSrc.startsWith("about:")) {
            loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            return true
        }

        // Yöntem 2: data-rm-k div'indeki şifreli JSON'u çöz
        val encryptedJson = document.selectFirst("div[data-rm-k=true]")?.text()
            ?: document.selectFirst("[data-rm-k]")?.text()

        if (!encryptedJson.isNullOrBlank()) {
            val iframeUrl = decryptDizipal(encryptedJson)
            if (!iframeUrl.isNullOrBlank()) {
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
                return true
            }
        }

        return false
    }

    private fun decryptDizipal(encryptedJson: String): String? {
        return try {
            val json = JSONObject(encryptedJson)
val ciphertextB64 = json.getString("ciphertext")
            val ivHex = json.getString("iv")
            val saltHex = json.getString("salt")

            val salt = hexToBytes(saltHex)
            val iv = hexToBytes(ivHex)
val ciphertext = android.util.Base64.decode(ciphertextB64, android.util.Base64.DEFAULT)
            val keySpec = PBEKeySpec(PASSPHRASE.toCharArray(), salt, 999, 256)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val secretKey = keyFactory.generateSecret(keySpec)
            val aesKey = SecretKeySpec(secretKey.encoded, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
