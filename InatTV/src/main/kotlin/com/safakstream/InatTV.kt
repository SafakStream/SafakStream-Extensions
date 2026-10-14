// SafakStream-Extensions/InatTV/src/main/kotlin/com/safakstream/InatTV.kt

package com.safakstream

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class InatTV : MainAPI() {
    override var mainUrl              = "https://www.inattvizle494.top"
    override var name                 = "InatTV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
        "Accept"     to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer"    to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "canli-maclar"  to "Canli Maclar",
        "tv-kanallari"  to "7/24 TV Kanallari"
    )

    private var cachedMatches: List<MatchItem> = emptyList()
    private var cachedChannels: List<MatchItem> = emptyList()

    private data class MatchItem(
        val name: String,
        val title: String,
        val href: String,
        val searchKey: String,
        val category: String,
        val sources: List<Source>,
        val homeLogo: String?,
        val awayLogo: String?,
        val time: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Source(
        @JsonProperty("ad")   val quality: String? = null,
        @JsonProperty("tip")  val type: String?    = null,
        @JsonProperty("link") val link: String?    = null
    )

    private val mapper by lazy {
        ObjectMapper().registerModule(KotlinModule.Builder().build()).also {
            it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    private suspend fun fetchAllItems(forceRefresh: Boolean = false): Pair<List<MatchItem>, List<MatchItem>> {
        if (!forceRefresh && (cachedMatches.isNotEmpty() || cachedChannels.isNotEmpty())) {
            return cachedMatches to cachedChannels
        }

        val document = app.get(mainUrl, headers = headers).document

        val matches = document.select("ul.match-list li[data-source]").mapNotNull { it.toMatchItem() }
        val channels = document.select("#tv-listesi ul.match-list li[data-target]").mapNotNull { it.toMatchItem() }

        cachedMatches = matches
        cachedChannels = channels

        Log.d("InatTV", "Mac sayisi: ${matches.size}, Kanal sayisi: ${channels.size}")
        return matches to channels
    }

    private fun Element.toMatchItem(): MatchItem? {
        val rawName = attr("data-name")
        val name = if (rawName.isNotBlank()) {
            Jsoup.parse(rawName).text()
        } else {
            attr("data-title").substringBefore(" Canli Izle").trim()
        }

        if (name.isBlank()) return null

        val href = fixUrl(attr("data-url").ifBlank { "#" })
        val title = attr("data-title")
        val searchKey = attr("data-search").ifBlank { name }
        val category = attr("data-category").ifBlank { "genel" }

        // IKI YAPIYI DESTEKLE:
        // 1) Maclar: data-source='[{"ad":"HD","tip":"viptv","link":"101303"}]'
        // 2) TV kanallari: data-target="m3u8" data-source="https://..."
        val sourcesRaw = attr("data-source")
        val directTarget = attr("data-target")

        val sources: List<Source> = when {
            sourcesRaw.startsWith("[") -> {
                try {
                    mapper.readValue(sourcesRaw)
                } catch (e: Exception) {
                    Log.e("InatTV", "Source parse hatasi: ${e.message}")
                    emptyList()
                }
            }
            directTarget.isNotBlank() && sourcesRaw.isNotBlank() -> {
                listOf(
                    Source(
                        quality = "HD",
                        type = directTarget,
                        link = sourcesRaw
                    )
                )
            }
            else -> emptyList()
        }

        // Logo: maclarda data-home_logo, TV kanallarinda img src
        val homeLogo = attr("data-home_logo").ifBlank {
            selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else fixUrl(it)
            }
        }
        val awayLogo = attr("data-away_logo").ifBlank { null }
        val time = selectFirst("span.saat")?.text()

        return MatchItem(name, title, href, searchKey, category, sources, homeLogo, awayLogo, time)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (matches, channels) = fetchAllItems()
        val items = when (request.data) {
            "canli-maclar" -> matches
            "tv-kanallari" -> channels
            else           -> emptyList()
        }

        val results = items.map { it.toSearchResponse() }
        return newHomePageResponse(request.name, results)
    }

    private fun MatchItem.toSearchResponse(): SearchResponse {
        val poster = homeLogo
        val dataPayload = buildString {
            append("name=").append(name.replace("|", " "))
            append("||sources=").append(mapper.writeValueAsString(sources))
        }
        return newMovieSearchResponse(name, dataPayload, TvType.Live) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val (matches, channels) = fetchAllItems()
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        val all = matches + channels
        return all.filter { item ->
            item.searchKey.lowercase().contains(q) || item.name.lowercase().contains(q)
        }.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val name = url.substringAfter("name=", "").substringBefore("||sources=").trim().ifBlank { "InatTV" }
        val sourcesJson = url.substringAfter("||sources=", "").trim()

        val sources: List<Source> = try {
            if (sourcesJson.isBlank()) emptyList()
            else mapper.readValue(sourcesJson)
        } catch (e: Exception) {
            Log.e("InatTV", "load source parse hatasi: ${e.message}")
            emptyList()
        }

        val availableSources = sources.filter { it.type == "m3u8" || it.type == "frame" }
        val unavailableCount = sources.size - availableSources.size

        val description = buildString {
            append("Toplam kaynak: ${sources.size}\n")
            append("Oynatilabilir kaynak: ${availableSources.size}\n")
            if (unavailableCount > 0) {
                append("Desteklenmeyen kaynak: $unavailableCount (vip/viptv)")
            }
        }

        return newMovieLoadResponse(name, url, TvType.Live, url) {
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val sourcesJson = data.substringAfter("||sources=", "").trim()
        val sources: List<Source> = try {
            if (sourcesJson.isBlank()) emptyList()
            else mapper.readValue(sourcesJson)
        } catch (e: Exception) {
            Log.e("InatTV", "loadLinks parse hatasi: ${e.message}")
            return false
        }

        var anyHandled = false

        for (source in sources) {
            val link = source.link ?: continue
            val type = source.type ?: continue
            val quality = source.quality ?: "Bilinmiyor"

            when (type.lowercase()) {
                "m3u8" -> {
                    val fixed = if (link.startsWith("http")) link else fixUrl(link)
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name   = "${this.name} [$quality]",
                            url    = fixed,
                            type   = INFER_TYPE
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = when (quality.uppercase()) {
                                "HD", "FHD" -> Qualities.P1080.value
                                "SD"        -> Qualities.P480.value
                                else        -> Qualities.Unknown.value
                            }
                        }
                    )
                    anyHandled = true
                }

                "frame" -> {
                    val fixed = if (link.startsWith("http")) link else fixUrl(link)
                    val handled = loadExtractor(fixed, "$mainUrl/", subtitleCallback, callback)
                    if (handled) anyHandled = true
                }

                else -> {
                    Log.d("InatTV", "Desteklenmeyen kaynak tipi: $type ($link)")
                }
            }
        }

        return anyHandled
    }
}
