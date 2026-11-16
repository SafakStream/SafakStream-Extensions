package com.safakstream

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.nicehttp.NiceResponse

open class CloseLoadExtractor : ExtractorApi() {
    override val name = "CloseLoad"
    override val mainUrl = "https://closeload.filmmakinesi.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        Log.d("SafakStream_${this.name}", "url » $url")
        val iSource = app.get(url, referer = extRef)
        val obfuscatedScript = iSource.document.select("script").find {
            it.data().contains("eval(function(p,a,c,k,e")
        }?.data()?.trim()
        getSubs(iSource, obfuscatedScript, subtitleCallback)
        getLinks(obfuscatedScript, callback)
    }

    private fun getSubs(iSource: NiceResponse, obfuscatedScript: String?, subtitleCallback: (SubtitleFile) -> Unit) {
        iSource.document.select("track").forEach {
            subtitleCallback.invoke(SubtitleFile(lang = it.attr("label"), url = mainUrl + it.attr("src")))
        }
        val track = obfuscatedScript?.substringAfter("tracks: ")?.substringBefore("]") + "]"
        if (track.startsWith("[") && track.endsWith("]")) {
            Log.d("SafakStream_${this.name}", "track -> $track")
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val tracks: List<SubSource> = objectMapper.readValue(track)
            Log.d("SafakStream_${this.name}", "tracks -> $tracks")
            tracks.forEach { it ->
                subtitleCallback.invoke(SubtitleFile(lang = it.label.toString(), url = mainUrl + it.file.toString()))
            }
        }
    }

    private suspend fun getLinks(obfuscatedScript: String?, callback: (ExtractorLink) -> Unit) {
        val rawScript = getAndUnpack(obfuscatedScript!!)
        val helloVarmi = rawScript.contains("dc_hello")
        var dcRegex = Regex("dc_hello\\(\"([^\"]*)\"\\)", setOf(RegexOption.IGNORE_CASE))
        if (!helloVarmi) {
            dcRegex = Regex("""dc_\w+\(\[(.*?)\]\)""", RegexOption.DOT_MATCHES_ALL)
        }
        val match = dcRegex.find(rawScript)
        val groupValues = match!!.groupValues[1]
        val lastUrl = if (helloVarmi) {
            Log.d("SafakStream_${this.name}", "groupValues » $groupValues")
            val dcUrl = dcHello(groupValues).substringAfter("http")
            Log.d("SafakStream_${this.name}", "dcUrl » $dcUrl")
            dcUrl
        } else {
            val parts = groupValues.split(",").map { it.trim().removeSurrounding("\"") }
            Log.d("SafakStream_${this.name}", "parts » $parts")
            val dcUrl = dcNew(parts)
            Log.d("SafakStream_${this.name}", "dcUrl » $dcUrl")
            dcUrl
        }
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = lastUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
                this.quality = Qualities.Unknown.value
            }
        )
    }

    fun dcHello(base64Input: String): String {
        val decodedOnce = String(Base64.decode(base64Input, Base64.DEFAULT))
        val reversedString = decodedOnce.reversed()
        val decodedTwice = String(Base64.decode(reversedString, Base64.DEFAULT))
        return if (decodedTwice.contains("+")) {
            decodedTwice.substringAfterLast("+")
        } else if (decodedTwice.contains(" ")) {
            decodedTwice.substringAfterLast(" ")
        } else if (decodedTwice.contains("|")) {
            decodedTwice.substringAfterLast("|")
        } else {
            decodedTwice
        }
    }

    fun dcNew(parts: List<String>): String {
        val value = parts.joinToString("")
        val decodedBytes = Base64.decode(value, Base64.DEFAULT)
        var result = String(decodedBytes, Charsets.ISO_8859_1).reversed()
        val rot13Applied = StringBuilder()
        for (c in result) {
            if (c in 'a'..'z') {
                val newChar = c + 13
                rot13Applied.append(if (newChar > 'z') newChar - 26 else newChar)
            } else if (c in 'A'..'Z') {
                val newChar = c + 13
                rot13Applied.append(if (newChar > 'Z') newChar - 26 else newChar)
            } else {
                rot13Applied.append(c)
            }
        }
        result = rot13Applied.toString()
        val unmix = StringBuilder()
        for ((i, char) in result.withIndex()) {
            var charCode = char.code
            charCode = (charCode - (399756995 % (i + 5)) + 256) % 256
            unmix.append(charCode.toChar())
        }
        return unmix.toString()
    }
}

private data class SubSource(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("kind") val kind: String? = null
)
