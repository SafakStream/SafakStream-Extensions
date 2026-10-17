package com.safakstream.mahsunsports

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import java.net.URLEncoder

class MahsunSportsProvider : MainAPI() {
    override var mainUrl = "https://mahsunsports55.xyz"
    override var name = "MahsunSports"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    private val baseUrls = listOf(
        "https://andro.evrenesoglu155.click/checklist/",
        "https://andro.evrenesoglu153.click/checklist/",
        "https://andro.evrenesoglu151.click/checklist/",
        "https://andro.evrenesoglu141.click/checklist/"
    )

    private val scriptUrl = "https://chatang0.com/script4.js"

    // Maçlar ve kanallar için varsayılan poster
    private val defaultPoster = "https://mahsunsports55.xyz/favicon.ico"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val script = app.get(scriptUrl).text
        val home = ArrayList<HomePageList>()

        val karsilasmalar = extractJsonArray(script, "karsilasmalar")
        if (karsilasmalar != null) {
            val liveMatches = ArrayList<SearchResponse>()
            for (i in 0 until karsilasmalar.length()) {
                val obj = karsilasmalar.getJSONObject(i)
                if (!obj.optBoolean("live", false)) continue

                val title = obj.optString("title")
                val league = obj.optString("league")
                val time = obj.optString("time")
                val url = obj.optString("url")
                val id = extractId(url) ?: continue

                val displayTitle = "🔴 $title ($time - $league)"
                liveMatches.add(
                    newLiveSearchResponse(
                        name = displayTitle,
                        url = buildLoadUrl(id, displayTitle),
                        type = TvType.Live
                    )
                )
            }
            if (liveMatches.isNotEmpty()) {
                home.add(HomePageList("🔴 Canlı Maçlar", liveMatches))
            }
        }

        addSportSection(home, script, "futbolMatches", "⚽ Futbol")
        addSportSection(home, script, "basketbolMatches", "🏀 Basketbol")
        addSportSection(home, script, "voleybolMatches", "🏐 Voleybol")
        addSportSection(home, script, "tenisMatches", "🎾 Tenis")

        val channelsArr = extractJsonArray(script, "channels")
        if (channelsArr != null) {
            val channelList = ArrayList<SearchResponse>()
            for (i in 0 until channelsArr.length()) {
                val obj = channelsArr.getJSONObject(i)
                val title = obj.optString("title")
                val url = obj.optString("url")
                val id = extractId(url) ?: continue
                val displayTitle = "📺 $title"
                channelList.add(
                    newLiveSearchResponse(
                        name = displayTitle,
                        url = buildLoadUrl(id, displayTitle),
                        type = TvType.Live
                    )
                )
            }
            if (channelList.isNotEmpty()) {
                home.add(HomePageList("📺 7/24 Spor Kanalları", channelList))
            }
        }

        return newHomePageResponse(home)
    }

    private fun addSportSection(
        home: ArrayList<HomePageList>,
        script: String,
        arrayName: String,
        sectionTitle: String
    ) {
        val arr = extractJsonArray(script, arrayName) ?: return
        val list = ArrayList<SearchResponse>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val title = obj.optString("title")
            val league = obj.optString("league")
            val time = obj.optString("time")
            val url = obj.optString("url")
            val isLive = obj.optBoolean("live", false)
            val id = extractId(url) ?: continue

            val prefix = if (isLive) "🔴 " else ""
            val displayTitle = "$prefix$title ($time - $league)"
            list.add(
                newLiveSearchResponse(
                    name = displayTitle,
                    url = buildLoadUrl(id, displayTitle),
                    type = TvType.Live
                )
            )
        }
        if (list.isNotEmpty()) {
            home.add(HomePageList(sectionTitle, list))
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val script = app.get(scriptUrl).text
        val results = ArrayList<SearchResponse>()
        val lowerQuery = query.lowercase()

        val arrays = listOf("karsilasmalar", "channels", "futbolMatches", "basketbolMatches", "voleybolMatches", "tenisMatches")
        for (arrName in arrays) {
            val arr = extractJsonArray(script, arrName) ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("title")
                val league = obj.optString("league", "")
                val url = obj.optString("url")
                val id = extractId(url) ?: continue

                if (title.lowercase().contains(lowerQuery) || league.lowercase().contains(lowerQuery)) {
                    val displayTitle = if (league.isNotEmpty()) "$title - $league" else title
                    results.add(
                        newLiveSearchResponse(
                            name = displayTitle,
                            url = buildLoadUrl(id, displayTitle),
                            type = TvType.Live
                        )
                    )
                }
            }
        }
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfter("id=", "").substringBefore("&").ifBlank { "Unknown" }
        val rawTitle = url.substringAfter("title=", "").substringBefore("&").ifBlank { null }
        val displayTitle = if (rawTitle != null) {
            try { java.net.URLDecoder.decode(rawTitle, "UTF-8") } catch (e: Exception) { rawTitle }
        } else {
            "MahsunSports: $id"
        }

        return newMovieLoadResponse(
            name = displayTitle,
            url = url,
            dataUrl = id,
            type = TvType.Live
        ) {
            this.posterUrl = defaultPoster
            this.plot = "Canlı yayın - MahsunSports"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data
        if (id.isBlank()) return false

        val streamFile = getStreamFile(id)

        for (base in baseUrls) {
            val streamUrl = base + streamFile
            try {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "MahsunSports - $id",
                        url = streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P720.value
                    }
                )
                return true
            } catch (e: Exception) {
                continue
            }
        }
        return false
    }

    private fun buildLoadUrl(id: String, title: String): String {
        val encoded = try { URLEncoder.encode(title, "UTF-8") } catch (e: Exception) { title }
        return "$mainUrl/event.html?id=$id&title=$encoded"
    }

    private fun getStreamFile(id: String): String {
        return when {
            id == "androstreamlivebs1" || id == "facebooklivebs1" -> "androstreamlivebs1.m3u8"
            id.startsWith("facebooklive") -> "$id.m3u8"
            id.startsWith("androstreamlive") -> "$id.m3u8"
            else -> "$id.m3u8"
        }
    }

    private fun extractId(url: String): String? {
        if (!url.contains("id=")) return null
        return url.substringAfter("id=").substringBefore("&").substringBefore("#").takeIf { it.isNotBlank() }
    }

    private fun extractJsonArray(script: String, arrayName: String): JSONArray? {
        return try {
            val startPattern = "const $arrayName = ["
            val startIdx = script.indexOf(startPattern)
            if (startIdx == -1) return null

            val arrayStart = startIdx + startPattern.length - 1
            var depth = 0
            var inString = false
            var escape = false
            var endIdx = -1

            for (i in arrayStart until script.length) {
                val c = script[i]
                if (escape) { escape = false; continue }
                if (c == '\\') { escape = true; continue }
                if (c == '"') { inString = !inString; continue }
                if (inString) continue
                when (c) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) { endIdx = i; break }
                    }
                }
            }

            if (endIdx == -1) return null
            JSONArray(script.substring(arrayStart, endIdx + 1))
        } catch (e: Exception) {
            null
        }
    }
}
