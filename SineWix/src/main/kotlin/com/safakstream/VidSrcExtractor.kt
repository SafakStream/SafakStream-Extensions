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

        // Sayfadaki iframe'in src'sini bul
        val iframeSrc = Regex("""<iframe[^>]+src="([^"]+)"""").find(embedPage)?.groupValues?.get(1)
            ?: return

        // iframe'in içine git
        val iframePage = app.get(iframeSrc, referer = url).text

        // iframe içindeki video linkini bul (m3u8 veya mp4)
        val videoUrl = Regex(""""(https?://[^"]+\.(?:m3u8|mp4)[^"]*)"""").find(iframePage)?.groupValues?.get(1)
            ?: Regex("""'(https?://[^']+\.(?:m3u8|mp4)[^']*)'""").find(iframePage)?.groupValues?.get(1)
            ?: return

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = videoUrl,
                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = iframeSrc
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
