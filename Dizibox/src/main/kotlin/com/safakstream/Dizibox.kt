package com.safakstream

import com.lagradost.cloudstream3.Episode
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
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.nodes.Document

// --- REKLAM İÇİN EKLENEN KÜTÜPHANELER ---
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.cloudstream3.CommonActivity

class Dizibox : MainAPI() {
    override var mainUrl              = "https://ddizibox.com"
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val defaultPlayerBase = "https://pilavyerplay.top"

    // ========================================================================
    // MODERN REKLAM / DESTEK FONKSİYONU
    // ========================================================================
    fun showSupportDialog(context: Context) {
        val prefs = context.getSharedPreferences("SafakStreamOrtakHafiza", Context.MODE_PRIVATE)
        val lastShown = prefs.getLong("son_reklam_zamani", 0)
        val currentTime = System.currentTimeMillis()

        // 60 Dakika = 60 * 60 * 1000 = 3.600.000 ms
        if (currentTime - lastShown > 3600000L) { 
            
            val dialog = Dialog(context)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val mainLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(70, 70, 70, 70)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#212429"))
                    cornerRadius = 60f
                    setStroke(2, Color.parseColor("#33363D"))
                }
                background = bg
            }

            val title = TextView(context).apply {
                text = "SafakStream'i Destekle ❤️"
                setTextColor(Color.WHITE)
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            }
            mainLayout.addView(title)

            val subtitle = TextView(context).apply {
                text = "Tamamen Ücretsiz Eklentiler"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 15)
            }
            mainLayout.addView(subtitle)

            val desc = TextView(context).apply {
                text = "Bu eklentiler sizin için tamamen ücretsizdir ve her zaman öyle kalacaktır.\n\nEmeğime destek olmak için bir reklam izleyebilirsiniz. Her küçük katkı benim için çok değerlidir, teşekkürler!"
                setTextColor(Color.parseColor("#C5C5C5"))
                textSize = 14f
                setLineSpacing(15f, 1.1f)
                setPadding(0, 0, 0, 60)
            }
            mainLayout.addView(desc)

            val buttonLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val btnLater = TextView(context).apply {
                text = "DAHA SONRA"
                setTextColor(Color.parseColor("#7A9CFF"))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(30, 30, 30, 30)
                val bg = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(3, Color.parseColor("#5C85FF"))
                    cornerRadius = 100f
                }
                background = bg
                setOnClickListener { dialog.dismiss() }
            }
            
            val laterParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            laterParams.marginEnd = 20
            buttonLayout.addView(btnLater, laterParams)

            val btnWatchAd = TextView(context).apply {
                text = "REKLAM İZLE\nVE DESTEK OL"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(30, 20, 30, 20)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#22449B"))
                    cornerRadius = 100f
                }
                background = bg
                setOnClickListener {
                    val adUrl = "https://link-center.net/10612612/WsR8DGfNtSq0"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adUrl))
                    context.startActivity(intent)
                    dialog.dismiss()
                }
            }
            
            val adParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            buttonLayout.addView(btnWatchAd, adParams)

            mainLayout.addView(buttonLayout)

            dialog.setContentView(mainLayout)
            dialog.show()
            
            prefs.edit().putLong("son_reklam_zamani", currentTime).apply()
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/rss.xml" to "Son Eklenenler"
    )

    // ============ ANA SAYFA (RSS) ============
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        
        // --- REKLAM TETİKLEYİCİSİ ---
        CommonActivity.activity?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                showSupportDialog(ctx)
            }
        }
        // ---------------------------

        if (page > 1) return newHomePageResponse(request.name, emptyList())
        val body = app.get(request.data, headers = mapOf("User-Agent" to userAgent)).text
        val items = parseRss(body)
        return newHomePageResponse(request.name, items)
    }

    private fun parseRss(xml: String): List<SearchResponse> {
        val doc = org.jsoup.Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
        val results = mutableListOf<SearchResponse>()
        for (item in doc.select("item")) {
            val title = item.selectFirst("title")?.text()?.trim() ?: continue
            val link  = item.selectFirst("link")?.text()?.trim() ?: continue
            if (link.isBlank()) continue
            val cleanTitle = title.removeSuffix(" izle").trim()
            val poster = item.selectFirst("media|content")?.attr("url")
                ?: item.selectFirst("enclosure")?.attr("url") ?: ""
            val isMovie = link.contains("/film/")
            val response = if (isMovie) {
                newMovieSearchResponse(cleanTitle, link, TvType.Movie)
            } else {
                newTvSeriesSearchResponse(cleanTitle, link, TvType.TvSeries)
            }
            response.posterUrl = poster
            if (results.none { it.url == link }) results.add(response)
        }
        return results
    }

    // ============ ARAMA ============
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/ara?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return parseSearchResults(doc)
    }

    private fun parseSearchResults(doc: Document): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        for (img in doc.select("img[alt]")) {
            val alt = img.attr("alt")
            if (!alt.contains(" izle")) continue
            val a = img.closest("a") ?: continue
            val href = a.attr("href")
            if (href.isBlank()) continue
            if (!href.contains("/dizi/") && !href.contains("/film/")) continue
            val cleanTitle = alt.removeSuffix(" izle").trim()
            val poster = img.attr("src")
            val isMovie = href.contains("/film/")
            val response = if (isMovie) {
                newMovieSearchResponse(cleanTitle, href, TvType.Movie)
            } else {
                newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries)
            }
            response.posterUrl = poster
            if (results.none { it.url == href }) results.add(response)
        }
        return results
    }

    // ============ DETAY ============
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val isMovie = url.contains("/film/")

        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: ""
        val title = ogTitle.substringBefore(" izle").substringBefore(" |").trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val description = doc.selectFirst("p.mt-5.max-w-3xl")?.text()?.trim()
            ?: doc.selectFirst("p.mt-5.max-w-2xl")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val year = Regex("""\((\d{4})\)""").find(ogTitle)?.groupValues?.get(1)?.toIntOrNull()

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            val episodeList = mutableListOf<Episode>()
            val seasonLinks = doc.select("a[href*='?sezon=']").map { it.attr("href") }.distinct()

            if (seasonLinks.isEmpty()) {
                episodeList.addAll(parseEpisodes(doc))
            } else {
                for (sLink in seasonLinks) {
                    val fullUrl = if (sLink.startsWith("http")) sLink else mainUrl + sLink
                    val seasonDoc = app.get(fullUrl, headers = mapOf("User-Agent" to userAgent)).document
                    episodeList.addAll(parseEpisodes(seasonDoc))
                }
            }

            val distinctEpisodes = episodeList.distinctBy { it.data }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, distinctEpisodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    private fun parseEpisodes(doc: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        for (a in doc.select("a[href*='/sezon-'][href*='/bolum-']")) {
            val href = a.attr("href")
            if (href.isBlank()) continue
            val seasonNum = Regex("""/sezon-(\d+)/""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            val episodeNum = Regex("""/bolum-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            val name = a.selectFirst("p")?.text()?.trim()
                ?: "Sezon ${seasonNum ?: "?"} Bölüm ${episodeNum ?: "?"}"
            episodes.add(
                newEpisode(href) {
                    this.name = name
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            )
        }
        return episodes
    }

    // ============ VIDEO LINKLERI ============
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("User-Agent" to userAgent)).document

        val pvId = doc.selectFirst("div[data-pv]")?.attr("data-pv")
            ?: doc.select("div[data-pv]").firstOrNull()?.attr("data-pv")
            ?: return false

        val scriptSrc = doc.select("script[src*=pilavyerplay]").firstOrNull()?.attr("src")
        val dynamicBase = if (scriptSrc != null && scriptSrc.contains("/assets/js/")) {
            scriptSrc.substringBefore("/assets/js/")
        } else {
            defaultPlayerBase
        }

        val playerUrl = "$dynamicBase/assets/js/s.php?s=$pvId"
        val playerHtml = app.get(
            playerUrl,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to data,
                "Origin" to mainUrl
            )
        ).text

        val playerJsonMatch = Regex("""window\.__PLAYER__\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            .find(playerHtml)?.groupValues?.get(1) ?: return false

        val playerObj = JSONObject(playerJsonMatch)
        val streamUrl = playerObj.optString("stream", "")
        if (streamUrl.isBlank()) return false

        val subsArray = playerObj.optJSONArray("subs")
        if (subsArray != null) {
            for (i in 0 until subsArray.length()) {
                val sub = subsArray.getJSONObject(i)
                val subUrl = sub.optString("src", "")
                val subLang = sub.optString("lang", "tr")
                if (subUrl.isNotBlank()) {
                    subtitleCallback(SubtitleFile(subLang, subUrl))
                }
            }
        }

        val m3u8Response = app.get(
            streamUrl,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to playerUrl,
                "Origin" to dynamicBase
            )
        ).text

        if (!m3u8Response.contains("#EXTM3U")) {
            if (streamUrl.contains(".mp4")) {
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = "DiziBox",
                        url = streamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = playerUrl
                        this.quality = 1080
                    }
                )
                return true
            }
            return false
        }

        callback(
            newExtractorLink(
                source = this.name,
                name = "DiziBox",
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = playerUrl
                this.quality = 1080
            }
        )

        return true
    }
}
