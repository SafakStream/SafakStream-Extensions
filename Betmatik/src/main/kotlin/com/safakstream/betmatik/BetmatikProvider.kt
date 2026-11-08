package com.safakstream.betmatik

import android.util.Log
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
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.cloudstream3.CommonActivity

class BetmatikProvider : MainAPI() {
    override var mainUrl = "https://betmatiktv156.com"
    override var name = "Betmatik"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    companion object {
        private const val TAG = "Betmatik"
        private const val API_BASE = "https://data-reality.com"
        private const val UA = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
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

            // Ana Arka Plan (Koyu Gri ve Yuvarlak Köşeler)
            val mainLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 60, 60, 60)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2C2E33")) // Koyu gri arka plan
                    cornerRadius = 50f // Yuvarlak köşeler
                }
                background = bg
            }

            val title = TextView(context).apply {
                text = "SafakStream'i Destekle ❤️"
                setTextColor(Color.WHITE)
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 30)
            }
            mainLayout.addView(title)

            val subtitle = TextView(context).apply {
                text = "Tamamen Ücretsiz Eklentiler"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            }
            mainLayout.addView(subtitle)

            val desc = TextView(context).apply {
                text = "Bu eklentiler sizin için tamamen ücretsizdir ve her zaman öyle kalacaktır.\n\nEmeğime destek olmak için bir reklam izleyebilirsiniz. Her küçük katkı benim için çok değerlidir, teşekkürler!"
                setTextColor(Color.parseColor("#B0B0B0")) // Gri renk metin
                textSize = 14f
                setLineSpacing(10f, 1f)
                setPadding(0, 0, 0, 50)
            }
            mainLayout.addView(desc)

            val buttonLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val btnLater = Button(context).apply {
                text = "DAHA SONRA"
                setTextColor(Color.parseColor("#5C85FF")) // Mavi yazı
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                val bg = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(3, Color.parseColor("#5C85FF")) // Mavi çerçeve
                    cornerRadius = 30f
                }
                background = bg
                setPadding(40, 20, 40, 20)
                
                setOnClickListener { dialog.dismiss() }
            }
            
            val laterParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            laterParams.marginEnd = 30
            buttonLayout.addView(btnLater, laterParams)

            val btnWatchAd = Button(context).apply {
                text = "REKLAM İZLE ▶"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2555FF")) // Parlak Mavi arka plan
                    cornerRadius = 30f
                }
                background = bg
                setPadding(40, 20, 40, 20)
                
                setOnClickListener {
                    val adUrl = "https://link-center.net/10612612/WsR8DGfNtSq0" // LİNVERTİSE LİNKİN
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adUrl))
                    context.startActivity(intent)
                    dialog.dismiss()
                }
            }
            buttonLayout.addView(btnWatchAd)

            mainLayout.addView(buttonLayout)

            dialog.setContentView(mainLayout)
            dialog.show()
            
            // Reklamın gösterildiği zamanı kaydet
            prefs.edit().putLong("son_reklam_zamani", currentTime).apply()
        }
    }

    private fun streamHeaders(): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-site"
    )

    private var cachedBaseUrl: String? = null

    override val mainPage = mainPageOf(
        "$API_BASE/matches.php" to "Canlı Maçlar",
        "$API_BASE/channels.php" to "7/24 Kanallar"
    )

    private suspend fun fetchBaseUrl(): String? {
        cachedBaseUrl?.let { return it }
        return try {
            val res = app.get("$API_BASE/domain.php", headers = mapOf("User-Agent" to UA))
            if (res.code != 200) {
                Log.e(TAG, "domain.php code=${res.code}")
                return null
            }
            val json = JSONObject(res.text)
            val base = json.optString("baseurl", "")
            if (base.isBlank()) return null
            cachedBaseUrl = base
            Log.d(TAG, "baseUrl=$base")
            base
        } catch (e: Exception) {
            Log.e(TAG, "domain.php hata: ${e.message}")
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        
        // --- REKLAM TETİKLEYİCİSİ ---
        CommonActivity.activity?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                showSupportDialog(ctx)
            }
        }

        val res = try {
            app.get(request.data, headers = mapOf("User-Agent" to UA))
        } catch (e: Exception) {
            Log.e(TAG, "getMainPage hata: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }

        if (res.code != 200) return newHomePageResponse(request.name, emptyList())

        val doc = Jsoup.parse(res.text)
        val items: List<SearchResponse> = doc.select("a.single-match").mapNotNull { el ->
            el.toSearchResponse()
        }

        Log.d(TAG, "getMainPage ${request.name}: ${items.size} item")
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = attr("href")
        if (!href.contains("channel?id=")) return null
        val channelId = href.substringAfter("channel?id=").substringBefore("&")
        if (channelId.isBlank()) return null

        val home = selectFirst(".home")?.text()?.trim().orEmpty()
        val away = selectFirst(".away")?.text()?.trim().orEmpty()
        val event = selectFirst(".event")?.text()?.trim().orEmpty()

        val title = when {
            home.isNotBlank() && away.isNotBlank() -> "$home - $away"
            home.isNotBlank() -> home
            event.isNotBlank() -> event
            else -> return null
        }

        val allImgs = select("img").mapNotNull { img ->
            val src = img.attr("src").trim()
            if (src.isBlank()) return@mapNotNull null
            when {
                src.startsWith("http://") || src.startsWith("https://") -> src
                src.startsWith("//") -> "https:$src"
                else -> runCatching { fixUrl(src) }.getOrNull()
            }
        }
        val poster = allImgs.firstOrNull { url ->
            !url.contains("img/tv.png", ignoreCase = true)
        } ?: allImgs.firstOrNull()

        val stableUrl = buildStableUrl(channelId, title)
        return newLiveSearchResponse(title, stableUrl, TvType.Live) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        val all = mutableListOf<SearchResponse>()
        for (endpoint in listOf("$API_BASE/matches.php", "$API_BASE/channels.php")) {
            try {
                val res = app.get(endpoint, headers = mapOf("User-Agent" to UA))
                if (res.code == 200) {
                    Jsoup.parse(res.text).select("a.single-match").forEach { el ->
                        val item = el.toSearchResponse()
                        if (item != null && item.name.contains(q, ignoreCase = true)) {
                            all.add(item)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return all.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val parsed = parseStableUrl(url) ?: return null
        return newLiveStreamLoadResponse(parsed.title, url, url) {
            this.plot = "Canlı yayın - Betmatik TV"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseStableUrl(data) ?: return false
        val base = fetchBaseUrl() ?: return false

        val streamUrl = "${base}${parsed.channelId}/mono.m3u8"
        Log.d(TAG, "streamUrl=$streamUrl")

        try {
            val check = app.get(streamUrl, headers = streamHeaders(), allowRedirects = true)
            val text = check.text.trimStart()
            if (check.code != 200 || !text.startsWith("#EXTM3U")) {
                Log.e(TAG, "m3u8 kontrol başarısız code=${check.code} ilk30=${text.take(30)}")
                return false
            }
            Log.d(TAG, "m3u8 OK, ilk satır=${text.lineSequence().firstOrNull()}")
        } catch (e: Exception) {
            Log.e(TAG, "m3u8 check hata: ${e.message}")
            return false
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = parsed.title,
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.headers = streamHeaders()
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    private fun buildStableUrl(channelId: String, title: String): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "$mainUrl/__betmatik__?id=${enc(channelId)}&title=${enc(title)}"
    }

    private fun parseStableUrl(url: String): ParsedUrl? {
        if (!url.contains("__betmatik__")) return null
        return try {
            val q = URI(url).rawQuery ?: return null
            val map = q.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to
                    URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
            val id = map["id"] ?: return null
            val title = map["title"] ?: id
            ParsedUrl(id, title)
        } catch (e: Exception) {
            null
        }
    }

    data class ParsedUrl(val channelId: String, val title: String)
}
