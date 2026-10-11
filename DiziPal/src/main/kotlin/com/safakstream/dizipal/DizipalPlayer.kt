package com.safakstream.dizipal

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
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
        try {
            val response = app.get(url, referer = referer).text

            val openPlayerRegex = Regex("""window\.openPlayer\s*\(\s*['"]([^'"]+)['"]""")
            val subUrls = mutableSetOf<String>()
            val fileRegex = Regex(""""file":"((?:\\\"|[^"])+)","label":"((?:\\\"|[^"])+)"""")

            fileRegex.findAll(response).forEach { matchResult ->
                val subUrlExt = matchResult.groupValues[1]
                val subLangExt = matchResult.groupValues[2]

                val subUrl = subUrlExt.replace("\\/", "/").replace("\\u0026", "&").replace("\\", "")
                val subLang = subLangExt
                    .replace("\\u0131", "ı").replace("\\u0130", "İ")
                    .replace("\\u00fc", "ü").replace("\\u00e7", "ç")
                    .replace("\\u011f", "ğ").replace("\\u015f", "ş")

                if (subUrls.add(subUrl)) {
                    subtitleCallback.invoke(
                        SubtitleFile(subLang, fixUrl(subUrl))
                    )
                }
            }

            val playlistId = openPlayerRegex.find(response)?.groupValues?.get(1) ?: return
            Log.d("DiziPal", "playlistId: $playlistId")

            val domainRegex = Regex("https?://[^/]+")
            val domain = domainRegex.find(url)?.value ?: "https://dplayer82.site"

            val apiUrl = "$domain/source2.php?v=$playlistId"
            Log.d("DiziPal", "apiUrl: $apiUrl")

            val apiResponse = app.get(apiUrl, referer = url).text

            val fileRegex2 = Regex(""""file"\s*:\s*"([^"]+)"""")
            fileRegex2.findAll(apiResponse).forEach { matchResult ->
                var fileUrl = matchResult.groupValues[1].replace("\\/", "/")

                fileUrl = when {
                    fileUrl.startsWith("//") -> "https:$fileUrl"
                    !fileUrl.startsWith("http") -> "https://$fileUrl"
                    else -> fileUrl
                }

                if (fileUrl.contains("m.php")) {
                    fileUrl = fileUrl.replace("m.php", "master.m3u8")
                }

                Log.d("DiziPal", "fileUrl: $fileUrl")

                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "DPlayer (Auto)",
                        url = fileUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.quality = 0
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("DiziPal", "DPlayer Extractor Hata: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun fixUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            else -> "https://$url"
        }
    }
}
