package com.safakstream.dizipal

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class DizipalPlayer : ExtractorApi() {
    override var name = "DizipalPlayer"
    override var mainUrl = "dplayer82.site"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text
        val openPlayerRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
        val match = openPlayerRegex.find(response)
        val fileUrl = match?.groupValues?.get(1) ?: return

        val realUrl = if (fileUrl.startsWith("//")) "https:$fileUrl" else fileUrl

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = realUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "Referer" to (referer ?: ""),
                    "Origin" to (referer?.let { java.net.URI(it).scheme + "://" + java.net.URI(it).host } ?: "")
                )
            }
        )
    }
}
