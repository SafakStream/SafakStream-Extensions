package com.safakstream.streamingcommunity

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class StreamingCommunity(
    private val prefLang: String = "it",
    customBaseUrl: String? = null,
    private val showUpcoming: Boolean = true
) : MainAPI() {

    companion object {
        const val TAG = "SCommunity"
        const val DEFAULT_BASE_URL = "https://streamingunity.vip/"

        @PublishedApi
        internal val mapper = jacksonObjectMapper()

        @PublishedApi
        internal inline fun <reified T> T.toJson(): String = mapper.writeValueAsString(this)

        @PublishedApi
        internal inline fun <reified T> String.fromJson(): T = mapper.readValue(this)

        fun resolveBaseUrl(customBaseUrl: String?): String {
            return if (customBaseUrl.isNullOrBlank()) DEFAULT_BASE_URL else customBaseUrl
        }

        fun resolveCdnHost(host: String): String {
            return "cdn.$host"
        }

        fun normalizeBaseUrl(url: String?): String? {
            if (url.isNullOrBlank()) return null
            var normalized = url.trim()
            if (!normalized.startsWith("http")) {
                normalized = "https://$normalized"
            }
            return normalized.trimEnd('/')
        }
    }

    private val siteRootUrl: String = resolveBaseUrl(customBaseUrl).let {
        if (it.endsWith("/")) it else "$it/"
    }
    private val siteHost: String = siteRootUrl.toHttpUrlOrNull()?.host ?: ""
    private val cdnHost: String = resolveCdnHost(siteHost)
    private var inertiaVersion: String = ""
    private var decodedXsrfToken: String = ""

    private val headers: MutableMap<String, String> = mutableMapOf(
        "Cookie" to "",
        "X-Inertia" to "true",
        "X-Inertia-Version" to inertiaVersion,
        "X-Requested-With" to "XMLHttpRequest"
    )

    override var mainUrl: String = "$siteRootUrl$prefLang"
    override var name: String = "StreamingCommunity"
    override val hasMainPage: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Documentary
    )

    override val mainPage = mainPageOf(
        SliderFetchRequestSlider("top10").toJson() to "Slider",
        SliderFetchRequestSlider("trending").toJson() to "Slider",
        SliderFetchRequestSlider("latest").toJson() to "Slider",
        SliderFetchRequestSlider("upcoming").toJson() to "Slider",
        GenreRequest("Animazione", "Animation", 0x13).toJson() to "Genre",
        GenreRequest("Avventura", "Adventure", 0xb).toJson() to "Genre",
        GenreRequest("Azione", "Action", 0x4).toJson() to "Genre",
        GenreRequest("Commedia", "Comedy", 0xc).toJson() to "Genre",
        GenreRequest("Crime", "Crime", 0x2).toJson() to "Genre",
        GenreRequest("Documentario", "Documentary", 0x18).toJson() to "Genre",
        GenreRequest("Dramma", "Drama", 0x1).toJson() to "Genre",
        GenreRequest("Famiglia", "Family", 0x10).toJson() to "Genre",
        GenreRequest("Fantascienza", "Science Fiction", 0xa).toJson() to "Genre",
        GenreRequest("Fantasy", "Fantasy", 0x8).toJson() to "Genre",
        GenreRequest("Horror", "Horror", 0x7).toJson() to "Genre",
        GenreRequest("Reality", "Reality", 0x12).toJson() to "Genre",
        GenreRequest("Romance", "Romance", 0xf).toJson() to "Genre",
        GenreRequest("Thriller", "Thriller", 0x5).toJson() to "Genre"
    )

    private fun isHtmlPayload(payload: String): Boolean {
        val trimmed = payload.trimStart()
        return trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE", ignoreCase = true)
    }

    private fun extractInertiaPageJson(html: String): String? {
        val doc = Jsoup.parse(html)
        val dataPage = doc.selectFirst("#app")?.attr("data-page") ?: return null
        return if (dataPage.isBlank()) null else Jsoup.parse(dataPage).text()
    }

    private fun parseInertiaPayload(payload: String, logContext: String): InertiaResponse? {
        if (payload.isBlank()) {
            Log.e(TAG, "$logContext: empty payload")
            return null
        }
        if (isHtmlPayload(payload)) {
            Log.e(TAG, "$logContext: expected JSON but received HTML payload")
            return null
        }
        return try {
            payload.fromJson<InertiaResponse>()
        } catch (e: Exception) {
            Log.e(TAG, "$logContext: invalid JSON payload - ${e.message}")
            null
        }
    }

    private fun parseBrowseTitles(payload: String, logContext: String): List<Title> {
        val jsonPayload = if (isHtmlPayload(payload)) {
            Log.e(TAG, "$logContext: received HTML payload, attempting embedded data-page fallback")
            extractInertiaPageJson(payload) ?: return emptyList()
        } else {
            payload
        }
        val result = parseInertiaPayload(jsonPayload, logContext) ?: return emptyList()
        return result.props.titles ?: emptyList()
    }

    private fun parseSliderFetchSections(payload: String): HomePageList? {
        if (payload.isBlank()) return null
        val trimmed = payload.trimStart()
        if (trimmed.startsWith("{") || trimmed.contains("\"message\"")) {
            Log.e(TAG, "Sliders fetch: received error object instead of slider array: ${payload.take(300)}")
            return null
        }
        if (isHtmlPayload(payload)) {
            Log.e(TAG, "Sliders fetch: expected JSON array but received HTML payload")
            return null
        }
        return try {
            val sliders = payload.fromJson<List<Slider>>()
            val slider = sliders.firstOrNull() ?: return null
            val items = searchResponseBuilder(slider.titles)
            if (items.isEmpty()) return null
            val name = slider.label.ifBlank { slider.name }
            HomePageList(name, items, false)
        } catch (e: Exception) {
            Log.e(TAG, "Sliders fetch: invalid JSON payload - ${e.message}")
            null
        }
    }

    private fun getSliderFetchHeaders(): Map<String, String> {
        return mapOf(
            "Cookie" to (headers["Cookie"] ?: ""),
            "X-Requested-With" to "XMLHttpRequest",
            "X-XSRF-TOKEN" to decodedXsrfToken,
            "Referer" to "$mainUrl/",
            "Accept" to "application/json, text/plain, */*",
            "Content-Type" to "application/json",
            "Origin" to siteRootUrl.removeSuffix("/")
        )
    }

    private suspend fun setupHeaders() {
        val response = app.get("$mainUrl/archive")
        val cookieJar = mutableMapOf<String, String>()
        response.cookies.forEach { (key, value) ->
            cookieJar[key] = value
        }
        val csrfResponse = app.get(
            "$siteRootUrl/sanctum/csrf-cookie",
            headers = mapOf(
                "Referer" to "$mainUrl/",
                "X-Requested-With" to "XMLHttpRequest"
            )
        )
        csrfResponse.cookies.forEach { (key, value) ->
            cookieJar[key] = value
        }
        headers["Cookie"] = cookieJar.entries.joinToString("; ") { "${it.key}=${it.value}" }
        decodedXsrfToken = cookieJar["XSRF-TOKEN"]?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        } ?: ""
        val page = response.document
        val inertiaPageObject = page.select("#app").attr("data-page")
        inertiaVersion = inertiaPageObject
            .substringAfter("\"version\":\"", "")
            .substringBefore("\"", "")
        headers["X-Inertia-Version"] = inertiaVersion
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (!showUpcoming && request.data == SliderFetchRequestSlider("upcoming").toJson()) {
            return null
        }
        if (headers["Cookie"].isNullOrEmpty()) {
            setupHeaders()
        }
        val hasNext = page < 0x11
        return when (request.name) {
            "Slider" -> {
                val slider = request.data.fromJson<SliderFetchRequestSlider>()
                val body = "{\"sliders\":[${slider.toJson()}]}"
                val response = app.post(
                    "$siteRootUrl/api/sliders/fetch?lang=$prefLang",
                    headers = getSliderFetchHeaders(),
                    json = body
                )
                val payload = response.text
                val r = parseSliderFetchSections(payload) ?: return null
                newHomePageResponse(r, false)
            }
            "Genre" -> {
                val genre = request.data.fromJson<GenreRequest>()
                val response = app.get(
                    "$siteRootUrl$prefLang/archive",
                    headers = getSliderFetchHeaders(),
                    params = mapOf(
                        "page" to page.toString(),
                        "lang" to prefLang,
                        "genre[]" to genre.id.toString()
                    )
                )
                val payload = response.text
                val data = payload.fromJson<GenreSearchResponse>().data ?: return null
                val name = if (prefLang == "en") genre.nameEN else genre.nameIT
                val homePageList = HomePageList(
                    name,
                    searchResponseBuilder(data),
                    false
                )
                newHomePageResponse(homePageList, hasNext)
            }
            else -> null
        }
    }

    override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse>? {
        val url = "$mainUrl/search"
        val response = app.get(url, params = mapOf("q" to query)).text
        val titles = parseBrowseTitles(response, "Search")
        return searchResponseBuilder(titles)
    }

    private fun searchResponseBuilder(listJson: List<Title>): List<com.lagradost.cloudstream3.SearchResponse> {
        return listJson
            .filter { it.type == "movie" || it.type == "tv" }
            .map { title ->
                val url = "$mainUrl/titles/${title.id}-${title.slug}"
                if (title.type == "tv") {
                    newTvSeriesSearchResponse(title.name, url, TvType.TvSeries) {
                        this.posterUrl = "https://$cdnHost/images/${title.poster}"
                    }
                } else {
                    newMovieSearchResponse(title.name, url, TvType.Movie) {
                        this.posterUrl = "https://$cdnHost/images/${title.poster}"
                    }
                }
            }
    }

    private fun getActualUrl(url: String): String {
        if (url.contains(mainUrl)) return url
        val replacingValue = if (url.contains("/it/") || url.contains("/en/")) {
            mainUrl.toHttpUrlOrNull()?.host ?: ""
        } else {
            "${mainUrl.toHttpUrlOrNull()?.host ?: ""}/$prefLang"
        }
        val currentHost = url.toHttpUrlOrNull()?.host ?: ""
        return url.replace(currentHost, replacingValue)
    }

    private suspend fun getPoster(title: TitleProp): String? {
        val tmdbId = title.tmdbId
        if (tmdbId != null) {
            val tmdbUrl = "https://www.themoviedb.org/${title.type}/$tmdbId"
            val resp = app.get(tmdbUrl).document
            val img = resp.select("img.poster.w-full").attr("srcset")
                .split(", ").lastOrNull()
            return img
        }
        return title.backgroundImageId?.let {
            "https://$cdnHost/images/$it"
        }
    }

    private suspend fun getEpisodes(props: Props): List<com.lagradost.cloudstream3.Episode> {
        val episodeList = mutableListOf<com.lagradost.cloudstream3.Episode>()
        val title = props.title ?: return emptyList()
        title.seasons?.forEach { season ->
            val responseEpisodes = mutableListOf<EpisodeDto>()
            if (season.id == props.loadedSeason?.id) {
                responseEpisodes.addAll(props.loadedSeason?.episodes ?: emptyList())
            } else {
                if (inertiaVersion.isEmpty()) setupHeaders()
                val url = "$mainUrl/titles/${title.id}-${title.slug}/season-${season.number}"
                val response = app.get(url, headers = headers)
                val obj = response.text.fromJson<InertiaResponse>()
                responseEpisodes.addAll(obj.props.loadedSeason?.episodes ?: emptyList())
            }
            responseEpisodes.forEach { ep ->
                val loadData = LoadData(
                    url = "$mainUrl/iframe/${title.id}?episode_id=${ep.id}&canPlayFHD=1",
                    type = "tv",
                    tmdbId = title.tmdbId,
                    seasonNumber = season.number,
                    episodeNumber = ep.number
                )
                episodeList.add(newEpisode(loadData.toJson()) {
                    this.name = ep.name
                    this.posterUrl = "${props.cdnUrl}/images/${ep.cover}"
                    this.description = ep.plot
                    this.episode = ep.number
                    this.season = season.number
                    this.runTime = ep.duration
                })
            }
        }
        return episodeList
    }

    override suspend fun load(url: String): LoadResponse? {
        val actualUrl = getActualUrl(url)
        if (headers["Cookie"].isNullOrEmpty()) setupHeaders()
        val response = app.get(actualUrl, headers = headers)
        val responseBody = response.text
        val props = responseBody.fromJson<InertiaResponse>().props
        val title = props.title ?: return null
        val genres = title.genres.map { it.name.replaceFirstChar { c -> c.uppercase() } }
        val year = title.releaseDate?.substringBefore("-")?.toIntOrNull()
        val related = props.sliders?.getOrNull(0)
        val poster = getPoster(title)

        return if (title.type == "tv") {
            val episodes = getEpisodes(props)
            newTvSeriesLoadResponse(title.name, actualUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = title.plot
                this.tags = genres
                this.comingSoon = false
                this.recommendations = related?.titles?.let { searchResponseBuilder(it) }
            }
        } else {
            val data = LoadData(
                url = "$mainUrl/iframe/${title.id}&canPlayFHD=1",
                type = "movie",
                tmdbId = title.tmdbId
            )
            newMovieLoadResponse(title.name, actualUrl, TvType.Movie, data.toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = title.plot
                this.tags = genres
                this.comingSoon = false
                this.recommendations = related?.titles?.let { searchResponseBuilder(it) }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false
        val loadData = data.fromJson<LoadData>()
        val response = app.get(loadData.url).document
        val iframeSrc = response.select("iframe").attr("src")

        VixCloudExtractor().getUrl(iframeSrc, siteRootUrl, subtitleCallback, callback)

        val vixsrcUrl = if (loadData.type == "movie") {
            "https://vixsrc.to/movie/${loadData.tmdbId}"
        } else {
            "https://vixsrc.to/tv/${loadData.tmdbId}/${loadData.seasonNumber}/${loadData.episodeNumber}"
        }

        VixSrcExtractor().getUrl(vixsrcUrl, "https://vixsrc.to/", subtitleCallback, callback)
        return true
    }
}
