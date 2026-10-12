package com.safakstream.streamingcommunity

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

class VixCloudExtractor : ExtractorApi() {
    override val mainUrl: String = "vixcloud.co"
    override val name: String = "VixCloud"
    override val requiresReferer: Boolean = true
    val TAG: String = "VixCloudExtractor"

    private var referer: String? = null

    private val h: MutableMap<String, String> = mutableMapOf(
        "Accept" to "*/*",
        "Connection" to "keep-alive",
        "Cache-Control" to "no-cache",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0"
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        this.referer = referer
        Log.d(TAG, "REFERER: $referer  URL: $url")

        val playlistUrl = getPlaylistLink(url)
        Log.w(TAG, "FINAL URL: $playlistUrl")

        newExtractorLink(
            "VixCloud",
            "Streaming Community - VixCloud",
            playlistUrl,
            ExtractorLinkType.M3U8
        ) {
            this.referer = this@VixCloudExtractor.referer ?: ""
            this.quality = com.lagradost.cloudstream3.utils.Qualities.Unknown.value
        }.let(callback)
    }

    private suspend fun getPlaylistLink(url: String): String {
        Log.d(TAG, "Item url: $url")
        val script = getScript(url)
        val masterPlaylist = script.getJSONObject("masterPlaylist")
        val masterPlaylistParams = masterPlaylist.getJSONObject("params")
        val token = masterPlaylistParams.getString("token")
        val expires = masterPlaylistParams.getString("expires")
        val playlistUrl = masterPlaylist.getString("url")

        val params = "token=$token&expires=$expires"
        var masterPlaylistUrl = if (playlistUrl.contains("?b")) {
            playlistUrl.replace("?b:1", "?b=1") + "&" + params
        } else {
            "$playlistUrl?$params"
        }

        Log.d(TAG, "masterPlaylistUrl: $masterPlaylistUrl")

        if (script.getBoolean("canPlayFHD")) {
            masterPlaylistUrl += "&h=1"
        }
        Log.d(TAG, "Master Playlist URL: $masterPlaylistUrl")
        return masterPlaylistUrl
    }

    private suspend fun getScript(url: String): JSONObject {
        Log.d(TAG, "Item url: $url")
        val document = app.get(
            url,
            headers = h,
            interceptor = CloudflareKiller()
        ).document

        Log.d(TAG, document.toString())

        val script = document.select("script")
            .firstOrNull { it.data().contains("masterPlaylist") }
            ?.data()
            ?.replace("\n", "")
            ?.replace("\t", "")
            ?: throw Exception("masterPlaylist script not found")

        val scriptJson = getSanitisedScript(script)
        Log.d(TAG, "Script Json: $scriptJson")
        return JSONObject(scriptJson)
    }

    private fun getSanitisedScript(script: String): String {
        val regex = Regex("window\\.(\\w+)\\s*=")
        val parts = regex.split(script, limit = 2).drop(1)
        val keys = regex.findAll(script).map { it.groupValues[1] }.toList()

        val jsonObjects = keys.zip(parts).map { (key, value) ->
            val cleaned = value
                .replace(";", "")
                .replace(Regex("(\\{|\\[|,)\\s*(\\w+)\\s*:"), "$1 \"$2\":")
                .replace(Regex(",(\\s*[}\\]])"), "$1")
                .trim()
            "\"$key\": $cleaned"
        }

        val finalObject = "{\n" + jsonObjects.joinToString(",\n") + "\n}"
        return finalObject.replace("'", "\"")
    }
}
