package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * pichive.online iframe'ini m3u8'e çeviren extractor.
 *
 * Strateji:
 *  1) iframe.php?v=TOKEN sayfasını çek (Cloudflare olabilir)
 *  2) Sayfada playerjs'in file: parametresini ara
 *  3) Bulunamazsa /source2.php?v=TOKEN gibi endpoint'leri dene
 *  4) Son çare loadExtractor ile iframe'i doğrudan extractorlara ver
 */
class PichiveExtractor : ExtractorApi() {
    override var name = "PichiveExtractor"
    override var mainUrl = "https://pichive.online"
    override val requiresReferer = true

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
        "Referer" to "https://selcukflix.app/"
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("PichiveExtractor", "getUrl: $url")

        // 1) iframe sayfasını çek
        val html = try {
            app.get(url, headers = headers, referer = referer).text
        } catch (e: Exception) {
            Log.e("PichiveExtractor", "Fetch hatası: ${e.message}")
            ""
        }

        if (html.isNotEmpty()) {
            Log.d("PichiveExtractor", "HTML uzunluk: ${html.length}")

            // Cloudflare kontrolü
            if (html.contains("Sorry, you have been blocked") || html.contains("Just a moment")) {
                Log.w("PichiveExtractor", "Cloudflare engeli algılandı")
            } else {
                // playerjs file: parametresini ara
                val fileMatches = Regex(""""file"\s*:\s*"([^"]+)"""").findAll(html)
                fileMatches.forEach { m ->
                    var fileUrl = m.groupValues[1].replace("\\/", "/")
                    if (fileUrl.startsWith("//")) fileUrl = "https:$fileUrl"
                    if (fileUrl.contains(".m3u8") || fileUrl.contains("master")) {
                        Log.d("PichiveExtractor", "Bulundu: $fileUrl")
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "Pichive",
                                url = fileUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = url
                                this.headers = headers
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        return
                    }
                }

                // openPlayer id'sini ara → /source2.php?v=ID
                val openPlayer = Regex("""openPlayer\s*\(\s*['"]([^'"]+)['"]""").find(html)
                if (openPlayer != null) {
                    val token = openPlayer.groupValues[1]
                    val domain = Regex("""https?://[^/]+""").find(url)?.value ?: mainUrl
                    val sourceUrl = "$domain/source2.php?v=$token"
                    Log.d("PichiveExtractor", "openPlayer token: $token -> $sourceUrl")
                    try {
                        val resp = app.get(sourceUrl, headers = headers, referer = url).text
                        Regex(""""file"\s*:\s*"([^"]+)"""").findAll(resp).forEach { m ->
                            var fileUrl = m.groupValues[1].replace("\\/", "/")
                            if (fileUrl.startsWith("//")) fileUrl = "https:$fileUrl"
                            if (fileUrl.contains("m.php")) fileUrl = fileUrl.replace("m.php", "master.m3u8")
                            Log.d("PichiveExtractor", "source2 -> $fileUrl")
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "Pichive",
                                    url = fileUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = url
                                    this.headers = headers
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("PichiveExtractor", "source2 hatası: ${e.message}")
                    }
                }
            }
        }

        // 2) Son çare: Cloudstream'in dahili extractorlarına ver
        try {
            loadExtractor(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            Log.e("PichiveExtractor", "loadExtractor hatası: ${e.message}")
        }
    }
}
