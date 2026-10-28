package com.safakstream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class VidLinkExtractor : ExtractorApi() {
    override val name = "VidLink"
    override val mainUrl = "https://vidlink.pro"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Sayfa kaynağını çek
            val page = app.get(url, referer = mainUrl).text
            
            // HTML içinden m3u8 veya mp4 linkini regex ile bul
            val videoUrl = Regex(""""(https?://[^"]+\.(?:m3u8|mp4)[^"]*)"""").find(page)?.groupValues?.get(1)
                ?: Regex("""'(https?://[^']+\.(?:m3u8|mp4)[^']*)'""").find(page)?.groupValues?.get(1)
                ?: Regex("""file:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(page)?.groupValues?.get(1)
                ?: Regex("""source:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(page)?.groupValues?.get(1)

            if (videoUrl != null) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            // Hata durumunda sessizce çık
        }
    }
}
