package com.safakstream.sinewix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SinewixProvider : MainAPI() {
    override var mainUrl = "https://ydfvfdizipanel.ru"
    override var name = "Sinewix"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "tr"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
        TvType.Cartoon
    )

    private val apiToken = "9iQNC5HQwPlaFuJDkhncJ5XTJ8feGXOJatAA"
    private val hash256 = "f4d4bc98a3fc4600e7f2c2bab7533f1f03d8a70ff03c256bb11dc57050536bd0"
    private val signature = "308202c3308201aca003020102021430030f5763077759b8be9be1c54b2d35c249457a300a06082a8648ce3d0403023053310b3009060355040613025452310f300d060355040813065475726b6579310f300d06035504071306416e6b6172613110300e060355040a130753696e655769783110300e0603550403130753696e65576978301e170d3234303331393132333634355a170d3439303331333132333634355a3053310b3009060355040613025452310f300d060355040813065475726b6579310f300d06035504071306416e6b6172613110300e060355040a130753696e655769783110300e0603550403130753696e655769783059301306072a8648ce3d020106082a8648ce3d0301070342000414abdf595ca5b66d4f9bfd2f6fbf06725ea95d5be1fe367a7b8b4c06f3630f52d0a0b6d9e03d360ef8a5fb0989f6eebfa838e4a905a8f7c9e1e1273919e1e86ca350304e301d0603551d0e0416041459461dfa144e54e4df99787e9cf2717ce7a32b90301f0603551d2304183016801459461dfa144e54e4df99787e9cf2717ce7a32b90300c0603551d13040530030101ff300a06082a8648ce3d0403020348003045022066faae9ec20a32e1858a2f4ca676ee5ae8c7d6fc610c1440263f3bf74112cbe1022100ec5a1e2f38fa093c3fbfa3f345cbbce4fcba884260f88a91da933ba9cb62955f"
    private val hmacSecret = "4962QZiZZqWbQlC0k0pdIvojy-STtR8NTq7oQCxMGBF2kwsVX2DBxgK72BovMy4U"

    // Cihaz ID'si (her uygulama açılışında rastgele oluşturulur)
    private val deviceId = "uuid_" + java.util.UUID.randomUUID().toString()

    // Session ID (giriş yapıldıktan sonra alınır, şimdilik boş)
    private var sessionId = ""
    private var jwtToken: String? = null
    private var tokenExpiresAt: Long = 0

    // API isteği için gerekli header'ları oluşturan fonksiyon
    private fun getApiHeaders(method: String, fullUrl: String, formData: Map<String, String>? = null): Map<String, String> {
        val nowTs = (System.currentTimeMillis() / 1000).toString()
        val reqNonceBytes = ByteArray(24)
        SecureRandom().nextBytes(reqNonceBytes)
        val reqNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(reqNonceBytes)

        // İmzalanacak string'i oluştur
        val urlWithoutQuery = fullUrl.substringBefore("?")
        val apiIndex = urlWithoutQuery.indexOf("/api/")
        val apiPathForSigning = if (apiIndex != -1) urlWithoutQuery.substring(apiIndex) else "/api"

        val bodyBytes = if (formData.isNullOrEmpty()) {
            ByteArray(0)
        } else {
            formData.entries.joinToString("&") { "${it.key}=${it.value}" }.toByteArray(Charsets.UTF_8)
        }
        val bodyHash = sha256Hex(bodyBytes)

        val stringToSign = "$method\n$apiPathForSigning\n$nowTs\n$reqNonce\n$bodyHash"

        // HMAC-SHA256 imzası
        val sigBytes = hmacSha256(hmacSecret, stringToSign)
        val sigHex = sigBytes.joinToString("") { "%02x".format(it) }

        return mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/x-www-form-urlencoded",
            "packagename" to "com.wxsmash.game",
            "User-Agent" to "EasyPlex (Android 14; SM-A546B; Samsung Galaxy A54 5G; tr)",
            "X-App-Id" to "sinewix-mobile",
            "X-Platform" to "android",
            "X-Device-Id" to deviceId,
            "signature" to signature,
            "hash256" to hash256,
            "X-Encrypt-Response" to "1",
            "X-Timestamp" to nowTs,
            "X-Nonce" to reqNonce,
            "X-Signature" to "sha256=$sigHex"
        )
    }

    // SHA-256 hash fonksiyonu
    private fun sha256Hex(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    // HMAC-SHA256 imza fonksiyonu
    private fun hmacSha256(secret: String, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    // Ana sayfa kategorileri
    override val mainPage = mainPageOf(
        "$mainUrl/public/api/genres/latestmovies/all/$apiToken" to "Son Eklenen Filmler",
        "$mainUrl/public/api/genres/latestseries/all/$apiToken" to "Son Eklenen Diziler",
        "$mainUrl/public/api/media/seriesEpisodesAll/$apiToken" to "Yeni Bölümler",
        "$mainUrl/public/api/genres/latestanimes/all/$apiToken" to "Son Animeler",
        "$mainUrl/public/api/genres/mediaLibrary/show/80/movie/$apiToken" to "Suç Filmleri",
        "$mainUrl/public/api/genres/mediaLibrary/show/80/serie/$apiToken" to "Suç Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/9648/movie/$apiToken" to "Gizem Filmleri",
        "$mainUrl/public/api/genres/mediaLibrary/show/9648/serie/$apiToken" to "Gizem Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/10769/serie/$apiToken" to "Kore Dizileri",
        "$mainUrl/public/api/genres/mediaLibrary/show/16/movie/$apiToken" to "Animasyonlar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"
        val headers = getApiHeaders("GET", url)

        val items = if (request.name == "Yeni Bölümler") {
            val data = app.get(url, headers = headers).parsedSafe<SinewixYeniBolumResponse>()
            data?.data?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        } else {
            val data = app.get(url, headers = headers).parsedSafe<SinewixResponse>()
            data?.data?.mapNotNull { it.toSearchResponse(request.name) } ?: emptyList()
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/public/api/search/$query/$apiToken"
        val headers = getApiHeaders("GET", url)
        val response = app.get(url, headers = headers).parsedSafe<SinewixResponse>()

        val items = response?.searchResponse ?: response?.data ?: emptyList()
        return items.mapNotNull { it.toSearchResponse(null) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val headers = getApiHeaders("GET", url)
        val data = app.get(url, headers = headers).parsedSafe<SinewixItem>() ?: return null

        val title = data.name ?: data.title ?: return null
        val poster = data.posterPath ?: data.backdropPath ?: data.backdropPathTv ?: ""

        val isSeries = url.contains("series/show") ||
                url.contains("serie") ||
                data.type == "serie"
        val isAnime = url.contains("anime") || data.type == "anime"

        return if (isSeries || isAnime) {
            val episodes = data.seasons
                ?.flatMap { season ->
                    season.episodes?.map { episode ->
                        newEpisode(episode.videos?.firstOrNull()?.link ?: "") {
                            this.name = episode.name ?: "Bölüm ${episode.episodeNumber}"
                            this.season = season.seasonNumber
                            this.episode = episode.episodeNumber
                            this.posterUrl = episode.stillPath ?: episode.stillPathTv
                        }
                    } ?: emptyList()
                } ?: emptyList()

            newTvSeriesLoadResponse(
                title,
                url,
                if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
            }
        } else {
            val videoLink = data.videos?.firstOrNull()?.link ?: data.directLink ?: ""
            newMovieLoadResponse(title, url, TvType.Movie, videoLink) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        val isDirectFile = Regex("\\.(mkv|mp4|m3u8|webm|avi)(\\?|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(data)

        if (isDirectFile) {
            val isM3u8 = data.contains(".m3u8", ignoreCase = true)
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = data,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = mainUrl
                }
            )
        } else {
            loadExtractor(data, subtitleCallback, callback)
        }
        return true
    }

    private fun SinewixItem.toSearchResponse(url: String?): SearchResponse? {
        val itemTitle = name ?: title ?: return null
        val poster = posterPath ?: backdropPath ?: backdropPathTv ?: ""

        val type = when {
            url?.contains("serie") == true -> TvType.TvSeries
            url?.contains("anime") == true -> TvType.Anime
            this.type == "serie" -> TvType.TvSeries
            this.type == "anime" -> TvType.Anime
            else -> TvType.Movie
        }

        val href = if (type == TvType.Movie) {
            "$mainUrl/public/api/media/detail/$id/$apiToken"
        } else {
            "$mainUrl/public/api/series/show/$id/$apiToken"
        }

        return if (type == TvType.Movie) {
            newMovieSearchResponse(itemTitle, href, type) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(itemTitle, href, type) {
                this.posterUrl = poster
            }
        }
    }

    private fun SinewixYeniBolum.toSearchResponse(): SearchResponse? {
        val itemTitle = showName ?: return null
        val poster = posterPath ?: stillPath ?: ""

        val displayTitle = "$itemTitle S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"

        val href = if (!directLink.isNullOrBlank()) {
            directLink
        } else {
            "$mainUrl/public/api/series/show/$id/$apiToken"
        }

        return newTvSeriesSearchResponse(displayTitle, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }
}
