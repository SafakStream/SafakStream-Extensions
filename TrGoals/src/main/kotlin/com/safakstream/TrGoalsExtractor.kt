// SafakStream-Extensions/TrGoals/src/main/kotlin/com/safakstream/TrGoalsExtractor.kt

package com.safakstream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * TrGoals VIP TV extractor
 * Cloudflare korumalı .js URL'lerini WebView ile açar ve m3u8 yakalar.
 */
class TrGoalsExtractor : ExtractorApi() {
    override val name = "TrGoals"
    override val mainUrl = "https://www.trgoals183.top"
    override val requiresReferer = true

    private val TAG = "TrGoalsExtractor"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        Log.d(TAG, "getUrl cagrildi: $url")

        val headers = mapOf(
            "User-Agent" to TrGoals.USER_AGENT,
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl,
        )

        // HLS/m3u8 pattern'lerini yakala
        val m3u8Pattern = Regex("""\.m3u8|master\.txt|playlist|/hls/|manifest|\.m3u|index\.m3u8""")

        val resolver = WebViewResolver(
            interceptUrl = m3u8Pattern,
            additionalUrls = listOf(m3u8Pattern),
            useOkhttp = false,
            timeout = 30_000L,
        )

        val response = try {
            app.get(
                url,
                referer = "$mainUrl/",
                headers = headers,
                interceptor = resolver,
            )
        } catch (e: Exception) {
            Log.e(TAG, "WebView hata: ${e.message}")
            return
        }

        val m3u8Url = response.url
        Log.d(TAG, "Yakalanan URL: $m3u8Url")

        // Yakalanan URL .m3u8 içeriyorsa M3u8Helper ile parçala
        if (m3u8Url.contains(".m3u8") || m3u8Url.contains("master.txt") ||
            m3u8Url.contains("/hls/") || m3u8Url.contains("manifest")
        ) {
            try {
                M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = m3u8Url,
                    referer = "$mainUrl/",
                    headers = headers,
                ).forEach(callback)
                Log.d(TAG, "M3u8 generate basarili")
            } catch (e: Exception) {
                Log.e(TAG, "M3u8 generate hata: ${e.message}")
                // Fallback: doğrudan link olarak ver
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8,
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                        this.headers = headers
                    }
                )
            }
        } else {
            // m3u8 değilse yine de link ver (JW Player farklı formatta olabilir)
            Log.w(TAG, "m3u8 yakalanamadi, fallback: $m3u8Url")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8,
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
        }
    }
}
