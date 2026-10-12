package com.safakstream.streamingcommunity

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class StreamingCommunity(
    private var lang: String = "it",
    customBaseUrl: String? = null,
    private val showUpcoming: Boolean = true
) : MainAPI() {

    companion object {
        const val TAG = "SCommunity"
        const val DEFAULT_BASE_URL = "https://streamingunity.vip/"
        private var name: String = "StreamingCommunity"

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
    private val siteHost: String = siteRootUrl.toHttpUrlOrNull()?.host() ?: ""
    private val cdnHost: String = resolveCdnHost(siteHost)
    private var inertiaVersion: String = ""
    private var decodedXsrfToken: String = ""

    private val headers: MutableMap<String, String> = mutableMapOf(
        "Cookie" to "",
        "X-Inertia" to "true",
        "X-Inertia-Version" to inertiaVersion,
        "X-Requested-With" to "XMLHttpRequest"
    )

    override var mainUrl: String = "$siteRootUrl$lang"
    override var name: String = "StreamingCommunity"
    override val hasMainPage: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Documentary
    )

    override val mainPage = mainPageOf(
        AppUtils.toJson(SliderFetchRequestSlider("top10")) to "Slider",
        AppUtils.toJson(SliderFetchRequestSlider("trending")) to "Slider",
        AppUtils.toJson(SliderFetchRequestSlider("latest")) to "Slider",
        AppUtils.toJson(SliderFetchRequestSlider("upcoming")) to "Slider",
        AppUtils.toJson(GenreRequest("Animazione", "Animation", 0x13)) to "Genre",
        AppUtils.toJson(GenreRequest("Avventura", "Adventure", 0xb)) to "Genre",
        AppUtils.toJson(GenreRequest("Azione", "Action", 0x4)) to "Genre",
        AppUtils.toJson(GenreRequest("Commedia", "Comedy", 0xc)) to "Genre",
        AppUtils.toJson(GenreRequest("Crime", "Crime", 0x2)) to "Genre",
        AppUtils.toJson(GenreRequest("Documentario", "Documentary", 0x18)) to "Genre",
        AppUtils.toJson(GenreRequest("Dramma", "Drama", 0x1)) to "Genre",
        AppUtils.toJson(GenreRequest("Famiglia", "Family", 0x10)) to "Genre",
        AppUtils.toJson(GenreRequest("Fantascienza", "Science Fiction", 0xa)) to "Genre",
        AppUtils.toJson(GenreRequest("Fantasy", "Fantasy", 0x8)) to "Genre",
        AppUtils.toJson(GenreRequest("Horror", "Horror", 0x7)) to "Genre",
        AppUtils.toJson(GenreRequest("Reality", "Reality", 0x12)) to "Genre",
        AppUtils.toJson(GenreRequest("Romance", "Romance", 0xf)) to "Genre",
        AppUtils.toJson(GenreRequest("Thriller", "Thriller", 0x5)) to "Genre"
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
            AppUtils.parseJson<InertiaResponse>(payload)
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
            val sliders = AppUtils.parseJson<List<Slider>>(payload)
            val slider = sliders.firstOrNull() ?: return null
            val items = searchResponseBuilder(slider.titles)
            if (items.isEmpty()) return null
            val name = slider.label.ifBlank { slider.name }
            HomePageList(name, items, false)
        } catch (e: Exception) {
            Log.e(TAG, "Sliders fetch: invalid JSON payload - ${e.message}")
            Log.e(TAG, payload)
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
        if (!showUpcoming && request.data == AppUtils.toJson(SliderFetchRequestSlider("upcoming"))) {
            return null
        }
        if (headers["Cookie"].isNullOrEmpty()) {
            setupHeaders()
        }
        val hasNext = page < 0x11
        return when (request.name) {
            "Slider" -> {
                val slider = AppUtils.parseJson<SliderFetchRequestSlider>(request.data)
                val body = "{\"sliders\":[${AppUtils.toJson(slider)}]}"
                val response = app.post(
                    "$siteRootUrl/api/sliders/fetch?lang=$lang",
                    headers = getSliderFetchHeaders(),
                    json = body
                )
                val payload = response.text
                val r = parseSliderFetchSections(payload) ?: return null
                newHomePageResponse(r, false)
            }
            "Genre" -> {
                val genre = AppUtils.parseJson<GenreRequest>(request.data)
                val response = app.get(
                    "$siteRootUrl$lang/archive",
                    headers = getSliderFetchHeaders(),
                    params = mapOf(
                        "page" to page.toString(),
                        "lang" to lang,
                        "genre[]" to genre.id.toString()
                    )
                )
                val payload = response.text
                val data = AppUtils.tryParseJson<SearchResponse>(payload)?.data ?: return null
                val name = if (lang == "en") genre.nameEN else genre.nameIT
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

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search"
        val response = app.get(url, params = mapOf("q" to query)).text
        val titles = parseBrowseTitles(response, "Search")
        return searchResponseBuilder(titles)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val params = mutableMapOf("q" to query)
        if (page > 1) params["page"] = page.toString()
        val response = app.get("$mainUrl/search", params = params).text
        val titles = parseBrowseTitles(response, "Search page=$page")
        val items = searchResponseBuilder(titles)
        val hasNext = items.isNotEmpty() && items.size >= 60
        return newSearchResponseList(items, hasNext)
    }

    private fun searchResponseBuilder(listJson: List<Title>): List<SearchResponse> {
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
            mainUrl.toHttpUrlOrNull()?.host() ?: ""
        } else {
            "${mainUrl.toHttpUrlOrNull()?.host() ?: ""}/$lang"
        }
        val currentHost = url.toHttpUrlOrNull()?.host() ?: ""
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

    private suspend fun getEpisodes(props: Props): List<Episode> {
        val episodeList = mutableListOf<Episode>()
        val title = props.title ?: return emptyList()
        title.seasons?.forEach { season ->
            val responseEpisodes = mutableListOf<Episode>()
            if (season.id == props.loadedSeason?.id) {
                responseEpisodes.addAll(props.loadedSeason?.episodes ?: emptyList())
            } else {
                if (inertiaVersion.isEmpty()) setupHeaders()
                val url = "$mainUrl/titles/${title.id}-${title.slug}/season-${season.number}"
                val response = app.get(url, headers = headers)
                val obj = AppUtils.parseJson<InertiaResponse>(response.text)
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
                episodeList.add(newEpisode(AppUtils.toJson(loadData)) {
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
        val props = AppUtils.parseJson<InertiaResponse>(responseBody).props
        val title = props.title ?: return null
        val genres = title.genres.map { APIHolder.capitalize(it.name) }
        val year = title.releaseDate?.substringBefore("-")?.toIntOrNull()
        val related = props.sliders?.getOrNull(0)
        val trailers = title.trailers?.mapNotNull { it.youtubeUrl }
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
                this.trailers = trailers
            }
        } else {
            val data = LoadData(
                url = "$mainUrl/iframe/${title.id}&canPlayFHD=1",
                type = "movie",
                tmdbId = title.tmdbId
            )
            newMovieLoadResponse(title.name, actualUrl, TvType.Movie, AppUtils.toJson(data)) {
                this.posterUrl = poster
                this.year = year
                this.plot = title.plot
                this.tags = genres
                this.comingSoon = false
                this.recommendations = related?.titles?.let { searchResponseBuilder(it) }
                this.trailers = trailers
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
        val loadData = AppUtils.parseJson<LoadData>(data)
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
