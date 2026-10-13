// SafakStream-Extensions/Sinewix/src/main/kotlin/com/safakstream/Sinewix.kt

package com.safakstream

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Sinewix : MainAPI() {
    override var mainUrl = "https://ydfvfdizipanel.ru"
    override var name = "Sinewix"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
        TvType.Cartoon
    )

    companion object {
        private const val API_TOKEN = "9iQNC5HQwPlaFuJDkhncJ5XTJ8feGXOJatAA"
        private const val HMAC_SECRET = "4962QZiZZqWbQlC0k0pdIvojy-STtR8NTq7oQCxMGBF2kwsVX2DBxgK72BovMy4U"
        private const val RESP_SECRET = "H1KKG-JCt1RqHQawNxw7xujy2-vevYuyBqX3Aei1EAdi9VwDnmRL-3Oz2xxvy1VU"
        private const val CERT_SHA256 = "f4d4bc98a3fc4600e7f2c2bab7533f1f03d8a70ff03c256bb11dc57050536bd0"
        private const val RAW_CERT_HEX = "308202c3308201aca003020102021430030f5763077759b8be9be1c54b2d35c249457a300a06082a8648ce3d0403023053310b3009060355040613025452310f300d060355040813065475726b6579310f300d06035504071306416e6b6172613110300e060355040a130753696e655769783110300e0603550403130753696e65576978301e170d3234303331393132333634355a170d3439303331333132333634355a3053310b3009060355040613025452310f300d060355040813065475726b6579310f300d06035504071306416e6b6172613110300e060355040a130753696e655769783110300e0603550403130753696e655769783059301306072a8648ce3d020106082a8648ce3d0301070342000414abdf595ca5b66d4f9bfd2f6fbf06725ea95d5be1fe367a7b8b4c06f3630f52d0a0b6d9e03d360ef8a5fb0989f6eebfa838e4a905a8f7c9e1e1273919e1e86ca350304e301d0603551d0e0416041459461dfa144e54e4df99787e9cf2717ce7a32b90301f0603551d2304183016801459461dfa144e54e4df99787e9cf2717ce7a32b90300c0603551d13040530030101ff300a06082a8648ce3d0403020348003045022066faae9ec20a32e1858a2f4ca676ee5ae8c7d6fc610c1440263f3bf74112cbe1022100ec5a1e2f38fa093c3fbfa3f345cbbce4fcba884260f88a91da933ba9cb62955f"

        private val authMutex = Mutex()
        private var jwtToken: String? = null
        private var sessionId: String = ""
        private var tokenExpiresAt: Long = 0L
        private val deviceId: String = "uuid_" + UUID.randomUUID().toString()

        fun sha256Hex(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            return hash.joinToString("") { "%02x".format(it) }
        }

        fun hmacSha256(secret: String, data: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(keySpec)
            return mac.doFinal(data.toByteArray(Charsets.UTF_8))
        }

        fun b64urlNoPad(data: ByteArray): String {
            return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/public/api/genres/latestmovies/all/$API_TOKEN" to "Son Eklenen Filmler",
        "${mainUrl}/public/api/genres/latestseries/all/$API_TOKEN" to "Son Eklenen Diziler",
        "${mainUrl}/public/api/media/seriesEpisodesAll/$API_TOKEN" to "Yeni Bölümler",
        "${mainUrl}/public/api/genres/latestanimes/all/$API_TOKEN" to "Son Animeler",
        "${mainUrl}/public/api/genres/mediaLibrary/show/80/movie/$API_TOKEN" to "Suç Filmleri",
        "${mainUrl}/public/api/genres/mediaLibrary/show/80/serie/$API_TOKEN" to "Suç Dizileri",
        "${mainUrl}/public/api/genres/mediaLibrary/show/9648/movie/$API_TOKEN" to "Gizem Filmleri",
        "${mainUrl}/public/api/genres/mediaLibrary/show/9648/serie/$API_TOKEN" to "Gizem Dizileri",
        "${mainUrl}/public/api/genres/mediaLibrary/show/10769/serie/$API_TOKEN" to "Kore Diziler",
        "${mainUrl}/public/api/genres/mediaLibrary/show/16/movie/$API_TOKEN" to "Animasyonlar"
    )

    // ==================== AUTH ====================

    private suspend fun ensureAuthenticated(): Boolean {
        // Token hala geçerli mi?
        if (!jwtToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt) {
            return true
        }

        return authMutex.withLock {
            // Kilit alındıktan sonra tekrar kontrol et
            if (!jwtToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt) {
                return@withLock true
            }

            try {
                val nowTs = (System.currentTimeMillis() / 1000L).toString()
                val tokenNonceBytes = ByteArray(24)
                SecureRandom().nextBytes(tokenNonceBytes)
                val tokenNonce = b64urlNoPad(tokenNonceBytes)

                // Form parametreleri
                val formParams = linkedMapOf(
                    "signature" to RAW_CERT_HEX,
                    "package_name" to "com.wxsmash.game",
                    "device_id" to deviceId,
                    "nonce" to tokenNonce,
                    "timestamp" to nowTs,
                    "app_version" to "2.5.3",
                    "app_id" to "sinewix-mobile",
                    "platform" to "android"
                )

                val bodyStr = formParams.entries.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }
                val bodyBytes = bodyStr.toByteArray(Charsets.UTF_8)
                val bodyHash = sha256Hex(bodyBytes)

                val reqNonceBytes = ByteArray(24)
                SecureRandom().nextBytes(reqNonceBytes)
                val reqNonce = b64urlNoPad(reqNonceBytes)

                val apiPath = "/api/tokens/mobile/generate"
                val stringToSign = "POST\n$apiPath\n$nowTs\n$reqNonce\n$bodyHash"
                val sigHex = hmacSha256(HMAC_SECRET, stringToSign).joinToString("") { "%02x".format(it) }

                val headers = mapOf(
                    "Accept" to "application/json",
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "packagename" to "com.wxsmash.game",
                    "User-Agent" to "EasyPlex (Android 14; SM-A546B; Samsung Galaxy A54 5G; tr)",
                    "X-App-Id" to "sinewix-mobile",
                    "X-Platform" to "android",
                    "X-Device-Id" to deviceId,
                    "signature" to RAW_CERT_HEX,
                    "hash256" to CERT_SHA256,
                    "X-Encrypt-Response" to "1",
                    "X-Timestamp" to nowTs,
                    "X-Nonce" to reqNonce,
                    "X-Signature" to "sha256=$sigHex"
                )

                val url = "$mainUrl/public$apiPath"
                val response = app.post(
                    url,
                    headers = headers,
                    data = formParams,
                    referer = mainUrl
                ).text

                Log.d("Sinewix", "Auth response: ${response.take(200)}")

                val json = JSONObject(response)
                val decrypted = if (json.optBoolean("encrypted")) {
                    decryptAesGcm(json, sessionId, reqNonce, nowTs)
                } else {
                    response
                }

                val authObj = JSONObject(decrypted)
                if (authObj.optBoolean("success")) {
                    jwtToken = authObj.getString("access_token")
                    sessionId = authObj.getString("session_id")
                    val expiresIn = authObj.optLong("expires_in", 86400L)
                    tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 300L) * 1000L
                    Log.d("Sinewix", "Auth OK, token expires in ${expiresIn}s")
                    true
                } else {
                    Log.e("Sinewix", "Auth failed: $decrypted")
                    false
                }
            } catch (e: Exception) {
                Log.e("Sinewix", "Auth error: ${e.message}", e)
                false
            }
        }
    }

    private fun decryptAesGcm(json: JSONObject, sessionId: String, nonce: String, timestamp: String): String {
        // Şimdilik placeholder - gerçek AES-GCM implementasyonu için anahtar türetme gerekli
        // Smali kodunda Companion.decryptAesGcm çağrılıyor
        return json.optString("data", json.toString())
    }

    // ==================== API REQUEST ====================

    private suspend fun apiRequest(
        method: String,
        fullUrlOrPath: String,
        formData: Map<String, String>? = null
    ): String {
        ensureAuthenticated()

        // URL normalize etme
        val fullUrl = when {
            fullUrlOrPath.startsWith("http") -> fullUrlOrPath
            fullUrlOrPath.startsWith("/api/") -> "$mainUrl/public$fullUrlOrPath"
            fullUrlOrPath.startsWith("api/") -> "$mainUrl/public/$fullUrlOrPath"
            else -> "$mainUrl/public/api/${fullUrlOrPath.removePrefix("/")}"
        }

        val urlWithoutQuery = fullUrl.substringBefore("?")
        val apiIndex = urlWithoutQuery.indexOf("/api/")
        val apiPathForSigning = if (apiIndex != -1) urlWithoutQuery.substring(apiIndex) else "/api"

        val nowTs = (System.currentTimeMillis() / 1000L).toString()

        // Request nonce
        val reqNonceBytes = ByteArray(24)
        SecureRandom().nextBytes(reqNonceBytes)
        val reqNonce = b64urlNoPad(reqNonceBytes)

        // Body
        val bodyBytes = if (!formData.isNullOrEmpty()) {
            val bodyStr = formData.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            bodyStr.toByteArray(Charsets.UTF_8)
        } else {
            ByteArray(0)
        }
        val bodyHash = sha256Hex(bodyBytes)

        // String to sign
        val stringToSign = "${method.uppercase(Locale.ROOT)}\n$apiPathForSigning\n$nowTs\n$reqNonce\n$bodyHash"
        val sigHex = hmacSha256(HMAC_SECRET, stringToSign).joinToString("") { "%02x".format(it) }

        // Header'lar
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "packagename" to "com.wxsmash.game",
            "User-Agent" to "EasyPlex (Android 14; SM-A546B; Samsung Galaxy A54 5G; tr)",
            "X-App-Id" to "sinewix-mobile",
            "X-Platform" to "android",
            "X-Device-Id" to deviceId,
            "signature" to RAW_CERT_HEX,
            "hash256" to CERT_SHA256,
            "X-Encrypt-Response" to "1",
            "X-Timestamp" to nowTs,
            "X-Nonce" to reqNonce,
            "X-Signature" to "sha256=$sigHex"
        )

        if (sessionId.isNotBlank()) {
            headers["X-Session-Id"] = sessionId
        }
        if (!jwtToken.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $jwtToken"
        }

        val responseText = if (method.uppercase(Locale.ROOT) == "POST") {
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            app.post(
                fullUrl,
                headers = headers,
                data = formData ?: emptyMap(),
                referer = mainUrl
            ).text
        } else {
            app.get(
                fullUrl,
                headers = headers,
                referer = mainUrl
            ).text
        }

        // Şifreli yanıtı çöz
        return if (responseText.contains("\"encrypted\":true") || responseText.contains("\"encrypted\": true")) {
            try {
                val json = JSONObject(responseText)
                decryptAesGcm(json, sessionId, reqNonce, nowTs)
            } catch (e: Exception) {
                Log.e("Sinewix", "Decrypt error: ${e.message}")
                responseText
            }
        } else {
            responseText
        }
    }

    // ==================== DTOs ====================

    data class SineWixResponseHash(
        @JsonProperty("current_page") val currentPage: Int? = null,
        @JsonProperty("data") val data: List<SineWixIcerikler>? = null,
        @JsonProperty("search") val searchResponse: List<SineWixIcerikler>? = null,
        @JsonProperty("results") val results: List<SineWixIcerikler>? = null
    )

    data class SineWixYeniBolumResponse(
        @JsonProperty("current_page") val currentPage: Int? = null,
        @JsonProperty("data") val data: List<SineWixYeniBolum>? = null
    )

    data class SineWixIcerikler(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("backdrop_path_tv") val backdropPathTv: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("seasons") val seasons: List<SineWixSeason>? = null,
        @JsonProperty("videos") val videos: List<SineWixVideo>? = null,
        @JsonProperty("link") val directLink: String? = null
    )

    data class SineWixYeniBolum(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("episode_id") val episodeId: Int? = null,
        @JsonProperty("serie_id") val serieId: Int? = null,
        @JsonProperty("name") val showName: String? = null,
        @JsonProperty("episode_name") val episodeName: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("link") val directLink: String? = null
    )

    data class SineWixSeason(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episodes") val episodes: List<SineWixEpisode>? = null
    )

    data class SineWixEpisode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("still_path_tv") val stillPathTv: String? = null,
        @JsonProperty("videos") val videos: List<SineWixVideo>? = null
    )

    data class SineWixVideo(
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("server") val server: String? = null
    )

    // ==================== HELPERS ====================

    private val jacksonMapper: ObjectMapper by lazy {
        ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // ==================== CONVERTERS ====================

    private fun SineWixIcerikler.toSearchResponse(url: String? = null): SearchResponse? {
        val displayTitle = name ?: title ?: return null
        val poster = posterPath ?: backdropPath ?: backdropPathTv ?: ""

        val tvType = when {
            type.equals("serie", true) || type.equals("series", true) -> TvType.TvSeries
            type.equals("anime", true) -> TvType.Anime
            type.equals("movie", true) -> TvType.Movie
            url?.contains("/movie/") == true -> TvType.Movie
            url?.contains("/serie/") == true -> TvType.TvSeries
            url?.contains("animes") == true || url?.contains("anime") == true -> TvType.Anime
            url?.contains("/series/") == true -> TvType.TvSeries
            else -> TvType.Movie
        }

        val href = if (tvType == TvType.TvSeries || tvType == TvType.Anime) {
            "$mainUrl/public/api/series/show/$id/$API_TOKEN"
        } else {
            "$mainUrl/public/api/media/detail/$id/$API_TOKEN"
        }

        return if (tvType == TvType.Movie) {
            newMovieSearchResponse(displayTitle, href, tvType) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(displayTitle, href, tvType) {
                this.posterUrl = poster
            }
        }
    }

    private fun SineWixYeniBolum.toSearchResponse(): SearchResponse? {
        val displayTitle = showName ?: return null
        val poster = posterPath ?: stillPath ?: ""

        val seriesId = serieId ?: id
        val href = if (!directLink.isNullOrBlank()) {
            directLink
        } else {
            "$mainUrl/public/api/series/show/$seriesId/$API_TOKEN"
        }

        val seasonPadded = (seasonNumber ?: 0).toString().padStart(2, '0')
        val episodePadded = (episodeNumber ?: 0).toString().padStart(2, '0')
        val title = "$displayTitle S${seasonPadded}E${episodePadded}"

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    // ==================== MAIN PAGE ====================

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"
        val response = try {
            apiRequest("GET", url)
        } catch (e: Exception) {
            Log.e("Sinewix", "getMainPage error: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }

        val items: List<SearchResponse> = try {
            if (request.name == "Yeni Bölümler") {
                val data = jacksonMapper.readValue<SineWixYeniBolumResponse>(response)
                data.data?.mapNotNull { it.toSearchResponse() } ?: emptyList()
            } else {
                val data = jacksonMapper.readValue<SineWixResponseHash>(response)
                data.data?.mapNotNull { it.toSearchResponse(request.data) } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("Sinewix", "Parse error: ${e.message}")
            emptyList()
        }

        return newHomePageResponse(request.name, items)
    }

    // ==================== SEARCH ====================

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        return try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8").replace("+", "%20")
            val url = "$mainUrl/public/api/search/$encoded/$API_TOKEN"
            val response = apiRequest("GET", url)

            val data = jacksonMapper.readValue<SineWixResponseHash>(response)
            val list = data.results ?: data.searchResponse ?: data.data ?: emptyList()

            list.mapNotNull { it.toSearchResponse() }
        } catch (e: Exception) {
            Log.e("Sinewix", "search error: ${e.message}")
            emptyList()
        }
    }

    // ==================== LOAD ====================

    override suspend fun load(url: String): LoadResponse? {
        val response = try {
            apiRequest("GET", url)
        } catch (e: Exception) {
            Log.e("Sinewix", "load error: ${e.message}")
            return null
        }

        val it: SineWixIcerikler = try {
            jacksonMapper.readValue(response)
        } catch (e: Exception) {
            Log.e("Sinewix", "load parse error: ${e.message}")
            return null
        }

        val title = it.name ?: it.title ?: return null
        val poster = it.posterPath ?: it.backdropPath ?: it.backdropPathTv ?: ""
        val plot = it.overview
        val year = (it.releaseDate ?: it.firstAirDate)?.take(4)?.toIntOrNull()

        // Tip belirleme
        val tvType = when {
            it.type.equals("serie", true) || it.type.equals("series", true) -> TvType.TvSeries
            it.type.equals("anime", true) -> TvType.Anime
            it.type.equals("movie", true) -> TvType.Movie
            url.contains("/series/show/") -> TvType.TvSeries
            url.contains("/media/detail/") -> TvType.Movie
            else -> TvType.Movie
        }

        return if (tvType == TvType.TvSeries || tvType == TvType.Anime) {
            // Bölümleri çıkar
            val episodes = it.seasons?.flatMap { season ->
                season.episodes?.map { episode ->
                    val videoLinks = episode.videos
                        ?.mapNotNull { v -> v.link?.takeIf { l -> l.isNotBlank() } }
                        ?: emptyList()
                    val videoPayload = videoLinks.joinToString("|||")

                    newEpisode(videoPayload) {
                        this.name = episode.name ?: "Bölüm ${episode.episodeNumber}"
                        this.season = season.seasonNumber
                        this.episode = episode.episodeNumber
                        this.posterUrl = episode.stillPath ?: episode.stillPathTv
                    }
                } ?: emptyList()
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            // Film - video linklerini payload olarak birleştir
            val videoLinks = it.videos
                ?.mapNotNull { v -> v.link?.takeIf { l -> l.isNotBlank() } }
                ?: emptyList()
            val directLink = it.directLink?.takeIf { l -> l.isNotBlank() }
            val allLinks = if (directLink != null) videoLinks + directLink else videoLinks
            val videoPayload = allLinks.joinToString("|||")

            newMovieLoadResponse(title, url, tvType, videoPayload) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    // ==================== LOAD LINKS ====================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        val links = data.split("|||").map { it.trim() }.filter { it.isNotBlank() }
        var found = false

        for (link in links) {
            try {
                when {
                    // MediaFire linki
                    link.contains("mediafire.com", ignoreCase = true) -> {
                        val pageHtml = try {
                            app.get(
                                link,
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                )
                            ).text
                        } catch (e: Exception) {
                            Log.e("Sinewix", "MediaFire fetch error: ${e.message}")
                            continue
                        }

                        // Download linkini regex ile bul
                        val downloadRegex = Regex("""href=["'](https://download[^"']+)["']""", RegexOption.IGNORE_CASE)
                        val ariaRegex = Regex("""aria-label=["']Download file["'][^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        val directUrl = downloadRegex.find(pageHtml)?.groupValues?.get(1)
                            ?: ariaRegex.find(pageHtml)?.groupValues?.get(1)

                        if (!directUrl.isNullOrBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = "Sinewix (MediaFire)",
                                    name = "Sinewix (MediaFire)",
                                    url = directUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = link
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            found = true
                        }
                    }

                    // Direkt video linki
                    Regex("""\.(mkv|mp4|m3u8|webm|avi)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(link) -> {
                        val isM3u8 = link.contains(".m3u8", ignoreCase = true)
                        callback.invoke(
                            newExtractorLink(
                                source = "Sinewix",
                                name = "Sinewix",
                                url = link,
                                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }

                    // Diğer linkler - loadExtractor ile çöz
                    else -> {
                        val handled = try {
                            loadExtractor(link, mainUrl, subtitleCallback, callback)
                        } catch (e: Exception) {
                            Log.e("Sinewix", "Extractor error for $link: ${e.message}")
                            false
                        }
                        if (handled) found = true
                    }
                }
            } catch (e: Exception) {
                Log.e("Sinewix", "loadLinks error for $link: ${e.message}")
            }
        }

        return found
    }
}
