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

        // 1) NP_CONFIG içindeki altyazıları çek (asıl yöntem)
        val npConfig = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(iframeHtml)?.groupValues?.get(1)

        if (npConfig != null) {
            parseNpConfigSubtitles(npConfig, url, subtitleCallback)
        }

        val domain = Regex("""https?://[^/]+""").find(url)?.value ?: "https://four.pichive.online"

        if (processIframe(iframeHtml, domain, url, callback)) {
            Log.d("RoketDiziPichive", "SUCCESS - video found")
            return
        }
        Log.e("RoketDiziPichive", "No video sources found for $url")
    }

    private suspend fun parseNpConfigSubtitles(npConfig: String, iframeUrl: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            val json = JSONObject(npConfig)
            val subtitles = json.optJSONArray("subtitles") ?: run {
                Log.d("RoketDiziPichive", "NP_CONFIG'de subtitles yok")
                return
            }
            Log.d("RoketDiziPichive", "=== ${subtitles.length()} altyazı bulundu ===")
            for (i in 0 until subtitles.length()) {
                val sub = subtitles.optJSONObject(i) ?: continue
                val label = sub.optString("label", "Altyazı").ifBlank { sub.optString("lang", "Bilinmeyen") }
                var subUrl = sub.optString("url", "")
                if (subUrl.isEmpty()) {
                    // Bazen url yerine tok var, ondan oluştur
                    val tok = sub.optString("tok", "")
                    if (tok.isNotEmpty()) {
                        val dom = Regex("""https?://[^/]+""").find(iframeUrl)?.value ?: "https://four.pichive.online"
                        subUrl = "$dom/nsub.php?v=$tok"
                    }
                }
                if (subUrl.isEmpty()) continue
                if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                else if (subUrl.startsWith("/")) {
                    val dom = Regex("""https?://[^/]+""").find(iframeUrl)?.value ?: "https://four.pichive.online"
                    subUrl = "$dom$subUrl"
                }

                Log.d("RoketDiziPichive", "Subtitle: $label -> ${subUrl.take(80)}...")
                try {
                    subtitleCallback.invoke(newSubtitleFile(label, subUrl) {
                        this.headers = browserHeaders + mapOf("Referer" to iframeUrl)
                    })
                } catch (e: Exception) {
                    Log.e("RoketDiziPichive", "Subtitle callback error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("RoketDiziPichive", "parseNpConfigSubtitles error: ${e.message}")
        }
    }

    private suspend fun processIframe(
        iframeHtml: String,
        domain: String,
        iframeUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val npConfig = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(iframeHtml)?.groupValues?.get(1)

        if (npConfig != null) {
            Log.d("RoketDiziPichive", "NP_CONFIG found len=${npConfig.length}")
            val master = Regex(""""master"\s*:\s*"([^"]+)"""").find(npConfig)?.groupValues?.get(1)?.replace("\\/", "/")
            val hosts = Regex(""""hosts"\s*:\s*\[([^\]]+)\]""").find(npConfig)?.groupValues?.get(1)
                ?.split(",")?.map { it.trim().trim('"').replace("\\/", "/") }?.filter { it.isNotBlank() } ?: emptyList()

            if (master != null) {
                val vToken = Regex("""[?&]v=([^&"]+)""").find(master)?.groupValues?.get(1)
                if (vToken != null) {
                    val candidates = mutableListOf<String>()
                    // Önce master URL'i direkt dene (en kısa yol)
                    candidates.add(master)
                    // Sonra diğer host'lar
                    for (host in hosts) {
                        val h = host.trimEnd('/')
                        candidates.add("$h/nm.php?v=$vToken")
                    }
                    candidates.add("$domain/nm.php?v=$vToken")

                    for (testUrl in candidates) {
                        try {
                            Log.d("RoketDiziPichive", "TRY: ${testUrl.take(120)}")
                            val resp = app.get(testUrl, headers = browserHeaders, referer = iframeUrl).text
                            Log.d("RoketDiziPichive", "  OK len=${resp.length}")

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
        return found
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
}
