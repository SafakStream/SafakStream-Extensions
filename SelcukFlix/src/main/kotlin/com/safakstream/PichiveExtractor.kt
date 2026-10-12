package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

/**
 * pichive.online iframe'ini m3u8'e çeviren extractor.
 *
 * Akış:
 *  1) iframe.php?v=TOKEN sayfasını çek (Referer: selcukflix.app ŞART!)
 *  2) Sayfada openPlayer('LONG_TOKEN', ...) çağrısını bul
 *  3) /source2.php?v=LONG_TOKEN'a istek at → JSON al
 *  4) JSON içinde playlist[].sources[].file → m.php → master.m3u8 olarak değiştir
 *  5) m3u8'i callback'e gönder
 */
class PichiveExtractor : ExtractorApi() {
    override var name = "PichiveExtractor"
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
        Log.d("PichiveExtractor", "getUrl: $url")

        // 1) iframe sayfasını çek — Cloudflare bypass için Referer = selcukflix.app
        val iframeHtml = try {
            app.get(
                url,
                headers = browserHeaders + mapOf("Referer" to "https://selcukflix.app/"),
                referer = "https://selcukflix.app/"
            ).text
        } catch (e: Exception) {
            Log.e("PichiveExtractor", "iframe fetch hatası: ${e.message}")
            ""
        }

        if (iframeHtml.isEmpty()) {
            Log.e("PichiveExtractor", "iframe HTML boş")
            return
        }

        Log.d("PichiveExtractor", "iframe HTML uzunluk: ${iframeHtml.length}")

        // 2) openPlayer('LONG_TOKEN') token'ını bul
        val tokenMatch = Regex("""openPlayer\('([^']+)'""").find(iframeHtml)
        if (tokenMatch == null) {
            Log.e("PichiveExtractor", "openPlayer token bulunamadı")
            return
        }
        val longToken = tokenMatch.groupValues[1]
        Log.d("PichiveExtractor", "token uzunluk: ${longToken.length}")

        // 3) /source2.php?v=LONG_TOKEN
        val domain = Regex("""https?://[^/]+""").find(url)?.value ?: "https://four.pichive.online"
        val sourceUrl = "$domain/source2.php?v=$longToken"
        Log.d("PichiveExtractor", "source2 URL: $sourceUrl")

        val jsonText = try {
            app.get(
                sourceUrl,
                headers = browserHeaders + mapOf(
                    "Referer" to url,
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                referer = url
            ).text
        } catch (e: Exception) {
            Log.e("PichiveExtractor", "source2 fetch hatası: ${e.message}")
            return
        }

        Log.d("PichiveExtractor", "source2 JSON uzunluk: ${jsonText.length}")

        // 4) JSON parse et
        try {
            val json = JSONObject(jsonText)
            if (!json.optBoolean("state", false)) {
                Log.e("PichiveExtractor", "state=false")
                return
            }

            val playlist = json.optJSONArray("playlist") ?: return
            for (i in 0 until playlist.length()) {
                val track = playlist.optJSONObject(i) ?: continue
                val trackTitle = track.optString("title", "Orijinal")
                val sources = track.optJSONArray("sources") ?: continue

                for (j in 0 until sources.length()) {
                    val src = sources.optJSONObject(j) ?: continue
                    val fileUrl = src.optString("file", "")
                    if (fileUrl.isEmpty()) continue

                    // m.php -> master.m3u8
                    val m3u8Url = if (fileUrl.contains("m.php")) {
                        fileUrl.replace("m.php", "master.m3u8")
                    } else if (fileUrl.contains(".php")) {
                        fileUrl.replace(Regex("""\.[a-z]+\.php"""), "master.m3u8")
                    } else {
                        fileUrl
                    }

                    val label = src.optString("label", "HD").uppercase()
                    Log.d("PichiveExtractor", "M3U8 bulundu [$trackTitle/$label]: ${m3u8Url.take(120)}")

                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "Pichive - $trackTitle ($label)",
                            url = m3u8Url,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = url
                            this.headers = browserHeaders + mapOf("Referer" to url)
                            this.quality = when (label.uppercase()) {
                                "4K" -> Qualities.P2160.value
                                "1080P", "FHD" -> Qualities.P1080.value
                                "720P", "HD" -> Qualities.P720.value
                                "480P" -> Qualities.P480.value
                                "360P" -> Qualities.P360.value
                                else -> Qualities.Unknown.value
                            }
                        }
                    )
                }
            }

            // 5) Altyazılar (varsa)
            // JSON içinde subtitle/ttList olabilir — sonra ekleyeceğiz
        } catch (e: Exception) {
            Log.e("PichiveExtractor", "JSON parse hatası: ${e.message}")
        }
    }
}
