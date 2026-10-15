package com.safakstream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class StreamedExtractor : ExtractorApi() {
    override val name = "Streamed"
    override val mainUrl = "https://embed.st"
    override val requiresReferer = true

    private val TAG = "StreamedExtractor"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "getUrl cagrildi: $url")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Referer" to "https://embed.st/",
            "Origin" to "https://embed.st"
        )

        // Genisletilmis regex: m3u8, txt, playlist, hls, manifest
        val m3u8Pattern = Regex("""\.m3u8|master\.txt|playlist|/hls/|manifest|\.m3u""")

        val resolver = WebViewResolver(
            interceptUrl = m3u8Pattern,
            additionalUrls = listOf(m3u8Pattern),
            useOkhttp = false,
            timeout = 30_000L
        )

        val response = try {
            app.get(
                url,
                referer = "https://embed.st/",
                headers = headers,
                interceptor = resolver
            )
        } catch (e: Exception) {
            Log.e(TAG, "WebView hata: ${e.message}")
            return
        }

        val m3u8Url = response.url
        Log.d(TAG, "Yakalanan URL: $m3u8Url")

        if (!m3u8Url.contains(".m3u8") && !m3u8Url.contains(".txt") && !m3u8Url.contains("hls")) {
            Log.e(TAG, "m3u8 yakalanamadi. Gelen: $m3u8Url")
            return
        }

        try {
            M3u8Helper.generateM3u8(
                source = this.name,
                streamUrl = m3u8Url,
                referer = url,
                headers = headers
            ).forEach(callback)
            Log.d(TAG, "M3u8 generate basarili")
        } catch (e: Exception) {
            Log.e(TAG, "M3u8 generate hata: ${e.message}")
        }
    }
}
