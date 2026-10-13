package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.safakstream.entities.*
import okhttp3.FormBody
import java.util.UUID

data class LoadData(val title: String, val id: String)

class NetMirrorAPI : MainAPI() {
    override var mainUrl = "https://net52.cc"
    override var name = "NetMirror"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private var cookieValue: String = ""

    private val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-IN,en-US;q=0.9,en;q=0.8",
        "Cache-Control" to "max-age=0",
        "Connection" to "keep-alive",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 5 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.132 Safari/537.36 /OS.Gatu v3.0",
        "X-Requested-With" to "XMLHttpRequest"
    )

    private val newTvBaseHeaders = mapOf(
        "Cache-Control" to "no-cache, no-store, must-revalidate",
        "Pragma" to "no-cache",
        "Expires" to "0",
        "X-Requested-With" to "NetmirrorNewTV v1.0",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",
        "Accept" to "application/json, text/plain, */*"
    )

    private val newTvDomains = listOf(
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3RzLmNvbQ==", "aHR0cHM6Ly9tb2JpbGVkZXRlY3QuYXBw", "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFydA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNj", "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNsaWNr", "aHR0cHM6Ly9tb2JpZGV0ZWN0Lmluaw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmxpdmU=", "aHR0cHM6Ly9tb2JpZGV0ZWN0LnBybw==", "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNob3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNpdGU=", "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNwYWNl", "aHR0cHM6Ly9tb2JpZGV0ZWN0LnN0b3Jl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnZpcA==", "aHR0cHM6Ly9tb2JpZGV0ZWN0Lndpa2k=", "aHR0cHM6Ly9tb2JpZGV0ZWN0Lnh5eg==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5hcnQ=", "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5jYw==", "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbmZv",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbms=", "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5saXZl", "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5wcm8=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5zdG9yZQ==", "aHR0cHM6Ly9tb2JpZGV0ZWN0cy50b3A=", "aHR0cHM6Ly9tb2JpZGV0ZWN0cy54eXo="
    )

    private suspend fun bypass(): String {
        if (cookieValue.isNotEmpty()) return cookieValue

        val saved = NetMirrorStorage.getCookie()
        if (saved.first != null && System.currentTimeMillis() - saved.second < 54000000) {
            cookieValue = saved.first!!
            return cookieValue
        }

        try {
            val reqHeaders = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Origin" to "https://net22.cc",
                "Referer" to "https://net22.cc/verify2",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
            )
            val body = FormBody.Builder().add("g-recaptcha-response", UUID.randomUUID().toString()).build()
            val response = app.post("https://net52.cc/verify.php", requestBody = body, headers = reqHeaders)
            val setCookie = response.headers["Set-Cookie"] ?: ""
            val match = Regex("t_hash_t=([^;]+)").find(setCookie)
            cookieValue = match?.groupValues?.get(1) ?: ""
            if (cookieValue.isNotEmpty()) NetMirrorStorage.saveCookie(cookieValue)
            return cookieValue
        } catch (e: Exception) {
            return ""
        }
    }

    private fun decodeBase64(value: String): String {
        return String(android.util.Base64.decode(value, android.util.Base64.DEFAULT))
    }

    private suspend fun resolveApiUrl(): String {
        val saved = NetMirrorStorage.getApiBase()
        if (saved.first != null && System.currentTimeMillis() - saved.second < 86400000) {
            return saved.first!!
        }
        for (domain in newTvDomains) {
            try {
                val base = decodeBase64(domain).trimEnd('/')
                val url = "$base/checknewtv.php"
                val response = app.get(url, headers = newTvBaseHeaders)
                if (response.code == 200) {
                    NetMirrorStorage.saveApiBase(base)
                    return base
                }
            } catch (e: Exception) { continue }
        }
        return ""
    }

    private suspend fun getNewTvUserToken(apiBase: String, ott: String): String {
        val saved = NetMirrorStorage.getUserToken(ott)
        if (saved.first != null && System.currentTimeMillis() - saved.second < 3600000) {
            return saved.first!!
        }
        try {
            val otpHeaders = mapOf(
                "accept" to "application/json, text/plain, */*",
                "cache-control" to "no-cache, no-store, must-revalidate",
                "Connection" to "Keep-Alive",
                "expires" to "0",
                "otp" to "111111",
                "pragma" to "no-cache",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.Gatu v1.0"
            )
            val response = app.get("$apiBase/newtv/otp.php", headers = otpHeaders)
            val otpResponse = tryParseJson<NewTvOtpResponse>(response.text)
            val token = otpResponse?.usertoken ?: ""
            if (token.isNotEmpty()) NetMirrorStorage.saveUserToken(ott, token)
            return token
        } catch (e: Exception) {
            return ""
        }
    }

    private fun buildNewTvHeaders(ott: String, extra: Map<String, String>): Map<String, String> {
        val map = newTvBaseHeaders.toMutableMap()
        map["Ott"] = ott
        map.putAll(extra)
        return map
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val cookie = bypass()
        val cookies = mapOf("t_hash_t" to cookie, "ott" to "nf", "hd" to "on")
        val response = app.get("$mainUrl/mobile/home?app=1", headers = headers, cookies = cookies)
        val document = response.document
        val trays = document.select(".tray-container, #top10")
        val lists = mutableListOf<HomePageList>()
        for (tray in trays) {
            val name = tray.select("h2, span").text()
            val items = tray.select("article, .top10-post").mapNotNull { el ->
                val id = el.selectFirst("a")?.attr("data-post") ?: el.attr("data-post")
                if (id.isNullOrEmpty()) return@mapNotNull null
                newMovieSearchResponse(name.ifEmpty { "NetMirror" }, id, TvType.Movie) {
                    this.posterUrl = "https://imgcdn.kim/poster/v/$id.jpg"
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
                }
            }
            if (items.isNotEmpty()) lists.add(HomePageList(name.ifEmpty { "NetMirror" }, items))
        }
        return newHomePageResponse(lists)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val cookie = bypass()
        val cookies = mapOf("t_hash_t" to cookie, "hd" to "on", "ott" to "nf")
        val url = "$mainUrl/mobile/search.php?s=$query&t=${System.currentTimeMillis() / 1000}"
        val response = app.get(url, headers = headers, cookies = cookies, referer = "$mainUrl/home")
        val data = tryParseJson<SearchData>(response.text) ?: return null
        return data.searchResult?.mapNotNull { result ->
            val id = result.id ?: return@mapNotNull null
            newMovieSearchResponse(result.t ?: "", id, TvType.Movie) {
                this.posterUrl = "https://imgcdn.kim/poster/v/$id.jpg"
                this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val cookie = bypass()
        val cookies = mapOf("t_hash_t" to cookie, "hd" to "on", "ott" to "nf")
        val id = tryParseJson<Map<String, String>>(url)?.get("id") ?: url
        val apiUrl = "$mainUrl/mobile/post.php?id=$id&t=${System.currentTimeMillis() / 1000}"
        val response = app.get(apiUrl, headers = headers, cookies = cookies, referer = "$mainUrl/home")
        val data = tryParseJson<PostData>(response.text) ?: return null

        val isSeries = !data.episodes.isNullOrEmpty()
        val type = if (isSeries) TvType.TvSeries else TvType.Movie
        val title = data.title ?: ""

        val episodeList = mutableListOf<Episode>()
        data.episodes?.forEach { ep ->
            val epId = ep.id ?: return@forEach
            episodeList.add(
                newEpisode(LoadData(title, epId)) {
                    this.name = ep.t
                    this.episode = ep.ep?.replace("E", "")?.trim()?.toIntOrNull()
                    this.season = ep.s?.replace("S", "")?.trim()?.toIntOrNull()
                    this.posterUrl = "https://imgcdn.kim/nf/v/200/$epId.jpg"
                    this.runTime = ep.time?.replace("m", "")?.trim()?.toIntOrNull()
                }
            )
        }

        return newTvSeriesLoadResponse(title, url, type, episodeList) {
            this.posterUrl = "https://imgcdn.kim/poster/v/$id.jpg"
            this.backgroundPosterUrl = "https://imgcdn.kim/poster/v/$id.jpg"
            this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
            this.plot = data.desc
            this.year = data.year?.toIntOrNull()
            this.tags = data.genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            this.actors = data.cast?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { ActorData(Actor(it)) }
            this.score = data.match?.replace("IMDb ", "")?.toDoubleOrNull()?.let { Score.from10(it) }
            this.duration = data.runtime?.replace("m", "")?.trim()?.toIntOrNull()
            this.contentRating = data.ua
            this.recommendations = data.suggest?.map { sug ->
                val sugId = sug.id ?: ""
                newMovieSearchResponse("", sugId, TvType.Movie) {
                    this.posterUrl = "https://imgcdn.kim/poster/v/$sugId.jpg"
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/home")
                }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val apiBase = resolveApiUrl()
        if (apiBase.isEmpty()) return false
        val userToken = getNewTvUserToken(apiBase, "nf")
        if (userToken.isEmpty()) return false
        val id = tryParseJson<Map<String, String>>(data)?.get("id") ?: data
        val headers = buildNewTvHeaders("nf", mapOf("Usertoken" to userToken))
        val response = app.get("$apiBase/newtv/player.php?id=$id", headers = headers)
        val playerResponse = tryParseJson<NewTvPlayerResponse>(response.text) ?: return false
        val videoLink = playerResponse.video_link ?: return false
        if (videoLink.isBlank()) return false
        callback.invoke(
            newExtractorLink(source = name, name = name, url = videoLink, type = ExtractorLinkType.M3U8) {
                this.referer = playerResponse.referer ?: apiBase
            }
        )
        return true
    }
}
