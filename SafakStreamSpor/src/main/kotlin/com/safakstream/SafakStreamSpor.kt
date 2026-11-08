// SafakStream-Extensions/BeyazElma/src/main/kotlin/com/safakstream/BeyazElma.kt

package com.safakstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

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

class SafakStreamSpor : MainAPI() {
    override var mainUrl = "https://beyazelma78.com"
    override var name = "SafakStream Spor"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Live,
        TvType.Others,
    )

    companion object {
        const val EMBED_URL = "https://embed.beyazelma78.com"
    }

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
        "" to "Canlı Maçlar",
        "kanallar" to "Canlı Kanallar",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        
        // --- REKLAM TETİKLEYİCİSİ ---
        CommonActivity.activity?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                showSupportDialog(ctx)
            }
        }
        // ---------------------------

        val url = if (request.data.isEmpty()) "$mainUrl/" else "$mainUrl/${request.data}"
        val document = app.get(url).document
        val items = when (request.data) {
            "kanallar" -> parseChannels(document)
            else -> parseMatches(document)
        }
        return newHomePageResponse(
            listOf(HomePageList(request.name, items)),
            hasNext = false
        )
    }

    private fun parseMatches(document: org.jsoup.nodes.Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        document.select("a[href^=/mac/]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isEmpty()) return@forEach

            val home = a.selectFirst(".site-fixture-side--home .site-fixture-name")?.text()?.trim()
            val away = a.selectFirst(".site-fixture-side--away .site-fixture-name")?.text()?.trim()

            val title = if (!home.isNullOrEmpty() && !away.isNullOrEmpty()) {
                "$home - $away"
            } else {
                a.selectFirst("p.text-xs")?.text()?.trim()
                    ?: a.text().trim().takeIf { it.isNotEmpty() }
                    ?: return@forEach
            }

            val poster = a.selectFirst("img.site-fixture-logo")?.attr("src")?.let {
                if (it.startsWith("http")) it else "$mainUrl$it"
            }

            out.add(
                newMovieSearchResponse(
                    name = title,
                    url = href,
                    type = TvType.Live,
                ) {
                    this.posterUrl = poster
                }
            )
        }
        return out.distinctBy { it.url }
    }

    private fun parseChannels(document: org.jsoup.nodes.Document): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        document.select("a.site-channel-row, a.site-channel-card, a[href^=/kanal/]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isEmpty() || !href.startsWith("/kanal/")) return@forEach

            val name = a.selectFirst(".site-channel-row-name")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: a.selectFirst("img")?.attr("alt")?.trim()
                    ?.removeSuffix(" logosu")
                    ?.takeIf { it.isNotEmpty() }
                ?: a.attr("title").trim().takeIf { it.isNotEmpty() }
                ?: return@forEach

            val poster = a.selectFirst("img")?.attr("src")?.let {
                if (it.startsWith("http")) it else "$mainUrl$it"
            }

            out.add(
                newMovieSearchResponse(
                    name = name,
                    url = href,
                    type = TvType.Live,
                ) {
                    this.posterUrl = poster
                }
            )
        }
        return out.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        // 1. Başlık ve Posteri almak için ORİJİNAL siteye git
        val document = app.get(url).document
        val rawTitle = document.selectFirst("title")?.text()?.trim() ?: "SafakStream Spor"
        val title = rawTitle.replace("| BeyazElma", "").replace("BeyazElma", "SafakStream Spor").trim()
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()

        // 2. Dünya simgesi için gitmesini istediğin link (İstersen Google yapabilirsin)
        val webUrl = "https://github.com/SafakStream/SafakStream-Extensions" 
        // val webUrl = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(title + " canlı izle", "UTF-8")

        // 3. Hem başlığı koru, hem videoyu oynat, hem de dünya simgesini webUrl'ye yönlendir.
        return newMovieLoadResponse(
            name = title,
            url = webUrl,        // Dünya simgesi burayı açar
            type = TvType.Live,
            dataUrl = url,       // Oynatıcı orijinal linki kullanır
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val q = query.trim()
        if (q.isEmpty()) return results

        try {
            val channelsDoc = app.get("$mainUrl/kanallar").document
            val channels = parseChannels(channelsDoc)
            results.addAll(channels.filter {
                it.name.contains(q, ignoreCase = true)
            })
        } catch (e: Exception) { }

        try {
            val matchesDoc = app.get("$mainUrl/").document
            val matches = parseMatches(matchesDoc)
            results.addAll(matches.filter {
                it.name.contains(q, ignoreCase = true)
            })
        } catch (e: Exception) { }

        return results.distinctBy { it.url }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val pageUrl = if (data.startsWith("http")) data else "$mainUrl$data"
        val document = app.get(pageUrl).document

        val embedPaths = Regex("""/api/embed\?u=[A-Za-z0-9_\-]+""")
            .findAll(document.html())
            .map { it.value }
            .distinct()
            .toList()

        if (embedPaths.isEmpty()) return false

        for (embedPath in embedPaths) {
            try {
                val embedFullUrl = if (embedPath.startsWith("http")) embedPath else "$mainUrl$embedPath"
                val embedHtml = app.get(embedFullUrl).text

                val slug = Regex(""""slug"\s*:\s*"([^"]+)"""")
                    .find(embedHtml)?.groupValues?.get(1)
                    ?: continue

                val apiUrl = "$EMBED_URL/api/play/$slug"
                val json = app.get(
                    apiUrl,
                    referer = "$EMBED_URL/",
                    headers = mapOf("Accept" to "application/json")
                ).text

                val obj = JSONObject(json)
                val sources = mutableListOf<Pair<String, String>>()

                if (obj.has("primary")) {
                    sources.add(obj.getString("primary") to "BeyazElma")
                }
                if (obj.has("fallbacks")) {
                    val arr = obj.getJSONArray("fallbacks")
                    for (i in 0 until arr.length()) {
                        sources.add(arr.getString(i) to "BeyazElma (yedek)")
                    }
                }

                for ((m3u8, label) in sources) {
                    if (m3u8.isBlank()) continue
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "SafakStream Spor",
                            url = m3u8,
                            type = ExtractorLinkType.M3U8,
                        ) {
                            this.referer = "$EMBED_URL/"
                            this.quality = Qualities.Unknown.value
                            this.headers = mapOf(
                                "Referer" to "$EMBED_URL/",
                                "Origin" to EMBED_URL,
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
                            )
                        }
                    )
                    found = true
                }
            } catch (e: Exception) {
                continue
            }
        }
        return found
    }
}
