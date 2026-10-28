package com.safakstream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.Base64

open class VidSrcExtractor : ExtractorApi() {
    override val name = "VidSrc"
    override val mainUrl = "https://vidsrc.sh"
    private val apiUrl = "https://vidsrc.stream"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframedoc = app.get(url).document

        val srcrcpList = iframedoc.select("div.serversList > div.server").mapNotNull {
            val datahash = it.attr("data-hash") ?: return@mapNotNull null
            val rcpLink = "$apiUrl/rcp/$datahash"
            val rcpRes = app.get(rcpLink, referer = apiUrl).text
            val srcrcpLink = Regex("src:\\s*'(.*)',").find(rcpRes)?.destructured?.component1() ?: return@mapNotNull null
            "https:$srcrcpLink"
        }

        srcrcpList.amap { server ->
            val res = app.get(server, referer = apiUrl)
            if (res.url.contains("/prorcp")) {
                val encodedElement = res.document.select("div#reporting_content+div")
                val decodedUrl = decodeUrl(encodedElement.attr("id"), encodedElement.text()) ?: return@amap
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        decodedUrl,
                        apiUrl,
                        Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            } else {
                loadExtractor(res.url, url, subtitleCallback, callback)
            }
        }
    }

    private fun decodeUrl(encType: String, url: String): String? {
        return when (encType) {
            "NdonQLf1Tzyx7bMG" -> bMGyx71TzQLfdonN(url)
            "sXnL9MQIry" -> Iry9MQXnLs(url)
            "IhWrImMIGL" -> IGLImMhWrI(url)
            "xTyBxQyGTA" -> GTAxQyTyBx(url)
            "ux8qjPHC66" -> C66jPHx8qu(url)
            "eSfH1IRMyL" -> MyL1IRSfHe(url)
            "KJHidj7det" -> detdj7JHiK(url)
            "o2VSUnjnZl" -> nZlUnj2VSo(url)
            "Oi3v1dAlaM" -> laM1dAi3vO(url)
            "TsA2KGDGux" -> GuxKGDsA2T(url)
            "JoAHUMCLXV" -> LXVUMCoAHJ(url)
            else -> null
        }
    }

    private fun bMGyx71TzQLfdonN(a: String): String {
        val b = 3
        val c = mutableListOf<String>()
        var d = 0
        while (d < a.length) {
            c.add(a.substring(d, minOf(d + b, a.length)))
            d += b
        }
        return c.reversed().joinToString("")
    }

    private fun Iry9MQXnLs(a: String): String {
        val b = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val d = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return d.mapIndexed { index, c -> (c.code xor b[index % b.length].code).toChar() }.joinToString("")
    }

    private fun IGLImMhWrI(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return b.reversed()
    }

    private fun GTAxQyTyBx(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return b.map { (it.code - 5).toChar() }.joinToString("")
    }

    private fun C66jPHx8qu(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return String(Base64.getDecoder().decode(b))
    }

    private fun MyL1IRSfHe(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return b.map { (it.code + 5).toChar() }.joinToString("")
    }

    private fun detdj7JHiK(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return b.reversed()
    }

    private fun nZlUnj2VSo(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return b.map { (it.code xor 5).toChar() }.joinToString("")
    }

    private fun laM1dAi3vO(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return String(Base64.getDecoder().decode(b))
    }

    private fun GuxKGDsA2T(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return b.map { (it.code - 3).toChar() }.joinToString("")
    }

    private fun LXVUMCoAHJ(a: String): String {
        val b = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return b.map { (it.code + 3).toChar() }.joinToString("")
    }
}
