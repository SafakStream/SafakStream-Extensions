package com.safakstream.inattv

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Inat TV domain çözücü.
 * - GitHub'daki domains.json manifestinden domain listesi çeker
 * - Her domain'i dener, çalışanı bulur ve cache'ler
 * - Çalışmazsa BOOTSTRAP ve +1/+2/+3 tahminleriyle yeni domain dener
 */
class DomainResolver(private val preferences: SharedPreferences) {

    companion object {
        const val BOOTSTRAP = "https://www.inattvizle492.top/"
        const val GATEWAY = "https://www.inatgiris.com/"
        const val MANIFEST = "https://raw.githubusercontent.com/SafakStream/SafakStream/builds/domains.json"
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

        private const val PREF_LAST_GOOD = "lastGood"
        private const val PREF_CHECKED_AT = "checkedAt"
    }

    private val mutex = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var cached: SiteSnapshot? = null

    /**
     * Şu anki aktif URL (cache > pref > bootstrap sırasıyla)
     */
    fun getCurrentUrl(): String {
        cached?.url?.let { return it }
        return preferences.getString(PREF_LAST_GOOD, BOOTSTRAP) ?: BOOTSTRAP
    }

    fun getCheckedAt(): Long {
        cached?.checkedAt?.let { return it }
        return preferences.getLong(PREF_CHECKED_AT, 0L)
    }

    /**
     * Domain'i çözer. force=true ise cache'i atlar.
     */
    suspend fun resolve(force: Boolean = false): SiteSnapshot? = mutex.withLock {
        // Cache kontrolü (force değilse ve 1 saat geçmediyse cache dön)
        if (!force) {
            cached?.let { if (System.currentTimeMillis() - it.checkedAt < 60 * 60 * 1000) return@withLock it }
        }

        return@withLock withContext(Dispatchers.IO) {
            val tried = mutableSetOf<String>()

            // 1) Manifest'ten domainleri dene
            val manifestDomains = fetchManifest()
            for (url in manifestDomains) {
                if (!tried.add(url)) continue
                val snap = tryResolve(url)
                if (snap != null) return@withContext snap
            }

            // 2) Gateway'den (inatgiris.com) hedef domainleri çıkar
            val gatewayUrl = preferences.getString(PREF_LAST_GOOD, BOOTSTRAP) ?: BOOTSTRAP
            val gatewayTargets = fetchGatewayTargets(gatewayUrl)
            for (url in gatewayTargets) {
                if (!tried.add(url)) continue
                val snap = tryResolve(url)
                if (snap != null) return@withContext snap
            }

            // 3) Bootstrap'tan +1/+2/+3 domain tahminleri
            val candidates = SportsParser.nextDomains(BOOTSTRAP) + listOf(BOOTSTRAP)
            for (url in candidates) {
                if (!tried.add(url)) continue
                val snap = tryResolve(url)
                if (snap != null) return@withContext snap
            }

            null
        }
    }

    /**
     * Manuel URL ayarlama
     */
    suspend fun setManual(value: String): SiteSnapshot? = mutex.withLock {
        val normalized = SportsParser.siteUrl(value) ?: return@withLock null
        withContext(Dispatchers.IO) {
            tryResolve(normalized)?.also { snap ->
                preferences.edit()
                    .putString(PREF_LAST_GOOD, snap.url)
                    .putLong(PREF_CHECKED_AT, snap.checkedAt)
                    .apply()
            }
        }
    }

    private fun fetchHtml(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun tryResolve(url: String): SiteSnapshot? {
        return try {
            val html = fetchHtml(url) ?: return null
            val finalUrl = SportsParser.siteUrl(url) ?: return null
            val channels = SportsParser.channels(html, finalUrl)
            if (channels.isEmpty()) return null
            val config = SportsParser.config(html, finalUrl) ?: return null

            val snap = SiteSnapshot(
                url = finalUrl,
                channels = channels,
                config = config,
                checkedAt = System.currentTimeMillis()
            )
            cached = snap
            preferences.edit()
                .putString(PREF_LAST_GOOD, snap.url)
                .putLong(PREF_CHECKED_AT, snap.checkedAt)
                .apply()
            snap
        } catch (t: Throwable) {
            null
        }
    }

    private fun fetchManifest(): List<String> {
        return try {
            val req = Request.Builder()
                .url(MANIFEST)
                .header("User-Agent", UA)
                .build()
            val json = http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            // Manifest yapısı: {"domains": ["https://...", ...]} VEYA direkt dizi
            val trimmed = json.trim()
            val result = mutableListOf<String>()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    SportsParser.siteUrl(s)?.let { result.add(it) }
                }
            } else {
                val obj = org.json.JSONObject(trimmed)
                val arr = obj.optJSONArray("domains")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i)
                        SportsParser.siteUrl(s)?.let { result.add(it) }
                    }
                }
            }
            result
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun fetchGatewayTargets(gatewayUrl: String): List<String> {
        return try {
            val html = fetchHtml(GATEWAY) ?: return emptyList()
            SportsParser.gatewayTargets(html, GATEWAY)
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
