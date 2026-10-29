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
        Log.d("RoketDiziPichive", "=== getUrl: $url ===")

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

        if (iframeHtml.isEmpty()) {
            Log.e("RoketDiziPichive", "iframe empty")
            return
        }
        Log.d("RoketDiziPichive", "HTML length=${iframeHtml.length}")
        Log.d("RoketDiziPichive", "HTML first 600: ${iframeHtml.take(600)}")

        // Subtitles
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

        if (processIframe(iframeHtml, domain, url, callback)) {
            Log.d("RoketDiziPichive", "SUCCESS - video found")
            return
        }
        Log.e("RoketDiziPichive", "No video sources found for $url")
    }

    private suspend fun processIframe(
        iframeHtml: String,
        domain: String,
        iframeUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 1) NP_CONFIG with hosts
        val npConfig = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(iframeHtml)?.groupValues?.get(1)

        if (npConfig != null) {
            Log.d("RoketDiziPichive", "NP_CONFIG found len=${npConfig.length}")
            val master = Regex(""""master"\s*:\s*"([^"]+)"""").find(npConfig)?.groupValues?.get(1)?.replace("\\/", "/")
            val hosts = Regex(""""hosts"\s*:\s*\[([^\]]+)\]""").find(npConfig)?.groupValues?.get(1)
                ?.split(",")?.map { it.trim().trim('"').replace("\\/", "/") }?.filter { it.isNotBlank() } ?: emptyList()
            Log.d("RoketDiziPichive", "master=${master?.take(120)}")
            Log.d("RoketDiziPichive", "hosts count=${hosts.size}, first=${hosts.firstOrNull()}")

            if (master != null) {
                val vToken = Regex("""[?&]v=([^&"]+)""").find(master)?.groupValues?.get(1)
                Log.d("RoketDiziPichive", "vToken len=${vToken?.length}, first=${vToken?.take(30)}")

                if (vToken != null) {
                    // Try on each host
                    val candidates = mutableListOf<String>()
                    for (host in hosts) {
                        val h = host.trimEnd('/')
                        candidates.add("$h/nm.php?v=$vToken")
                        candidates.add("$h/source2.php?v=$vToken")
                        candidates.add("$h/$vToken.m3u8")
                        candidates.add("$h/master.m3u8")
                        candidates.add("$h/playlist.m3u8")
                    }
                    // Also direct
                    candidates.add("$domain/nm.php?v=$vToken")
                    candidates.add("$domain/source2.php?v=$vToken")

                    for (testUrl in candidates) {
                        try {
                            Log.d("RoketDiziPichive", "TRY: ${testUrl.take(150)}")
                            val resp = app.get(testUrl, headers = browserHeaders, referer = iframeUrl).text
                            val short = resp.take(150).replace("\n", " ")
                            Log.d("RoketDiziPichive", "  OK len=${resp.length}: $short")

                            if (resp.contains("#EXTM3U")) {
                                callback.invoke(newExtractorLink(
                                    source = name, name = "RoketDizi Pichive",
                                    url = testUrl, type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = iframeUrl
                                    this.headers = browserHeaders
                                    this.quality = Qualities.P1080.value
                                })
                                return true
                            }
                            if (resp.trimStart().startsWith("{") || resp.trimStart().startsWith("[")) {
                                if (parsePlaylistJson(resp, domain, iframeUrl, callback)) return true
                            }
                        } catch (e: Exception) {
                            Log.d("RoketDiziPichive", "  ERR: ${e.message?.take(80)}")
                        }
                    }
                }
            }
        } else {
            Log.d("RoketDiziPichive", "No NP_CONFIG - trying other methods")
        }

        // 2) Direct m3u8/mp4 in HTML
        val directVideoRegex = Regex("""(https?:)?//[^"'\s\\<>]+\.(m3u8|mp4)(\?[^"'\s\\<>]*)?""")
        var found = false
        directVideoRegex.findAll(iframeHtml).forEach { m ->
            var u = m.groupValues[0].replace("\\/", "/").replace("&amp;", "&")
            if (u.startsWith("//")) u = "https:$u"
            Log.d("RoketDiziPichive", "Direct video: $u")
            callback.invoke(newExtractorLink(
                source = name, name = "RoketDizi Pichive",
                url = u,
                type = if (u.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = iframeUrl
                this.headers = browserHeaders
                this.quality = Qualities.P1080.value
            })
            found = true
        }
        if (found) return true

        // 3) JSON-style keys
        val jsonFileRegex = Regex(""""(?:file|source|src|url|playlist|stream)"\s*:\s*"(https?://[^"]+\.(?:m3u8|mp4)[^"]*)"""")
        jsonFileRegex.findAll(iframeHtml).forEach { m ->
            var u = m.groupValues[1].replace("\\/", "/")
            Log.d("RoketDiziPichive", "JSON video: $u")
            callback.invoke(newExtractorLink(
                source = name, name = "RoketDizi Pichive",
                url = u,
                type = if (u.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = iframeUrl
                this.headers = browserHeaders
                this.quality = Qualities.P1080.value
            })
            found = true
        }
        if (found) return true

        // 4) openPlayer
        val openPlayerToken = Regex("""openPlayer\(['"]([^'"]+)['"]""").find(iframeHtml)?.groupValues?.get(1)
        if (openPlayerToken != null) {
            Log.d("RoketDiziPichive", "openPlayer token len=${openPlayerToken.length}")
            if (trySource2(domain, openPlayerToken, iframeUrl, callback)) return true
        }

        return false
    }

    private suspend fun trySource2(
        domain: String,
        token: String,
        iframeUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val sourceUrl = "$domain/source2.php?v=$token"
            Log.d("RoketDiziPichive", "source2: ${sourceUrl.take(120)}")
            val jsonText = app.get(
                sourceUrl,
                headers = browserHeaders + mapOf(
                    "Referer" to iframeUrl,
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                referer = iframeUrl
            ).text
            if (!jsonText.trimStart().startsWith("{")) return false
            parsePlaylistJson(jsonText, domain, iframeUrl, callback)
        } catch (_: Exception) {
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
            if (!json.optBoolean("state", false)) {
                Log.d("RoketDiziPichive", "Playlist state=false: ${jsonText.take(100)}")
                return false
            }
            val playlist = json.optJSONArray("playlist") ?: return false
            var any = false
            for (i in 0 until playlist.length()) {
                val track = playlist.optJSONObject(i) ?: continue
                val title = track.optString("title", "Orijinal")
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
                    Log.d("RoketDiziPichive", "Playlist -> $m3u8Url")
                    callback.invoke(newExtractorLink(
                        source = name, name = "RoketDizi - $title ($label)",
                        url = m3u8Url, type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = iframeUrl
                        this.headers = browserHeaders
                        this.quality = when (label) {
                            "4K" -> Qualities.P2160.value
                            "1080P", "FHD" -> Qualities.P1080.value
                            "720P", "HD" -> Qualities.P720.value
                            "480P" -> Qualities.P480.value
                            "360P" -> Qualities.P360.value
                            else -> Qualities.Unknown.value
                        }
                    })
                    any = true
                }
            }
            any
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
