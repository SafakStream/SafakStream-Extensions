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
            app.get(url, headers = browserHeaders + mapOf("Referer" to "https://roketdizi.life/"), referer = "https://roketdizi.life/").text
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "iframe fetch error: ${e.message}")
            ""
        }

        if (iframeHtml.isEmpty()) return
        Log.d("RoketDiziPichive", "HTML length=${iframeHtml.length}")
        Log.d("RoketDiziPichive", "HTML first 600: ${iframeHtml.take(600)}")

        // 1) NP_CONFIG içindeki altyazıları çek
        val npConfig = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(iframeHtml)?.groupValues?.get(1)
        
        if (npConfig != null) {
            parseNpConfigSubtitles(npConfig, url, subtitleCallback)
        }

        // 2) HTML içindeki VTT altyazıları (eski yöntem, yedek)
        parseVttInHtml(iframeHtml, url, subtitleCallback)

        val domain = Regex("""https?://[^/]+""").find(url)?.value ?: "https://four.pichive.online"

        if (processIframe(iframeHtml, domain, url, subtitleCallback, callback)) {
            Log.d("RoketDiziPichive", "SUCCESS - video found")
            return
        }
        Log.e("RoketDiziPichive", "No video sources found for $url")
    }

    private suspend fun parseNpConfigSubtitles(npConfig: String, iframeUrl: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            // NP_CONFIG içinde "subtitles" veya "tracks" alanı var mı?
            val subRegex = Regex(""""(?:subtitles|tracks)"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            val match = subRegex.find(npConfig) ?: return
            val subArray = match.groupValues[1]
            
            // JSON array içindeki objeleri bul: {"file":"...vtt","label":"...","lang":"..."}
            val itemRegex = Regex("""\{[^{}]*\}""")
            itemRegex.findAll(subArray).forEach { item ->
                val obj = item.value
                val fileMatch = Regex(""""(?:file|src|url)"\s*:\s*"([^"]+)"""").find(obj)
                val file = fileMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: return@forEach
                if (!file.contains(".vtt") && !file.contains(".srt") && !file.contains(".ass")) return@forEach
                
                var subUrl = file
                if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                else if (subUrl.startsWith("/")) subUrl = "https://four.pichive.online$subUrl"
                
                val labelMatch = Regex(""""(?:label|language|name)"\s*:\s*"([^"]+)"""").find(obj)
                val label = unescapeUnicode(labelMatch?.groupValues?.get(1) ?: "Altyazı")
                
                Log.d("RoketDiziPichive", "NP_CONFIG Subtitle: $label -> $subUrl")
                try {
                    subtitleCallback.invoke(newSubtitleFile(label, subUrl) {
                        this.headers = browserHeaders + mapOf("Referer" to iframeUrl)
                    })
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "parseNpConfigSubtitles error: ${e.message}")
        }
    }

    private suspend fun parseVttInHtml(html: String, iframeUrl: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            val seen = mutableSetOf<String>()
            
            // Genişletilmiş regex: tırnaklı, tırnaksız, tek tırnak destekli
            // Örn: {"file":"...vtt","label":"Türkçe"} veya {file:"...vtt",label:"Türkçe"} veya {file:'...vtt',label:'Türkçe'}
            val patterns = listOf(
                Regex("""["']?file["']?\s*:\s*["']([^"']+\.(?:vtt|srt|ass)[^"']*)["'][^}]{0,500}?["']?label["']?\s*:\s*["']([^"']*)["']"""),
                Regex("""["']?label["']?\s*:\s*["']([^"']*)["'][^}]{0,500}?["']?file["']?\s*:\s*["']([^"']+\.(?:vtt|srt|ass)[^"']*)["']"""),
                Regex("""["']?file["']?\s*:\s*["']([^"']+\.(?:vtt|srt|ass)[^"']*)["']""")
            )

            for ((idx, pat) in patterns.withIndex()) {
                pat.findAll(html).forEach { m ->
                    val (url, label) = if (idx == 1) {
                        m.groupValues[2] to m.groupValues[1]
                    } else if (idx == 2) {
                        m.groupValues[1] to "Altyazı"
                    } else {
                        m.groupValues[1] to m.groupValues[2]
                    }
                    
                    var subUrl = url.replace("\\/", "/").replace("\\", "")
                    if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                    else if (subUrl.startsWith("/")) subUrl = "https://four.pichive.online$subUrl"
                    if (subUrl.isBlank() || seen.contains(subUrl)) return@forEach
                    seen.add(subUrl)
                    val lbl = unescapeUnicode(label).ifBlank { "Altyazı" }
                    Log.d("RoketDiziPichive", "HTML Subtitle (p$idx): $lbl -> $subUrl")
                    try {
                        subtitleCallback.invoke(newSubtitleFile(lbl, subUrl) {
                            this.headers = browserHeaders + mapOf("Referer" to iframeUrl)
                        })
                    } catch (_: Exception) {}
                }
            }
            Log.d("RoketDiziPichive", "HTML Subtitles found: ${seen.size}")

            // Debug: altyazı bulunamadıysa, anahtar kelimelerin geçtiği yerleri logla
            if (seen.isEmpty()) {
                Log.d("RoketDiziPichive", "=== DEBUG HTML KEYWORDS (len=${html.length}) ===")
                val keywords = listOf(".vtt", ".srt", ".ass", "\"tracks\"", "tracks:", "\"subtitles\"", "subtitles:", "\"captions\"", "caption", "subtitle")
                for (kw in keywords) {
                    var idx = html.indexOf(kw)
                    var cnt = 0
                    while (idx >= 0 && cnt < 2) {
                        val start = maxOf(0, idx - 80)
                        val end = minOf(html.length, idx + 150)
                        val ctx = html.substring(start, end).replace("\n", " ")
                        Log.d("RoketDiziPichive", "DEBUG[$kw] @$idx: $ctx")
                        idx = html.indexOf(kw, idx + 1)
                        cnt++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "parseVttInHtml error: ${e.message}")
        }
    }

    private suspend fun parseM3u8Subtitles(m3u8Url: String, iframeUrl: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            val content = app.get(m3u8Url, headers = browserHeaders, referer = iframeUrl).text
            Log.d("RoketDiziPichive", "=== M3U8 FULL CONTENT (len=${content.length}) ===")
            Log.d("RoketDiziPichive", content)
            // #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Türkçe",LANGUAGE="tr",URI="https://..."
            val regex = Regex("""#EXT-X-MEDIA:TYPE=SUBTITLES[^\n]*NAME="([^"]+)"[^\n]*LANGUAGE="([^"]+)"[^\n]*URI="([^"]+)"""")
            var found = false
            regex.findAll(content).forEach { m ->
                var subUrl = m.groupValues[3]
                if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                else if (subUrl.startsWith("/")) {
                    val baseDomain = Regex("""https?://[^/]+""").find(m3u8Url)?.value ?: "https://four.pichive.online"
                    subUrl = "$baseDomain$subUrl"
                }
                Log.d("RoketDiziPichive", "M3U8 Subtitle: ${m.groupValues[1]} (${m.groupValues[2]}) -> $subUrl")
                try {
                    subtitleCallback.invoke(newSubtitleFile(m.groupValues[1], subUrl) {
                        this.headers = browserHeaders + mapOf("Referer" to iframeUrl)
                    })
                    found = true
                } catch (_: Exception) {}
            }
            if (!found) {
                Log.d("RoketDiziPichive", "M3U8'de SUBTITLES bulunamadı")
            }
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "parseM3u8Subtitles error: ${e.message}")
        }
    }

    private suspend fun processIframe(
        iframeHtml: String,
        domain: String,
        iframeUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val npConfig = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(iframeHtml)?.groupValues?.get(1)

        if (npConfig != null) {
            Log.d("RoketDiziPichive", "NP_CONFIG found len=${npConfig.length}")
            Log.d("RoketDiziPichive", "=== NP_CONFIG FULL ===")
            Log.d("RoketDiziPichive", npConfig)
            val master = Regex(""""master"\s*:\s*"([^"]+)"""").find(npConfig)?.groupValues?.get(1)?.replace("\\/", "/")
            val hosts = Regex(""""hosts"\s*:\s*\[([^\]]+)\]""").find(npConfig)?.groupValues?.get(1)
                ?.split(",")?.map { it.trim().trim('"').replace("\\/", "/") }?.filter { it.isNotBlank() } ?: emptyList()
            
            if (master != null) {
                val vToken = Regex("""[?&]v=([^&"]+)""").find(master)?.groupValues?.get(1)
                if (vToken != null) {
                    val candidates = mutableListOf<String>()
                    for (host in hosts) {
                        val h = host.trimEnd('/')
                        candidates.add("$h/nm.php?v=$vToken")
                        candidates.add("$h/source2.php?v=$vToken")
                        candidates.add("$h/master.m3u8")
                    }
                    candidates.add("$domain/nm.php?v=$vToken")
                    candidates.add("$domain/source2.php?v=$vToken")

                    for (testUrl in candidates) {
                        try {
                            Log.d("RoketDiziPichive", "TRY: ${testUrl.take(120)}")
                            val resp = app.get(testUrl, headers = browserHeaders, referer = iframeUrl).text
                            Log.d("RoketDiziPichive", "  OK len=${resp.length}: ${resp.take(80).replace("\n", " ")}")

                            if (resp.contains("#EXTM3U")) {
                                // M3U8 içindeki altyazıları çek
                                parseM3u8Subtitles(testUrl, iframeUrl, subtitleCallback)
                                
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
                                if (parsePlaylistJson(resp, domain, iframeUrl, subtitleCallback, callback)) return true
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // Direct m3u8/mp4 in HTML
        val directVideoRegex = Regex("""(https?:)?//[^"'\s\\<>]+\.(m3u8|mp4)(\?[^"'\s\\<>]*)?""")
        var found = false
        directVideoRegex.findAll(iframeHtml).forEach { m ->
            var u = m.groupValues[0].replace("\\/", "/").replace("&amp;", "&")
            if (u.startsWith("//")) u = "https:$u"
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

        // openPlayer token
        val openPlayerToken = Regex("""openPlayer\(['"]([^'"]+)['"]""").find(iframeHtml)?.groupValues?.get(1)
        if (openPlayerToken != null) {
            if (trySource2(domain, openPlayerToken, iframeUrl, subtitleCallback, callback)) return true
        }

        return false
    }

    private suspend fun trySource2(
        domain: String,
        token: String,
        iframeUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val sourceUrl = "$domain/source2.php?v=$token"
            val jsonText = app.get(sourceUrl, headers = browserHeaders + mapOf(
                "Referer" to iframeUrl, "X-Requested-With" to "XMLHttpRequest"
            ), referer = iframeUrl).text
            if (!jsonText.trimStart().startsWith("{")) return false
            parsePlaylistJson(jsonText, domain, iframeUrl, subtitleCallback, callback)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun parsePlaylistJson(
        jsonText: String,
        domain: String,
        iframeUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val json = JSONObject(jsonText)
            if (!json.optBoolean("state", false)) return false
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
                    
                    parseM3u8Subtitles(m3u8Url, iframeUrl, subtitleCallback)
                    
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
