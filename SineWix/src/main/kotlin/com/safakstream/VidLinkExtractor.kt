package com.safakstream

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.fasterxml.jackson.annotation.JsonProperty

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
        // URL'den movie veya tv kısmını ve ID'yi alıyoruz
        val path = url.substringAfter("vidlink.pro/")
        val apiUrl = "https://vidlink.pro/api/b/$path"
        
        try {
            // VidLink'in kendi API'sine istek atıyoruz
            val res = app.get(apiUrl, referer = mainUrl).text
            val json = tryParseJson<VidLinkResponse>(res)
            val m3u8 = json?.stream?.playlist
            
            if (m3u8 != null) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
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

// API'den dönen JSON verisini karşılamak için veri sınıfları
data class VidLinkResponse(
    @JsonProperty("stream") val stream: VidLinkStream?
)

data class VidLinkStream(
    @JsonProperty("playlist") val playlist: String?
)
