package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class FilmmakinesiProvider : MainAPI() {
    override var mainUrl = "https://filmmakinesi.to"
    override var name = "FilmMakinesi"
    override var lang = "tr"
    
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Son Eklenenler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "$mainUrl/filmler-1/sayfa/$page/"
        val document = app.get(url).document

        val homeItems = document.select("div.film-list div.content div.col-6 a.item").mapNotNull { it.toSearchResult() }
        val seriesItems = document.select("div.tv--section a.item").mapNotNull { it.toSearchResult() }

        val allItems = (homeItems + seriesItems).distinctBy { it.url }

        return newHomePageResponse(
            list = listOf(HomePageList(request.name, allItems)),
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/arama/?s=$query"
        val document = app.get(url).document

        val results = document.select("div.film-list div.content div.col-6 a.item, div.content div.col-6 a.item, a.item").mapNotNull { it.toSearchResult() }

        if (results.isEmpty()) {
            val fallbackUrl = "$mainUrl/?s=$query"
            val fallbackDoc = app.get(fallbackUrl).document
            return fallbackDoc.select("a.item").mapNotNull { it.toSearchResult() }
        }

        return results.distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href").takeIf { it.isNotEmpty() } ?: return null
        val title = this.selectFirst("div.item-footer div.title")?.text()
            ?: this.selectFirst("div.item-title")?.text()
            ?: this.selectFirst("div.title")?.text()
            ?: return null

        val posterUrl = this.selectFirst("img.thumbnail")?.attr("src")
            ?: this.selectFirst("img")?.attr("src")

        val year = this.selectFirst("div.item-footer div.info span")?.text()?.toIntOrNull()
        val isSeries = href.contains("/dizi/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
            }
        } else {
            newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text()?.substringBefore(" izle")?.trim()
            ?: document.selectFirst("h1")?.text() ?: return null

        val posterUrl = document.selectFirst("img.cover-img")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val year = document.selectFirst("h1.title span.date a")?.text()?.toIntOrNull()
        val description = document.selectFirst("div.info-description p")?.text()
        val imdb = document.selectFirst("div.imdb b")?.text()
        val duration = document.selectFirst("div.time")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val genres = document.select("div.type a").map { it.text() }
        val isSeries = url.contains("/dizi/")

        return if (isSeries) {
            val episodes = document.select("div.bolum-list a.item-ep").mapNotNull { ep ->
                val epHref = ep.attr("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val epTitle = ep.selectFirst("div.ep-title")?.text() ?: "Bölüm"
                val epName = ep.selectFirst("div.ep-details span")?.text()
                
                // SEZON DÜZELTMESİ: Başlıktan Sezon ve Bölüm numaralarını ayıklıyoruz
                val seasonMatch = Regex("""(\d+)\. ?Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                val episodeMatch = Regex("""(\d+)\. ?Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(fixUrl(epHref)) {
                    this.name = if (epName != null) "$epTitle - $epName" else epTitle
                    this.season = seasonMatch
                    this.episode = episodeMatch
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
                this.plot = description
                this.tags = genres
                this.duration = duration
                this.score = imdb?.replace(",", ".")?.toDoubleOrNull()?.let { Score.from10(it) }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrlNull(posterUrl)
                this.year = year
                this.plot = description
                this.tags = genres
                this.duration = duration
                this.score = imdb?.replace(",", ".")?.toDoubleOrNull()?.let { Score.from10(it) }
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
        var found = false

        document.select("div.video-parts a[data-video_url]").forEach { element ->
            val videoUrl = element.attr("data-video_url")
            val sourceName = element.text().trim()
            
            if (videoUrl.isNotEmpty()) {
                val linkName = when {
                    videoUrl.contains("closeload") -> "$sourceName (Close)"
                    videoUrl.contains("rapid") -> "$sourceName (Rapid)"
                    else -> sourceName
                }
                
                try {
                    invokeLocalSource(linkName, videoUrl, subtitleCallback, callback)
                    found = true
                } catch (e: Exception) {
                    Log.e("FM", "Local source hatası: ${e.message}")
                    loadExtractor(videoUrl, "$mainUrl/", subtitleCallback, callback)
                    found = true
                }
            }
        }

        return found
    }

    private suspend fun invokeLocalSource(source: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit ) {
        val document = app.get(url, referer = "$mainUrl/").document
        val rawHtml = document.html()
        val scripts = document.select("script").map { it.data() }.filter { it.isNotBlank() }

        var decryptedUrl: String? = null
        var foundScript: String? = null
        var foundUnpacked: String? = null

        val unpackedFromHtml = unpackPackerJs(rawHtml)
        if (unpackedFromHtml != null) {
            val res = decryptLocalUrl(unpackedFromHtml, rawHtml)
            if (res != null) {
                decryptedUrl = res
                foundUnpacked = unpackedFromHtml
            }
        }

        if (decryptedUrl == null) {
            for (script in scripts) {
                if (script.contains("Date.now") && !script.contains("eval(") && !script.contains("sources") && !script.contains("vf9q") && !script.contains("master.txt") && !script.contains("dc_")) continue
                val unpackedScript = unpackPackerJs(script) ?: getAndUnpack(script)
                val res = decryptLocalUrl(unpackedScript, rawHtml)
                if (res != null) {
                    decryptedUrl = res
                    foundScript = script
                    foundUnpacked = unpackedScript
                    break
                }
            }
        }

        if (decryptedUrl == null) {
            decryptedUrl = decryptLocalUrl(rawHtml, rawHtml)
        }

        if (decryptedUrl == null) {
            Log.e("FM", "Could not decrypt video URL from $url")
            return
        }

        var cleanedUrl = decryptedUrl.replace("\\/", "/").replace("\\", "").trim().trim('"', '\'')
        if (!cleanedUrl.startsWith("http") && cleanedUrl.contains("http")) {
            cleanedUrl = "http" + cleanedUrl.substringAfter("http")
        }
        val lastUrl = fixUrl(cleanedUrl)
        Log.d("FM", "Final decrypted URL » $lastUrl")

        val candidates = listOfNotNull(rawHtml, foundScript, foundUnpacked)
        extractSubtitles(candidates, subtitleCallback)

        val finalUrl = if (lastUrl.endsWith(".txt")) {
            lastUrl.replace(".txt", ".m3u8")
        } else {
            lastUrl
        }

        val uri = try { java.net.URI(url) } catch (e: Exception) { null }
        val origin = if (uri?.host != null) "${uri.scheme}://${uri.host}" else mainUrl
        val refererUrl = if (url.startsWith("http")) url else "$mainUrl/"

        val streamHeaders = mapOf(
            "Referer" to refererUrl,
            "Origin" to origin,
            "Accept" to "*/*",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )

        callback.invoke(
            newExtractorLink(
                source  = source,
                name    = source,
                url     = finalUrl,
                type    = INFER_TYPE
            ) {
                this.referer = refererUrl
                this.headers = streamHeaders
                this.quality = Qualities.Unknown.value
            }
        )
    }

    private fun atob(s: String): String {
        return try {
            var str = s.trim()
            val padding = (4 - str.length % 4) % 4
            if (padding != 0) str += "=".repeat(padding)
            android.util.Base64.decode(str, android.util.Base64.DEFAULT).toString(Charsets.ISO_8859_1)
        } catch (e: Exception) {
            ""
        }
    }

    private fun caesarShift(text: String, shift: Int): String {
        return text.map { c ->
            when {
                c in 'A'..'Z' -> ((c.code - 'A'.code + shift) % 26 + 'A'.code).toChar()
                c in 'a'..'z' -> ((c.code - 'a'.code + shift) % 26 + 'a'.code).toChar()
                else -> c
            }
        }.joinToString("")
    }

    private fun xorUnmix(text: String, accStart: Int, increment: Int): String {
        var acc = accStart
        val unmix = StringBuilder()
        for (i in text.indices) {
            val b = text[i].code
            acc = (acc + increment) % 256
            val plain = b xor acc
            acc = (acc + b) % 256
            unmix.append(plain.toChar())
        }
        return unmix.toString()
    }

    private fun unpackPackerJs(rawHtml: String): String? {
        return try {
            val startMarker = "eval(function(p,a,c,k,e,d){"
            val endMarker = ",0,{}))"
            val startIdx = rawHtml.indexOf(startMarker)
            if (startIdx == -1) return null
            val endIdx = rawHtml.indexOf(endMarker, startIdx + startMarker.length)
            if (endIdx == -1) return null
            val block = rawHtml.substring(startIdx, endIdx + endMarker.length)
            val packedStart = block.indexOf("}('") + 3
            val packedEnd = block.indexOf("',", packedStart)
            if (packedStart == -1 || packedEnd == -1) return null
            val packed = block.substring(packedStart, packedEnd)
            val afterPacked = block.substring(packedEnd + 2)
            val baseEnd = afterPacked.indexOf(",")
            if (baseEnd == -1) return null
            val base = afterPacked.substring(0, baseEnd).toInt()
            val afterBase = afterPacked.substring(baseEnd + 1)
            val countEnd = afterBase.indexOf(",")
            if (countEnd == -1) return null
            val count = afterBase.substring(0, countEnd).toInt()
            val dictQuoteStart = afterBase.indexOf("'") + 1
            val dictQuoteEnd = afterBase.indexOf("'.split", dictQuoteStart)
            if (dictQuoteStart == -1 || dictQuoteEnd == -1) return null
            val dictStr = afterBase.substring(dictQuoteStart, dictQuoteEnd)
            val dictionary = dictStr.split('|')
            val lookup = mutableMapOf<String, String>()

            fun packerEncode(num: Int, base: Int): String {
                val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                if (num == 0) return "0"
                var n = num
                val sb = StringBuilder()
                while (n > 0) {
                    sb.insert(0, digits[n % base])
                    n /= base
                }
                return sb.toString()
            }

            var c = count - 1
            while (c >= 0) {
                val key = packerEncode(c, base)
                lookup[key] = if (c < dictionary.size && dictionary[c].isNotEmpty()) dictionary[c] else key
                c--
            }

            var result = packed
            val sortedKeys = lookup.keys.sortedByDescending { it.length }
            for (key in sortedKeys) {
                val value = lookup[key]!!
                result = result.replace(Regex("\\b${Regex.escape(key)}\\b"), value)
            }
            result
        } catch (e: Exception) {
            Log.e("FM", "Unpack hatası: ${e.message}")
            null
        }
    }

    private fun extractFuncBody(jsCode: String, funcName: String): String? {
        val patterns = listOf("function $funcName", "$funcName = function", "$funcName=function", "$funcName : function", "$funcName:function")
        var startIdx = -1
        for (pattern in patterns) {
            val idx = jsCode.indexOf(pattern)
            if (idx != -1) { startIdx = idx; break }
        }
        if (startIdx == -1) return null
        val braceIdx = jsCode.indexOf('{', startIdx)
        if (braceIdx == -1) return null
        var braceCount = 1
        var i = braceIdx + 1
        while (braceCount > 0 && i < jsCode.length) {
            when (jsCode[i]) {
                '{' -> braceCount++
                '}' -> braceCount--
            }
            i++
        }
        return if (braceCount == 0) jsCode.substring(braceIdx + 1, i - 1) else null
    }

    private fun parseAndExecuteJs(funcBody: String, parts: List<String>): String? {
        return try {
            val seedMatch = Regex("""(?:var|let|const)\s+\w+\s*=\s*["']([^"']+)["']\s*;\s*(?:var|let|const)\s+\w+\s*=\s*["']([^"']+)["']""").find(funcBody) ?: run { return null }
            val seedStr = seedMatch.groupValues[1]
            val opsStr = seedMatch.groupValues[2]

            val isSplice = funcBody.contains("splice")
            val workingParts = parts.toMutableList()
            var u3e = workingParts.joinToString("")

            val multMatch = Regex("""\*\s*(\d+)\s*\+\s*[a-zA-Z0-9_]+\)\s*%\s*(\d+)""").find(funcBody)
            val hMult = multMatch?.groupValues?.get(1)?.toIntOrNull() ?: if (isSplice) 37 else 31
            val hMod1 = multMatch?.groupValues?.get(2)?.toIntOrNull() ?: if (isSplice) 241 else 251

            var gzx1 = 0
            var mff = 0
            for (i in seedStr.indices) {
                val ngm = seedStr[i].code
                gzx1 = (gzx1 * hMult + ngm) % hMod1
                if (isSplice) mff = (mff + ((ngm shl 1) xor i)) and 255 else mff = (mff xor (ngm + i)) and 255
            }

            val ghihx = if (isSplice) ((gzx1 * 3 + mff) % 256) else ((gzx1 + mff) % 256)
            val cv1 = if (isSplice) ((mff % 11) + 5) else ((gzx1 % 13) + 3)
            var pzvvv = if (isSplice) (((mff * 251 + gzx1) % 65519) + 1).toLong() else (((gzx1 * 256 + mff) % 65521) + 1).toLong()

            val rotBaseMatch = Regex("""charCodeAt\(0\)\s*-\s*(\d+)""").find(funcBody)
            val rotBase = rotBaseMatch?.groupValues?.get(1)?.toIntOrNull() ?: if (isSplice) 96 else 64

            for (i in opsStr.length - 1 downTo 0) {
                val ch = opsStr[i]
                u3e = when (ch) {
                    'b', '7' -> atob(u3e)
                    'v', '3' -> u3e.reversed()
                    else -> {
                        val oufo = (26 - ((ch.code - rotBase) % 26)) % 26
                        caesarShift(u3e, oufo)
                    }
                }
            }
            if (opsStr.length > 4096) u3e = u3e.reversed()

            val prngMatch = Regex("""\*\s*(\d+)\s*\+\s*(\d+)\)\s*%\s*(\d+)""").findAll(funcBody).lastOrNull()
            val pMult = prngMatch?.groupValues?.get(1)?.toLongOrNull() ?: if (isSplice) 97L else 75L
            val pAdd = prngMatch?.groupValues?.get(2)?.toLongOrNull() ?: if (isSplice) 41L else 74L
            val pMod = prngMatch?.groupValues?.get(3)?.toLongOrNull() ?: if (isSplice) 65519L else 65537L

            val imm = u3e.length
            if (imm > 1) {
                val irdt = IntArray(imm)
                for (sm7 in imm - 1 downTo 1) {
                    pzvvv = (pzvvv * pMult + pAdd) % pMod
                    irdt[sm7] = (pzvvv % (sm7 + 1)).toInt()
                }
                val arr = u3e.toCharArray()
                for (sm7 in 1 until imm) {
                    val j = irdt[sm7]
                    val tmp = arr[sm7]
                    arr[sm7] = arr[j]
                    arr[j] = tmp
                }
                u3e = String(arr)
            }
            val sb = StringBuilder(imm)
            var to4 = ghihx
            val multXor = if (isSplice) 5 else 1
            for (c in u3e) {
                val ngm = c.code
                to4 = (to4 * multXor + cv1) % 256
                sb.append((ngm xor to4).toChar())
                to4 = (to4 + ngm) % 256
            }

            val result = sb.toString()
            result.trim().takeIf { it.startsWith("http") || it.contains(".m3u8") || it.contains("master.txt") }
        } catch (e: Exception) {
            Log.e("FM", "JS Parser error: ${e.message}")
            null
        }
    }

    private fun decryptV1(valueParts: List<String>): String? {
        var value = valueParts.joinToString("")
        value = caesarShift(value, 9); value = caesarShift(value, 16)
        value = value.reversed()
        var decoded = atob(value); decoded = atob(decoded)
        return xorUnmix(decoded, 241, 11)
    }

    private fun decryptV2(valueParts: List<String>): String? {
        var value = valueParts.joinToString("")
        value = value.reversed(); value = caesarShift(value, 15)
        var decoded = atob(value); decoded = decoded.reversed(); decoded = atob(decoded)
        return xorUnmix(decoded, 185, 12)
    }

    private fun decryptV3(valueParts: List<String>): String? {
        var value = valueParts.joinToString("")
        var decoded = atob(value); decoded = atob(decoded)
        decoded = decoded.reversed(); decoded = caesarShift(decoded, 25); decoded = atob(decoded)
        return xorUnmix(decoded, 77, 9)
    }

    private fun decryptV4(valueParts: List<String>): String? {
        var value = valueParts.joinToString("")
        var decoded = atob(value); decoded = decoded.reversed(); decoded = atob(decoded)
        return xorUnmix(decoded, 130, 10)
    }

    private fun tryAllDecryptors(parts: List<String>): String? {
        val decryptors = listOf(::decryptV1, ::decryptV2, ::decryptV3, ::decryptV4)
        for ((index, decryptor) in decryptors.withIndex()) {
            try {
                val result = decryptor(parts)
                if (!result.isNullOrBlank() && result.contains("http")) {
                    Log.d("FM", "Fallback decryptor v${index + 1} başarılı!")
                    return result
                }
            } catch (e: Exception) {
                Log.d("FM", "Fallback decryptor v${index + 1} başarısız: ${e.message}")
            }
        }
        return null
    }

    private fun decryptVf9q(script: String, customParts: List<String>? = null): String? {
        try {
            val parts = if (customParts != null && customParts.isNotEmpty()) {
                customParts
            } else {
                val partsMatch = Regex("""\[\s*((?:['"][^'"]+['"]\s*,?\s*)+)\]""").find(script) ?: return null
                partsMatch.groupValues[1].split(",").map {
                    it.trim().trim('\'', '"').replace("\\/", "/")
                }.filter { it.isNotEmpty() }
            }
            if (parts.isEmpty()) return null

            val isSplice = script.contains("splice")
            val workingParts = parts.toMutableList()

            var qm0jd: String? = null
            var ou6q: String? = null

            val varMatches = Regex("""(?:var|let|const)\s+[a-zA-Z0-9_]+\s*=\s*["']([^"']+)["'];""").findAll(script).map { it.groupValues[1] }.toList()

            if (varMatches.size >= 2 && !isSplice) {
                qm0jd = varMatches[0]
                ou6q = varMatches[1]
            } else if (isSplice || workingParts.size > 2) {
                val dk0fc = workingParts.size - 2
                val fgzMatch = Regex("""%\s*(\d+)\s*,\s*[a-zA-Z0-9_]+\s*=\s*(\d+)\s*\+\s*\([a-zA-Z0-9_]+\s*%\s*(\d+)\)""").find(script)
                val fgzMod = fgzMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val ugBase = fgzMatch?.groupValues?.get(2)?.toIntOrNull() ?: 8
                val ugMod = fgzMatch?.groupValues?.get(3)?.toIntOrNull() ?: 5

                val fgz26 = dk0fc % fgzMod
                val ug9 = ugBase + (dk0fc % ugMod)

                if (ug9 < workingParts.size && fgz26 < workingParts.size - 1) {
                    ou6q = workingParts.removeAt(ug9)
                    qm0jd = workingParts.removeAt(fgz26)
                }
            }

            if (qm0jd == null || ou6q == null) {
                if (varMatches.size >= 2) {
                    qm0jd = varMatches[0]
                    ou6q = varMatches[1]
                } else {
                    return null
                }
            }

            var jc13 = workingParts.joinToString("")

            val multMatch = Regex("""\*\s*(\d+)\s*\+\s*[a-zA-Z0-9_]+\)\s*%\s*(\d+)""").find(script)
            val hMult = multMatch?.groupValues?.get(1)?.toIntOrNull() ?: if (isSplice) 37 else 31
            val hMod1 = multMatch?.groupValues?.get(2)?.toIntOrNull() ?: if (isSplice) 241 else 251

            var rea2 = 0
            var ec27 = 0
            for (uuv14 in qm0jd.indices) {
                val qq4 = qm0jd[uuv14].code
                rea2 = (rea2 * hMult + qq4) % hMod1
                if (isSplice) ec27 = (ec27 + ((qq4 shl 1) xor uuv14)) and 255 else ec27 = (ec27 xor (qq4 + uuv14)) and 255
            }

            val pv7 = if (isSplice) ((rea2 * 3 + ec27) % 256) else ((rea2 + ec27) % 256)
            val pa2 = if (isSplice) ((ec27 % 11) + 5) else ((rea2 % 13) + 3)
            var u4y3 = if (isSplice) (((ec27 * 251 + rea2) % 65519) + 1).toLong() else (((rea2 * 256 + ec27) % 65521) + 1).toLong()

            val rotBaseMatch = Regex("""charCodeAt\(0\)\s*-\s*(\d+)""").find(script)
            val rotBase = rotBaseMatch?.groupValues?.get(1)?.toIntOrNull() ?: if (isSplice) 96 else 64

            for (uuv14 in ou6q.length - 1 downTo 0) {
                val a6v = ou6q[uuv14]
                if (a6v == 'b' || a6v == '7') {
                    jc13 = atob(jc13)
                } else if (a6v == 'v' || a6v == '3') {
                    jc13 = jc13.reversed()
                } else {
                    val z4o18 = (26 - ((a6v.code - rotBase) % 26)) % 26
                    val sb = StringBuilder()
                    for (c in jc13) {
                        if (c in 'a'..'z') {
                            val shifted = (c.code - 97 + z4o18) % 26 + 97
                            sb.append(shifted.toChar())
                        } else if (c in 'A'..'Z') {
                            val shifted = (c.code - 65 + z4o18) % 26 + 65
                            sb.append(shifted.toChar())
                        } else {
                            sb.append(c)
                        }
                    }
                    jc13 = sb.toString()
                }
            }

            val prngMatch = Regex("""\*\s*(\d+)\s*\+\s*(\d+)\)\s*%\s*(\d+)""").findAll(script).lastOrNull()
            val pMult = prngMatch?.groupValues?.get(1)?.toLongOrNull() ?: if (isSplice) 97L else 75L
            val pAdd = prngMatch?.groupValues?.get(2)?.toLongOrNull() ?: if (isSplice) 41L else 74L
            val pMod = prngMatch?.groupValues?.get(3)?.toLongOrNull() ?: if (isSplice) 65519L else 65537L

            val j4l88 = jc13.length
            val sd0f6 = IntArray(j4l88)
            for (uuv14 in j4l88 - 1 downTo 1) {
                u4y3 = (u4y3 * pMult + pAdd) % pMod
                sd0f6[uuv14] = (u4y3 % (uuv14 + 1)).toInt()
            }

            val h43 = jc13.toCharArray()
            for (uuv14 in 1 until j4l88) {
                val gka3 = sd0f6[uuv14]
                val rv62 = h43[uuv14]
                h43[uuv14] = h43[gka3]
                h43[gka3] = rv62
            }
            jc13 = String(h43)

            var m3l = pv7
            val multXor = if (isSplice) 5 else 1
            val kmqj1 = StringBuilder()
            for (uuv14 in jc13.indices) {
                val qq4 = jc13[uuv14].code
                m3l = (m3l * multXor + pa2) % 256
                kmqj1.append((qq4 xor m3l).toChar())
                m3l = (m3l + qq4) % 256
            }

            return kmqj1.toString()
        } catch (e: Exception) {
            Log.e("FM", "decryptVf9q Error: ${e.message}")
            return null
        }
    }

    private fun decryptLocalUrl(unpackedScript: String, rawHtml: String = ""): String? {
        val contextText = if (rawHtml.isNotBlank()) rawHtml else unpackedScript

        val targetVar = contextText.lineSequence()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("/*") }
            .mapNotNull { line ->
                Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*([a-zA-Z0-9_$]+)""").find(line)?.groupValues?.get(1)
            }
            .firstOrNull()

        if (!targetVar.isNullOrBlank()) {
            val assignRegexes = listOf(
                Regex("""(?:var|let|const)?\s*$targetVar\s*=\s*([a-zA-Z0-9_$]+)\s*\((.*?)\);""", RegexOption.DOT_MATCHES_ALL),
                Regex("""(?:var|let|const)?\s*$targetVar\s*=\s*([a-zA-Z0-9_$]+)\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
            )
            val assignMatch = assignRegexes.firstNotNullOfOrNull { it.find(unpackedScript) ?: it.find(contextText) }

            if (assignMatch != null) {
                val funcName = assignMatch.groupValues[1]
                val argsRaw = assignMatch.groupValues[2].trim()

                val splitMatch = Regex("""^["']([^"']+)["']\s*\.\s*split\s*\(\s*["']([^"']+)["']\s*\)$""").find(argsRaw)
                val parts = if (splitMatch != null) {
                    val str = splitMatch.groupValues[1]
                    val delim = splitMatch.groupValues[2]
                    str.split(delim).map {
                        it.trim().trim('\'', '"').replace("\\/", "/").replace("\\\"", "\"").replace("\\", "")
                    }.filter { it.isNotEmpty() }
                } else if (argsRaw.startsWith("[") && argsRaw.endsWith("]")) {
                    var list = Regex(""""([^"]*)"""").findAll(argsRaw).map {
                        it.groupValues[1].replace("\\/", "/").replace("\\\"", "\"").replace("\\", "")
                    }.filter { it.isNotEmpty() }.toList()
                    if (list.isEmpty()) {
                        list = argsRaw.removeSurrounding("[", "]").split(",").map {
                            it.trim().trim('\'', '"').replace("\\/", "/").replace("\\\"", "\"").replace("\\", "")
                        }.filter { it.isNotEmpty() }
                    }
                    list
                } else {
                    emptyList()
                }

                if (parts.isNotEmpty()) {
                    val funcBody = extractFuncBody(unpackedScript, funcName) ?: extractFuncBody(contextText, funcName)
                    val scriptToAnalyze = funcBody ?: unpackedScript

                    val decryptedTarget = if (scriptToAnalyze.contains("splice")) {
                        decryptVf9q(scriptToAnalyze, parts) ?: decryptVf9q(unpackedScript, parts)
                    } else {
                        if (funcBody != null) {
                            parseAndExecuteJs(funcBody, parts) ?: decryptVf9q(scriptToAnalyze, parts)
                        } else {
                            decryptVf9q(scriptToAnalyze, parts)
                        }
                    } ?: tryAllDecryptors(parts)

                    if (!decryptedTarget.isNullOrBlank() && !decryptedTarget.contains("playmix.uno")) {
                        val cleaned = decryptedTarget.replace("\\/", "/").replace("\\", "").trim().trim('"', '\'')
                        return fixUrl(cleaned)
                    }
                }
            }
        }

        val varPattern = Regex("""(?:var|let|const)?\s*(?:\w+\s*=\s*)?(\w+)\s*\(\s*\[(.*?)\]\s*\)""", RegexOption.DOT_MATCHES_ALL)
        val varMatches = varPattern.findAll(unpackedScript).toList()
        for (varMatch in varMatches) {
            val funcName = varMatch.groupValues[1]
            val partsStr = varMatch.groupValues[2]
            var parts = Regex(""""([^"]*)"""").findAll(partsStr).map {
                it.groupValues[1].replace("\\/", "/").replace("\\\"", "\"").replace("\\", "")
            }.filter { it.isNotEmpty() }.toList()

            if (parts.isEmpty()) {
                parts = partsStr.split(",").map {
                    it.trim().trim('\'', '"').replace("\\/", "/").replace("\\\"", "\"").replace("\\", "")
                }.filter { it.isNotEmpty() }
            }

            if (parts.isNotEmpty()) {
                val funcBody = extractFuncBody(unpackedScript, funcName)
                if (funcBody != null) {
                    val dynamicUrl = if (funcBody.contains("splice")) {
                        decryptVf9q(funcBody, parts)
                    } else {
                        parseAndExecuteJs(funcBody, parts)
                    }
                    if (!dynamicUrl.isNullOrBlank() && !dynamicUrl.contains("playmix.uno")) {
                        val cleaned = dynamicUrl.replace("\\/", "/").replace("\\", "").trim().trim('"', '\'')
                        return fixUrl(cleaned)
                    }
                }
                val fallbackRes = tryAllDecryptors(parts)
                if (!fallbackRes.isNullOrBlank() && !fallbackRes.contains("playmix.uno")) {
                    val cleaned = fallbackRes.replace("\\/", "/").replace("\\", "").trim().trim('"', '\'')
                    return fixUrl(cleaned)
                }
            }
        }

        val vf9qResult = decryptVf9q(unpackedScript)
        if (vf9qResult != null && (vf9qResult.contains("http") || vf9qResult.contains(".m3u8") || vf9qResult.contains(".mp4") || vf9qResult.contains("master.txt")) && !vf9qResult.contains("playmix.uno")) {
            val cleaned = vf9qResult.replace("\\/", "/").replace("\\", "").trim().trim('"', '\'')
            return fixUrl(cleaned)
        }

        val directMatch = Regex("""["'](https?:[\\/]+[^"']+(?:\.m3u8|/master\.txt|/playlist\.m3u8)[^"']*)["']""").find(unpackedScript)
            ?: Regex("""["'](https?:[\\/]+[^"']+(?:\.m3u8|/master\.txt|/playlist\.m3u8)[^"']*)["']""").find(rawHtml)
        if (directMatch != null) {
            val raw = directMatch.groupValues[1].replace("\\/", "/").replace("\\", "").trim().trim('"', '\'')
            if (!raw.contains("playmix.uno")) {
                return fixUrl(raw)
            }
        }

        return null
    }

    private fun unescapeUnicode(str: String): String {
        val regex = Regex("""\\u([0-9a-fA-F]{4})""")
        return regex.replace(str) { matchResult ->
            matchResult.groupValues[1].toInt(16).toChar().toString()
        }
    }

    private suspend fun extractSubtitles(candidates: List<String>, subtitleCallback: (SubtitleFile) -> Unit) {
        val seenFiles = mutableSetOf<String>()
        for (candidate in candidates) {
            val tracksBlockMatch = Regex("""tracks\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(candidate)
            val block = tracksBlockMatch?.groupValues?.get(1) ?: continue

            val trackObjRegex = Regex("""\{[^{}]*"file"\s*:\s*"([^"]+)"[^{}]*\}""")
            trackObjRegex.findAll(block).forEach { match ->
                val objStr = match.value
                val fileRaw = match.groupValues[1].replace("\\/", "/").replace("\\", "").trim().trim('"', '\'')
                if (fileRaw.isBlank() || seenFiles.contains(fileRaw)) return@forEach
                seenFiles.add(fileRaw)

                val labelRaw = Regex(""""label"\s*:\s*"([^"]+)"""").find(objStr)?.groupValues?.get(1)
                val langRaw = Regex(""""language"\s*:\s*"([^"]+)"""").find(objStr)?.groupValues?.get(1)
                val kindRaw = Regex(""""kind"\s*:\s*"([^"]+)"""").find(objStr)?.groupValues?.get(1)

                if (kindRaw != null && kindRaw != "captions" && kindRaw != "subtitles") {
                    return@forEach
                }

                val finalUrl = fixUrl(fileRaw)
                val label = unescapeUnicode(labelRaw ?: langRaw ?: "Türkçe")
                subtitleCallback.invoke(newSubtitleFile(label, finalUrl))
            }
        }
    }
}
