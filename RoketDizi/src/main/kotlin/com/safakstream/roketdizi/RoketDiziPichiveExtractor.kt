package com.safakstream.roketdizi

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class RoketDiziPichiveExtractor : ExtractorApi() {
    override var name = "RoketDiziPichive"
    override var mainUrl = "https://pichive.online"
    override val requiresReferer = true

    private val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("RoketDiziPichive", "getUrl: $url")

        val iframeHtml = try {
            app.get(
                url,
                headers = browserHeaders + mapOf("Referer" to "https://roketdizi.life/"),
                referer = "https://roketdizi.life/"
            ).text
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "iframe fetch error: ${e.message}")
            ""
        }

        if (iframeHtml.isEmpty()) return
        Log.d("RoketDiziPichive", "iframe HTML length: ${iframeHtml.length}")

        // Extract subtitles
        try {
            val subtitleRegex = Regex("""\{"file":"([^"]+\.vtt[^"]*)"[^}]*"label":"([^"]*)"[^}]*(?:"lang":"([^"]*)")?[^}]*\}""")
            val seen = mutableSetOf<String>()
            subtitleRegex.findAll(iframeHtml).forEach { m ->
                var subUrl = m.groupValues[1].replace("\\/", "/").replace("\\", "")
                if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                if (subUrl.isBlank() || seen.contains(subUrl)) return@forEach
                seen.add(subUrl)
                val label = unescapeUnicode(m.groupValues[2]).ifBlank { "Altyazı" }
                try {
                    subtitleCallback.invoke(newSubtitleFile(label, subUrl) {
                        this.headers = browserHeaders + mapOf("Referer" to url)
                    })
                } catch (_: Exception) {}
            }
            Log.d("RoketDiziPichive", "Subtitles: ${seen.size}")
        } catch (_: Exception) {}

        val domain = Regex("""https?://[^/]+""").find(url)?.value ?: "https://four.pichive.online"

        // Try selcukflix style: openPlayer token -> source2.php
        val openPlayerToken = Regex("""openPlayer\('([^']+)'""").find(iframeHtml)?.groupValues?.get(1)
        if (openPlayerToken != null) {
            Log.d("RoketDiziPichive", "openPlayer token found")
            if (trySource2(domain, openPlayerToken, url, callback)) return
        }

        // Try NP_CONFIG style: master URL's v token -> source2.php
        val masterUrl = Regex(""""(?:master|file)"\s*:\s*"([^"]+)"""").find(iframeHtml)
            ?.groupValues?.get(1)?.replace("\\/", "/")
        if (masterUrl != null) {
            Log.d("RoketDiziPichive", "master URL: ${masterUrl.take(80)}")
            val vToken = Regex("""[?&]v=([^&"]+)""").find(masterUrl)?.groupValues?.get(1)
            if (vToken != null && trySource2(domain, vToken, url, callback)) return

            // Try master directly
            try {
                val masterResp = app.get(
                    masterUrl,
                    headers = browserHeaders + mapOf(
                        "Referer" to "https://roketdizi.life/",
                        "Origin" to "https://roketdizi.life"
                    ),
                    referer = "https://roketdizi.life/"
                ).text
                if (masterResp.contains("#EXTM3U")) {
                    callback.invoke(newExtractorLink(
                        source = name,
                        name = "RoketDizi Pichive",
                        url = masterUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.headers = browserHeaders + mapOf("Referer" to url)
                        this.quality = Qualities.P1080.value
                    })
                    return
                } else {
                    // Maybe it's the playlist JSON
                    if (masterResp.trimStart().startsWith("{")) {
                        if (parsePlaylistJson(masterResp, domain, url, callback)) return
                    }
                }
            } catch (e: Exception) {
                Log.e("RoketDiziPichive", "master fetch error: ${e.message}")
            }
        }

        Log.e("RoketDiziPichive", "No video sources found")
    }

    private suspend fun trySource2(
        domain: String,
        token: String,
        iframeUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val sourceUrl = "$domain/source2.php?v=$token"
            Log.d("RoketDiziPichive", "source2 URL: ${sourceUrl.take(120)}")
            val jsonText = app.get(
                sourceUrl,
                headers = browserHeaders + mapOf(
                    "Referer" to iframeUrl,
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                referer = iframeUrl
            ).text
            if (!jsonText.trimStart().startsWith("{")) {
                Log.d("RoketDiziPichive", "source2 not JSON: ${jsonText.take(80)}")
                return false
            }
            parsePlaylistJson(jsonText, domain, iframeUrl, callback)
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "source2 error: ${e.message}")
            false
        }
    }

    private suspend fun parsePlaylistJson(
        jsonText: String,
        domain: String,
        iframeUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val json = JSONObject(jsonText)
            if (!json.optBoolean("state", false)) return false
            val playlist = json.optJSONArray("playlist") ?: return false
            var found = false
            for (i in 0 until playlist.length()) {
                val track = playlist.optJSONObject(i) ?: continue
                val trackTitle = track.optString("title", "Orijinal")
                val sources = track.optJSONArray("sources") ?: continue
                for (j in 0 until sources.length()) {
                    val src = sources.optJSONObject(j) ?: continue
                    var fileUrl = src.optString("file", "")
                    if (fileUrl.isEmpty()) continue
                    if (fileUrl.startsWith("//")) fileUrl = "https:$fileUrl"
                    else if (fileUrl.startsWith("/")) fileUrl = "$domain$fileUrl"

                    val m3u8Url = when {
                        fileUrl.contains("m.php") -> fileUrl.replace("m.php", "master.m3u8")
                        fileUrl.contains(".php") -> fileUrl.replace(Regex("""\.[a-z]+\.php"""), "master.m3u8")
                        else -> fileUrl
                    }
                    val label = src.optString("label", "HD").uppercase()
                    Log.d("RoketDiziPichive", "Found: $m3u8Url")

                    callback.invoke(newExtractorLink(
                        source = name,
                        name = "RoketDizi - $trackTitle ($label)",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = iframeUrl
                        this.headers = browserHeaders + mapOf("Referer" to iframeUrl)
                        this.quality = when (label) {
                            "4K" -> Qualities.P2160.value
                            "1080P", "FHD" -> Qualities.P1080.value
                            "720P", "HD" -> Qualities.P720.value
                            "480P" -> Qualities.P480.value
                            "360P" -> Qualities.P360.value
                            else -> Qualities.Unknown.value
                        }
                    })
                    found = true
                }
            }
            found
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "parsePlaylist error: ${e.message}")
            false
        }
    }

    private fun unescapeUnicode(s: String): String {
        if (!s.contains("\\u")) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (i + 5 < s.length && s[i] == '\\' && s[i + 1] == 'u') {
                val hex = s.substring(i + 2, i + 6)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 6
                    continue
                }
            }
            sb.append(s[i])
            i++
        }
        return sb.toString()
    }
}
