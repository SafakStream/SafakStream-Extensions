package com.safakstream.dizipal

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class DizipalPlayer : ExtractorApi() {
    override var name = "DizipalPlayer"
    override var mainUrl = "four.dplayer82.site"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            Log.d("DiziPal", "DizipalPlayer.getUrl çağrıldı: $url")

            // 1) iframe.php sayfasını çek
            val iframeResponse = app.get(url, referer = referer).text

            // 2) window.openPlayer('PLAYLIST_B64', ...) ara
            val openPlayerRegex = Regex("""window\.openPlayer\(\s*['"]([^'"]+)['"]""")
            val playlistB64 = openPlayerRegex.find(iframeResponse)?.groupValues?.get(1)
            if (playlistB64 == null) {
                Log.e("DiziPal", "openPlayer playlist bulunamadı")
                return
            }
            Log.d("DiziPal", "playlistB64 bulundu (ilk 80): ${playlistB64.take(80)}")

            // 3) Domain tespit et (iframe URL'inden)
            val domainRegex = Regex("https?://[^/]+")
            val domain = domainRegex.find(url)?.value ?: "https://four.dplayer82.site"

            // 4) source2.php çağır
            val apiUrl = "$domain/source2.php?v=$playlistB64"
            val apiResponse = app.get(
                apiUrl,
                referer = url,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01"
                )
            ).text
            Log.d("DiziPal", "source2 response (ilk 200): ${apiResponse.take(200)}")

            // 5) JSON parse
            val json = JSONObject(apiResponse)
            if (!json.optBoolean("state", false)) {
                Log.e("DiziPal", "source2 state=false veya expired")
                return
            }

            val playlist = json.optJSONArray("playlist") ?: return
            if (playlist.length() == 0) {
                Log.e("DiziPal", "playlist boş")
                return
            }

            val firstItem = playlist.getJSONObject(0)
            val sources = firstItem.optJSONArray("sources") ?: return

            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                val file = src.optString("file")
                val title = src.optString("title", "DizipalPlayer")
                val type = src.optString("type", "hls")

                if (file.isNullOrEmpty()) continue

                Log.d("DiziPal", "video kaynağı[$i] ($title): $file")

                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "DPlayer ($title)",
                        url = file,
                        type = if (type == "hls") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$domain/"
                        this.quality = 0
                    }
                )
            }

            // 6) Altyazıları da çek (m3u8 EXT-X-MEDIA'dan)
            // m.php içeriğini çekip altyazı URI'lerini bulalım
            try {
                val firstSourceUrl = sources.getJSONObject(0).optString("file")
                if (!firstSourceUrl.isNullOrEmpty()) {
                    val m3u8Content = app.get(firstSourceUrl, referer = "$domain/").text
                    val subtitleRegex = Regex("""#EXT-X-MEDIA:[^\n]*NAME="([^"]+)"[^\n]*URI="([^"]+)"""")
                    subtitleRegex.findAll(m3u8Content).forEach { match ->
                        val subName = match.groupValues[1]
                        var subUrl = match.groupValues[2]
                        if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                        else if (!subUrl.startsWith("http")) subUrl = "$domain/$subUrl"
                        Log.d("DiziPal", "altyazı: $subName -> $subUrl")
                        subtitleCallback.invoke(SubtitleFile(subName, subUrl))
                    }
                }
            } catch (e: Exception) {
                Log.e("DiziPal", "Altyazı parse hatası: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("DiziPal", "DizipalPlayer Hata: ${e.message}")
            e.printStackTrace()
        }
    }
}
