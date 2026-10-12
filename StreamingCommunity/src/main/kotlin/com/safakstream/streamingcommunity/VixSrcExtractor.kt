package com.safakstream.streamingcommunity

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

class VixSrcExtractor : ExtractorApi() {
    override val mainUrl: String = "vixsrc.to"
    override val name: String = "VixCloud"
    override val requiresReferer: Boolean = true
    val TAG: String = "VixSrcExtractor"

    private var referer: String? = null

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
            "VixSrc",
            "Streaming Community - VixSrc",
            playlistUrl,
            ExtractorLinkType.M3U8
        ) {
            this.referer = referer ?: ""
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

        val host = url.toHttpUrlOrNull()?.host() ?: ""
        val headers = mutableMapOf(
            "Accept" to "*/*",
            "Alt-Used" to host,
            "Connection" to "keep-alive",
            "Host" to host,
            "Referer" to (referer ?: ""),
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/133.0"
        )

        val response = app.get(url, headers = headers).document
        val script = response.select("script")
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
