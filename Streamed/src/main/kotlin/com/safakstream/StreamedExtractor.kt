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
        Log.d(TAG, "getUrl: $url")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Referer" to "https://streamed.pk/",
            "Origin" to "https://streamed.pk"
        )

        val resolver = WebViewResolver(
            interceptUrl = Regex("\\.m3u8"),
            additionalUrls = listOf(Regex("\\.m3u8")),
            useOkhttp = false,
            timeout = 25_000L
        )

        val response = try {
            app.get(
                url,
                referer = "https://streamed.pk/",
                headers = headers,
                interceptor = resolver
            )
        } catch (e: Exception) {
            Log.e(TAG, "WebView hata: ${e.message}")
            return
        }

        val m3u8Url = response.url
        Log.d(TAG, "Yakalanan URL: $m3u8Url")

        if (!m3u8Url.contains(".m3u8")) {
            Log.e(TAG, "m3u8 yakalanamadi")
            return
        }

        M3u8Helper.generateM3u8(
            source = this.name,
            streamUrl = m3u8Url,
            referer = url,
            headers = headers
        ).forEach(callback)
    }
}
