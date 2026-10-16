// SafakStream-Extensions/TrGoals/src/main/kotlin/com/safakstream/TrGoals.kt

package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TrGoals : MainAPI() {
    override var mainUrl = "https://www.trgoals183.top"
    override var name = "TrGoals"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Live,
        TvType.Others,
    )

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private fun getHeaders(referer: String = "$mainUrl/") = mapOf(
        "User-Agent" to USER_AGENT,
        "Referer" to referer,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
    )

    override val mainPage = mainPageOf(
        "" to "Canlı Maçlar",
        "kanallar" to "7/24 TV Kanalları",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, headers = getHeaders()).document
        val items = when (request.name) {
            "7/24 TV Kanalları" -> parseChannels(document)
            else -> parseMatches(document)
        }
        return newHomePageResponse(
            listOf(HomePageList(request.name, items)),
            hasNext = false
        )
    }

    // -------------------------------------------------------------------
    // MAÇ LİSTESİ PARSE
    // -------------------------------------------------------------------
    private fun parseMatches(document: Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        document.select("#mac-listesi ul.match-list li").forEach { li ->
            val item = parseMatchItem(li) ?: return@forEach
            out.add(item)
        }
        return out
    }

    // -------------------------------------------------------------------
    // KANAL LİSTESİ PARSE
    // -------------------------------------------------------------------
    private fun parseChannels(document: Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        document.select("#tv-listesi ul.match-list li").forEach { li ->
            val item = parseChannelItem(li) ?: return@forEach
            out.add(item)
        }
        return out
    }

    // -------------------------------------------------------------------
    // TEK BİR MAÇ <li> PARSE
    // -------------------------------------------------------------------
    private fun parseMatchItem(li: Element): SearchResponse? {
        val dataUrl = li.attr("data-url").trim()
        if (dataUrl.isEmpty()) return null

        val home = li.selectFirst("span.home")?.text()?.trim()
        val away = li.selectFirst("span.away")?.text()?.trim()
        val time = li.selectFirst("span.saat")?.text()?.trim()

        val title = if (!home.isNullOrEmpty() && !away.isNullOrEmpty()) {
            buildString {
                append(home)
                append(" - ")
                append(away)
                if (!time.isNullOrEmpty()) {
                    append(" (")
                    append(time)
                    append(")")
                }
            }
        } else {
            li.attr("data-name").trim().takeIf { it.isNotEmpty() } ?: return null
        }

        val poster = li.selectFirst("img.maxw40")?.attr("src")?.takeIf { it.isNotEmpty() }

        return newMovieSearchResponse(
            name = title,
            url = "trgoals://" + Base64.encodeToString(dataUrl.toByteArray(), Base64.NO_WRAP),
            type = TvType.Live,
        ) {
            this.posterUrl = poster
        }
    }

    // -------------------------------------------------------------------
    // TEK BİR KANAL <li> PARSE
    // -------------------------------------------------------------------
    private fun parseChannelItem(li: Element): SearchResponse? {
        val dataUrl = li.attr("data-url").trim()
        if (dataUrl.isEmpty()) return null

        val name = li.attr("data-name").trim().takeIf { it.isNotEmpty() } ?: return null
        val poster = li.selectFirst("img")?.attr("src")?.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("http")) it else "$mainUrl$it"
        }

        return newMovieSearchResponse(
            name = name,
            url = "trgoals://" + Base64.encodeToString(dataUrl.toByteArray(), Base64.NO_WRAP),
            type = TvType.Live,
        ) {
            this.posterUrl = poster
        }
    }

    // -------------------------------------------------------------------
    // ARAMA
    // -------------------------------------------------------------------
    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val document = app.get(mainUrl, headers = getHeaders()).document
        val q = query.lowercase()

        val matches = parseMatches(document).filter {
            it.name.lowercase().contains(q)
        }
        val channels = parseChannels(document).filter {
            it.name.lowercase().contains(q)
        }
        return (matches + channels).distinctBy { it.url }
    }

    // -------------------------------------------------------------------
    // DETAY (load)
    // -------------------------------------------------------------------
    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = try { String(android.util.Base64.decode(url.removePrefix("trgoals://"), android.util.Base64.NO_WRAP)) } catch (e: Exception) { url }
        val document = app.get(mainUrl, headers = getHeaders()).document
        val decoded = resolveDataByHash(document, cleanUrl)
            ?: throw ErrorLoadingException("Kaynak bulunamadı: $url")

        val title = decoded.optString("name", "TrGoals")
        val poster = decoded.optString("poster").takeIf { it.isNotEmpty() }
        val sourcesJson = decoded.optString("source", "[]")

        return newLiveStreamLoadResponse(
            name = title,
            url = cleanUrl,
            dataUrl = sourcesJson,
        ) {
            this.posterUrl = poster
        }
    }

    // -------------------------------------------------------------------
    // HASH'ten maç/kanal datasını çöz
    // -------------------------------------------------------------------
    private fun resolveDataByHash(document: Document, hashUrl: String): JSONObject? {
        val selector = "li[data-url='$hashUrl']"
        val li = document.selectFirst(selector) ?: return null

        val obj = JSONObject()
        obj.put("name", li.attr("data-name").trim().ifEmpty { li.attr("data-title").trim() })
        obj.put("source", li.attr("data-source"))
        obj.put("target", li.attr("data-target"))
        obj.put("category", li.attr("data-category"))

        val poster = li.selectFirst("img.maxw40, img")?.attr("src")?.takeIf { it.isNotEmpty() }
        obj.put("poster", poster ?: "")

        return obj
    }

    // -------------------------------------------------------------------
    // STREAM LİNKLERİNİ ÇÖZ (loadLinks) — suspend versiyon
    // -------------------------------------------------------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var found = false
        try {
            val arr = JSONArray(data)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tip = obj.optString("tip", "").lowercase()
                val link = obj.optString("link", "").trim()
                val ad = obj.optString("ad", "HD")
                if (link.isEmpty()) continue

                when (tip) {
                    "frame" -> {
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$ad (iframe)",
                                url = link,
                                type = ExtractorLinkType.VIDEO,
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }

                    "m3u8" -> {
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$ad (HLS)",
                                url = link,
                                type = ExtractorLinkType.M3U8,
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }

                    "viptv" -> {
                        val streamUrl = resolveVipTv(link)
                        if (!streamUrl.isNullOrEmpty()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$ad (VIP)",
                                    url = streamUrl,
                                    type = ExtractorLinkType.M3U8,
                                ) {
                                    this.referer = mainUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            found = true
                        }
                    }

                    "vip" -> {
                        val streamUrl = resolveVip(link)
                        if (!streamUrl.isNullOrEmpty()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$ad (VIP-id)",
                                    url = streamUrl,
                                    type = ExtractorLinkType.M3U8,
                                ) {
                                    this.referer = mainUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            found = true
                        }
                    }

                    "preload" -> {
                        // Yayın henüz eklenmedi, atla
                    }

                    else -> {
                        if (link.startsWith("http")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "$ad ($tip)",
                                    url = link,
                                    type = ExtractorLinkType.VIDEO,
                                ) {
                                    this.referer = mainUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            found = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // sessizce geç
        }
        return found
    }

    // -------------------------------------------------------------------
    // auth.php üzerinden VIP TV URL'ini çöz
    // -------------------------------------------------------------------
    private suspend fun resolveVipTv(channel: String): String? {
        return try {
            val response = app.post(
                url = "$mainUrl/auth.php",
                data = mapOf("channel" to channel),
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl,
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "X-Requested-With" to "XMLHttpRequest",
                ),
            )
            val json = JSONObject(response.text)
            json.optString("URL").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------
    // auth.php üzerinden VIP (id) URL'ini çöz
    // -------------------------------------------------------------------
    private suspend fun resolveVip(id: String): String? {
        return try {
            val response = app.post(
                url = "$mainUrl/auth.php",
                data = mapOf("id" to id),
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl,
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "X-Requested-With" to "XMLHttpRequest",
                ),
            )
            val json = JSONObject(response.text)
            json.optString("URL").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
