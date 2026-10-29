package com.safakstream.dizipal

import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
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
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DiziPal : MainAPI() {
    override var mainUrl = "https://dizipal1592.com"
    override var name = "DiziPal"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Trend"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        return try {
            val html = app.get(request.data, headers = headers + mapOf("Referer" to mainUrl)).text
            val rsc = extractRsc(html)
            val trend = extractJsonArrayAfter(rsc, "\"trend\":")
                ?: return newHomePageResponse(request.name, emptyList())
            val items = mutableListOf<SearchResponse>()
            for (i in 0 until trend.length()) {
                val item = trend.optJSONObject(i) ?: continue
                val title = item.optString("t", "").ifBlank { continue }
                val href = item.optString("href", "").ifBlank { continue }
                val img = item.optString("img", "").substringBefore("#")
                val meta = item.optString("meta", "")
                val isMovie = href.startsWith("/movies/")
                val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"
                val year = Regex("""^(\d{4})""").find(meta)?.groupValues?.get(1)?.toIntOrNull()
                if (isMovie) {
                    items.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                        this.posterUrl = img.ifBlank { null }
                        this.year = year
                    })
                } else {
                    items.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                        this.posterUrl = img.ifBlank { null }
                        this.year = year
                    })
                }
            }
            Log.d("DiziPal", "getMainPage: ${items.size} öğe")
            newHomePageResponse(request.name, items)
        } catch (e: Exception) {
            Log.e("DiziPal", "getMainPage error: ${e.message}")
            newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val body = JSONObject().apply {
                put("action", "searchContent")
                put("params", JSONArray().put(query))
            }

            val resp = app.post(
                "$mainUrl/api/mj",
                headers = headers + mapOf(
                    "Content-Type" to "application/json",
                    "Origin" to mainUrl,
                    "Referer" to "$mainUrl/kesfet"
                ),
                json = body
            ).text

            val root = JSONObject(resp)
            if (!root.optBoolean("ok", false)) {
                Log.e("DiziPal", "search: ok=false")
                return emptyList()
            }
            val results = root.optJSONArray("result") ?: return emptyList()
            val items = mutableListOf<SearchResponse>()
            for (i in 0 until results.length()) {
                val node = results.optJSONObject(i) ?: continue
                val slug = node.optString("used_slug", "").ifBlank { continue }
                val type = node.optString("used_type", "")
                val title = node.optString("object_name", "").ifBlank { continue }
                val poster = node.optString("object_poster_url", "").ifBlank { null }
                val year = node.optInt("object_release_year", 0).takeIf { it > 0 }
                val imdb = node.optDouble("object_related_imdb_point", 0.0).takeIf { it > 0 }
                val fullUrl = if (slug.startsWith("http")) slug else "$mainUrl/$slug"
                val isMovie = type.equals("Movies", ignoreCase = true) || slug.startsWith("movies/")
                if (isMovie) {
                    items.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                        this.posterUrl = poster
                        this.year = year
                        this.score = imdb?.let { Score.from10(it) }
                    })
                } else {
                    items.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                        this.posterUrl = poster
                        this.year = year
                        this.score = imdb?.let { Score.from10(it) }
                    })
                }
            }
            Log.d("DiziPal", "search: ${items.size} sonuç")
            items.distinctBy { it.url }
        } catch (e: Exception) {
            Log.e("DiziPal", "search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val html = app.get(url, headers = headers + mapOf("Referer" to mainUrl)).text
            val rsc = extractRsc(html)
            val data = extractJsonObjectAfter(rsc, "\"data\":{\"id\":")
                ?: run {
                    Log.e("DiziPal", "load: data not found")
                    return null
                }

            val ldDescription = extractLdJsonDescription(html)
            val title = data.optString("title", "").ifBlank { return null }
            val slots = data.optJSONObject("slots")
            val poster = slots?.optString("mjPoster")?.ifBlank { null }
            val year = slots?.optString("mjYear")?.toIntOrNull()
            val imdb = slots?.optString("mjImdb")?.toDoubleOrNull()
            val genres = mutableListOf<String>()
            slots?.optJSONArray("mjGenres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val label = arr.optJSONObject(i)?.optString("label", "") ?: ""
                    if (label.isNotBlank()) genres.add(label)
                }
            }

            val isMovie = data.optString("page", "") == "Movies"

            if (isMovie) {
                val duration = data.optInt("runtimeMin", 0).takeIf { it > 0 }
                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = ldDescription
                    this.tags = genres.ifEmpty { null }
                    this.duration = duration
                    this.score = imdb?.let { Score.from10(it) }
                }
            } else {
                val seasons = data.optJSONArray("seasons") ?: return null
                val episodes = mutableListOf<Episode>()
                for (si in 0 until seasons.length()) {
                    val season = seasons.optJSONObject(si) ?: continue
                    val seasonNo = season.optInt("no", si + 1)
                    val eps = season.optJSONArray("episodes") ?: continue
                    for (ei in 0 until eps.length()) {
                        val ep = eps.optJSONObject(ei) ?: continue
                        val epNo = ep.optInt("n", ei + 1)
                        val epTitle = ep.optString("title", "").ifBlank { "$seasonNo. Sezon $epNo. Bölüm" }
                        val epHref = ep.optString("href", "").ifBlank { continue }
                        val epUrl = if (epHref.startsWith("http")) epHref else "$mainUrl$epHref"
                        episodes.add(newEpisode(epUrl) {
                            this.name = epTitle
                            this.season = seasonNo
                            this.episode = epNo
                            this.posterUrl = poster
                        })
                    }
                }
                Log.d("DiziPal", "load series: $title, ${episodes.size} bölüm")
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = ldDescription
                    this.tags = genres.ifEmpty { null }
                    this.score = imdb?.let { Score.from10(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("DiziPal", "load error: ${e.message}")
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
            val rsc = extractRsc(html)
            val player = findPlayer(rsc) ?: run {
                Log.e("DiziPal", "loadLinks: player not found")
                return false
            }

            val key = player.optString("k", "")
            val enc = player.optJSONObject("enc")
            if (key.isBlank() || enc == null) {
                Log.e("DiziPal", "loadLinks: key/enc missing")
                return false
            }

            val iframeUrl = decryptPlayer(key, enc) ?: run {
                Log.e("DiziPal", "loadLinks: decrypt failed")
                return false
            }

            val absoluteIframe = when {
                iframeUrl.startsWith("//") -> "https:$iframeUrl"
                iframeUrl.startsWith("/") -> "$mainUrl$iframeUrl"
                else -> iframeUrl
            }
            Log.d("DiziPal", "iframe: $absoluteIframe")

            val iframeHtml = app.get(absoluteIframe, headers = headers + mapOf("Referer" to data)).text
            val npMatch = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(iframeHtml)
            if (npMatch == null) {
                Log.e("DiziPal", "loadLinks: NP_CONFIG not found")
                return false
            }
            val npJson = JSONObject(npMatch.groupValues[1])

            // Altyazılar
            npJson.optJSONArray("subtitles")?.let { subs ->
                for (i in 0 until subs.length()) {
                    val sub = subs.optJSONObject(i) ?: continue
                    val label = sub.optString("label", "").ifBlank { sub.optString("lang", "Altyazı") }
                    var subUrl = sub.optString("url", "")
                    if (subUrl.isEmpty()) continue
                    if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                    else if (subUrl.startsWith("/")) {
                        val baseDomain = Regex("""^https?://[^/]+""").find(absoluteIframe)?.value ?: ""
                        subUrl = "$baseDomain$subUrl"
                    }
                    Log.d("DiziPal", "subtitle: $label -> ${subUrl.take(80)}")
                    subtitleCallback.invoke(newSubtitleFile(label, subUrl) {
                        this.headers = this@DiziPal.headers + mapOf("Referer" to absoluteIframe)
                    })
                }
            }

            // Video kaynakları
            var any = false
            npJson.optJSONArray("sources")?.let { sources ->
                for (i in 0 until sources.length()) {
                    val src = sources.optJSONObject(i) ?: continue
                    val master = src.optString("master", "")
                    if (master.isEmpty()) continue
                    val label = src.optString("title", "DiziPal")
                    Log.d("DiziPal", "video: $label -> ${master.take(80)}")
                    callback.invoke(newExtractorLink(
                        source = name,
                        name = "DiziPal - $label",
                        url = master,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = absoluteIframe
                        this.headers = this@DiziPal.headers
                        this.quality = Qualities.P1080.value
                    })
                    any = true
                }
            }

            any
        } catch (e: Exception) {
            Log.e("DiziPal", "loadLinks error: ${e.message}")
            false
        }
    }

    // ===== Helpers =====

    private fun extractRsc(html: String): String {
        val sb = StringBuilder()
        val regex = Regex("""self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)""")
        regex.findAll(html).forEach { m ->
            try {
                val s = JSONTokener(m.groupValues[1]).nextValue() as String
                sb.append(s)
            } catch (_: Exception) {}
        }
        return sb.toString()
    }

    private fun extractJsonObjectAfter(text: String, key: String): JSONObject? {
        var searchStart = 0
        while (true) {
            val idx = text.indexOf(key, searchStart)
            if (idx < 0) return null
            val braceIdx = text.indexOf('{', idx)
            if (braceIdx < 0) return null
            val objStr = extractBalancedBraces(text, braceIdx)
            if (objStr == null) { searchStart = idx + 1; continue }
            return try { JSONObject(objStr) } catch (e: Exception) { searchStart = idx + 1; null }
        }
    }

    private fun extractJsonArrayAfter(text: String, key: String): JSONArray? {
        val idx = text.indexOf(key)
        if (idx < 0) return null
        val bracketIdx = text.indexOf('[', idx)
        if (bracketIdx < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in bracketIdx until text.length) {
            val ch = text[i]
            if (esc) { esc = false; continue }
            if (ch == '\\') { esc = true; continue }
            if (ch == '"') { inStr = !inStr; continue }
            if (inStr) continue
            if (ch == '[') depth++
            else if (ch == ']') {
                depth--
                if (depth == 0) {
                    return try { JSONArray(text.substring(bracketIdx, i + 1)) } catch (e: Exception) { null }
                }
            }
        }
        return null
    }

    private fun extractBalancedBraces(text: String, startIdx: Int): String? {
        if (text.getOrNull(startIdx) != '{') return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in startIdx until text.length) {
            val ch = text[i]
            if (esc) { esc = false; continue }
            if (ch == '\\') { esc = true; continue }
            if (ch == '"') { inStr = !inStr; continue }
            if (inStr) continue
            if (ch == '{') depth++
            else if (ch == '}') {
                depth--
                if (depth == 0) return text.substring(startIdx, i + 1)
            }
        }
        return null
    }

    private fun findPlayer(rsc: String): JSONObject? {
        var searchStart = 0
        while (true) {
            val idx = rsc.indexOf("\"player\":", searchStart)
            if (idx < 0) return null
            val braceIdx = rsc.indexOf('{', idx)
            if (braceIdx < 0) return null
            val objStr = extractBalancedBraces(rsc, braceIdx)
            if (objStr == null) { searchStart = idx + 1; continue }
            val obj = try { JSONObject(objStr) } catch (e: Exception) { searchStart = idx + 1; continue }
            if (obj.has("kind") && obj.has("enc")) return obj
            searchStart = idx + 1
        }
    }

    private fun decryptPlayer(key: String, enc: JSONObject): String? {
        return try {
            val ct = android.util.Base64.decode(enc.getString("ciphertext"), android.util.Base64.DEFAULT)
            val iv = hexToBytes(enc.getString("iv"))
            val salt = hexToBytes(enc.getString("salt"))

            val spec = PBEKeySpec(key.toCharArray(), salt, 999, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val keyBytes = factory.generateSecret(spec).encoded

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
            val decrypted = cipher.doFinal(ct)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("DiziPal", "decryptPlayer error: ${e.message}")
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

    private fun extractLdJsonDescription(html: String): String? {
        val regex = Regex("""<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(html).forEach { m ->
            try {
                val json = JSONObject(m.groupValues[1].trim())
                val desc = json.optString("description", "")
                if (desc.isNotBlank()) return desc
            } catch (_: Exception) {}
        }
        return null
    }
}
