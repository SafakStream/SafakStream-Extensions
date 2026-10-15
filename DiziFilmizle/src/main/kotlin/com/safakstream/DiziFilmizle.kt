package com.safakstream

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class DiziFilmizle : MainAPI() {
    override var mainUrl = "https://dizifilmizle.to"
    override var name = "DiziFilmizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mapper = jacksonObjectMapper()

    override val mainPage = mainPageOf(
        "movies/latest" to "Son Filmler",
        "series/latest" to "Son Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val apiUrl = "$mainUrl/api/${request.data}?page=$page&limit=30"
        Log.d("DiziFilmizle", "getMainPage: $apiUrl")
        val response = app.get(apiUrl).text

        val items = mutableListOf<SearchResponse>()
        try {
            val parsed = mapper.readValue<MovieListResponse>(response)
            parsed.movies?.forEach { m ->
                items.add(m.toSearchResponse(isSeries = false))
            }
            parsed.series?.forEach { s ->
                items.add(s.toSearchResponse(isSeries = true))
            }
        } catch (e: Exception) {
            Log.e("DiziFilmizle", "getMainPage parse hatasi: ${e.message}")
        }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val apiUrl = "$mainUrl/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        Log.d("DiziFilmizle", "search: $apiUrl")
        val response = app.get(apiUrl).text
        val items = mutableListOf<SearchResponse>()
        try {
            val parsed = mapper.readValue<SearchResponse2>(response)
            parsed.results?.forEach { r ->
                items.add(r.toSearchResponse())
            }
        } catch (e: Exception) {
            Log.e("DiziFilmizle", "search parse hatasi: ${e.message}")
        }
        return items
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d("DiziFilmizle", "load: $url")

        // URL formatı: /film/{slug} veya /dizi/{slug}
        val isSeries = url.contains("/dizi/")
        val slug = url.substringAfterLast("/")

        if (isSeries) {
            return loadSeries(url, slug)
        } else {
            return loadMovie(url, slug)
        }
    }

    private suspend fun loadMovie(url: String, slug: String): LoadResponse? {
        val doc = app.get(url).document

        // Film JSON'u HTML'de parts ile gömülü
        val html = doc.html()

        // Film adı ve yılı için title/desc — ana sayfa listesinden gelen title'ı kullan
        // Alternatif: search API'sinden bu slug'ı ara
        val searchResp = app.get("$mainUrl/api/search?q=$slug").text
        val searchParsed = try { mapper.readValue<SearchResponse2>(searchResp) } catch (e: Exception) { null }
        val matched = searchParsed?.results?.firstOrNull { it.slug == slug }

        val title = matched?.title ?: doc.selectFirst("h1")?.text() ?: slug
        val poster = matched?.posterUrl
        val year = matched?.year
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")

        // Parts'ı HTML'den çek
        val partsRegex = Regex("""parts.{0,3}\[([\s\S]*?)\]""")
        val partsMatch = partsRegex.find(html)
        val links = mutableListOf<Pair<String, String>>() // url to title

        if (partsMatch != null) {
            val partsStr = "[${partsMatch.groupValues[1]}]"
                .replace("\\\"", "\"")
                .replace("\\/", "/")
            try {
                val arr = JSONArray(partsStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pUrl = obj.optString("url", "")
                    val pTitle = obj.optString("title", "Kaynak")
                    val pQuality = obj.optString("quality", "")
                    if (pUrl.isNotBlank()) {
                        links.add(pUrl to "$pTitle $pQuality".trim())
                    }
                }
            } catch (e: Exception) {
                Log.e("DiziFilmizle", "parts parse hatasi: ${e.message}")
            }
        }

        val data = com.lagradost.cloudstream3.mvvm.safeApiCall {
            links // sadece URL listesi
        }

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
        }
    }

    private suspend fun loadSeries(url: String, slug: String): LoadResponse? {
        val doc = app.get(url).document
        val html = doc.html()

        // Series bilgileri — search API'sinden al
        val searchResp = app.get("$mainUrl/api/search?q=$slug").text
        val searchParsed = try { mapper.readValue<SearchResponse2>(searchResp) } catch (e: Exception) { null }
        val matched = searchParsed?.results?.firstOrNull { it.slug == slug }

        val title = matched?.title ?: doc.selectFirst("h1")?.text() ?: slug
        val poster = matched?.posterUrl
        val year = matched?.year
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")

        // series_id'yi HTML'den çek
       val seriesIdRegex = Regex("""series_id\D*?(\d+)""")
        val seriesId = seriesIdRegex.find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?: run {
                // Alternatif: HTML içinde id'yi bul
                Regex(""""id":(\d+),[^}]*"slug":"$slug"""").find(html)?.groupValues?.get(1)?.toLongOrNull()
            }

        Log.d("DiziFilmizle", "series_id: $seriesId")

        val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()

        if (seriesId != null) {
            val epResp = app.get("$mainUrl/api/episodes?series_id=$seriesId").text
            try {
                val epParsed = mapper.readValue<EpisodesResponse>(epResp)
                val grouped = epParsed.data?.episodes?.groupBy { it.seasonId ?: 0L } ?: emptyMap()
                // Her sezon için kaç bölüm var sayalım
                val seasonMap = mutableMapOf<Long, Int>()
                epParsed.data?.episodes?.forEach { ep ->
                    val sid = ep.seasonId ?: 0L
                    seasonMap[sid] = (seasonMap[sid] ?: 0) + 1
                }
                Log.d("DiziFilmizle", "seasons: ${seasonMap.keys}")

                // Sezon numaralarını belirle (kaç sezon var?)
                val seasonNumbers = epParsed.data?.episodes?.mapNotNull { it.seasonNumber }?.distinct()?.sorted()
                    ?: seasonMap.keys.mapIndexed { idx, _ -> idx + 1 }

                epParsed.data?.episodes?.forEach { ep ->
                    // Sezon numarasını belirle
                    val sNum = ep.seasonNumber ?: 1
                    val eNum = ep.episodeNumber ?: 0
                    val epTitle = ep.titleTr ?: ep.title ?: "$sNum. Sezon $eNum. Bölüm"
                    val epUrl = "$mainUrl/dizi/$slug/${sNum}-sezon-${eNum}-bolum"

                    episodes.add(
                        newEpisode(epUrl) {
                            this.name = epTitle
                            this.season = sNum
                            this.episode = eNum
                            this.posterUrl = ep.seriesPosterUrl ?: poster
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("DiziFilmizle", "episodes parse hatasi: ${e.message}")
            }
        }

        return newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DiziFilmizle", "loadLinks: $data")

        // Veri: /film/{slug} (film) veya /dizi/{slug}/{season}-sezon-{ep}-bolum (bölüm)
        val doc = app.get(data).document
        val html = doc.html()

        // HTML içinde parts\"...url\":\"...\" var mı?
       val partsRegex = Regex("""parts.{0,3}\[([\s\S]*?)\]""")
        val partsMatch = partsRegex.find(html)

        if (partsMatch != null) {
            val partsStr = "[${partsMatch.groupValues[1]}]"
                .replace("\\\"", "\"")
                .replace("\\/", "/")
            try {
                val arr = JSONArray(partsStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pUrl = obj.optString("url", "")
                    if (pUrl.isNotBlank()) {
                        // Vidmixi extractor'a gönder
                        loadExtractor(pUrl, data, subtitleCallback, callback)
                    }
                }
                return true
            } catch (e: Exception) {
                Log.e("DiziFilmizle", "loadLinks parts parse: ${e.message}")
            }
        }

        // Alternatif: HTML'de iframe/embed URL'lerini bul
        val embeds = Regex("""https?://[^"'\s<>]+(?:embed|player)[^"'\s<>]*""").findAll(html)
        var count = 0
        embeds.forEach { m ->
            val eu = m.value
            if (eu.contains("vidmixi.com") || eu.contains("vidload") || eu.contains("sibnet")) {
                loadExtractor(eu, data, subtitleCallback, callback)
                count++
            }
        }
        return count > 0
    }

    private fun MovieItem.toSearchResponse(isSeries: Boolean): SearchResponse {
        val type = if (isSeries) TvType.TvSeries else TvType.Movie
        val response = if (isSeries) {
            newTvSeriesSearchResponse(this.title ?: "?", "$mainUrl/dizi/${this.slug}", type)
        } else {
            newMovieSearchResponse(this.title ?: "?", "$mainUrl/film/${this.slug}", type)
        }
        response.posterUrl = this.posterUrl
        (response as? com.lagradost.cloudstream3.MovieSearchResponse)?.year = this.year
    (response as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year = this.year
        return response
    }

    private fun SeriesItem.toSearchResponse(isSeries: Boolean): SearchResponse {
        val response = newTvSeriesSearchResponse(this.title ?: "?", "$mainUrl/dizi/${this.slug}", TvType.TvSeries)
        response.posterUrl = this.posterUrl
        (response as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year = this.startYear
        return response
    }

    private fun SearchItem.toSearchResponse(): SearchResponse {
        val response = newMovieSearchResponse(
            this.title ?: "?",
            "$mainUrl/film/${this.slug}",
            TvType.Movie
        )
        response.posterUrl = this.posterUrl
        (response as? com.lagradost.cloudstream3.MovieSearchResponse)?.year = this.year
    (response as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year = this.year
        return response
    }
}

// Alias: SearchResponse zaten Cloudstream'de var, bizim DTO'yu ayırt etmek için isim
typealias SearchResponse2 = com.safakstream.SearchResponse
