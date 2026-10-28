package com.safakstream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class VidSrcExtractor : ExtractorApi() {
    override val name = "VidSrc"
    override val mainUrl = "https://vidsrc.sh"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // vidsrc.sh embed sayfasını çek
        val embedPage = app.get(url).text

        // 1. Yöntem: Doğrudan iframe src'sini bul ve içine gir
        val iframeSrc = Regex("""<iframe[^>]+src="([^"]+)"""").find(embedPage)?.groupValues?.get(1)
        
        if (iframeSrc != null) {
            val iframeUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
            val iframePage = app.get(iframeUrl, referer = url).text

            // 2. Yöntem: iframe içindeki m3u8/mp4 linkini bul
            val videoUrl = Regex(""""(https?://[^"]+\.(?:m3u8|mp4)[^"]*)"""").find(iframePage)?.groupValues?.get(1)
                ?: Regex("""'(https?://[^']+\.(?:m3u8|mp4)[^']*)'""").find(iframePage)?.groupValues?.get(1)
                ?: Regex("""sources:\s*\[\s*\{\s*file:\s*["']([^"']+)["']""").find(iframePage)?.groupValues?.get(1)

            if (videoUrl != null) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = iframeUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                return
            }
        }

        // 3. Yöntem: Eğer iframe bulunamazsa, sayfa içindeki gizli scriptleri tara
        val scriptVideoUrl = Regex("""file:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(embedPage)?.groupValues?.get(1)
        
        if (scriptVideoUrl != null) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = scriptVideoUrl,
                    type = if (scriptVideoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}
