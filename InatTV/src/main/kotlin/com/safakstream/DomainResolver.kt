// SafakStream-Extensions/InatTV/src/main/kotlin/com/safakstream/DomainResolver.kt

package com.safakstream

import android.util.Log
import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URI

object DomainResolver {
    private const val TAG = "DomainResolver"
    private const val MANIFEST = "https://raw.githubusercontent.com/Wiojelt/TurkSpor/main/domains.json"
    private const val BOOTSTRAP = "https://www.inattvizle494.top/"
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

    private var cached: SiteSnapshot? = null
    private var cachedAt: Long = 0

    data class SiteSnapshot(
        val url: String,
        val channels: List<SportsChannel>,
        val config: PlayerConfig
    )

    suspend fun resolve(force: Boolean = false): SiteSnapshot? {
        val now = System.currentTimeMillis()
        if (!force && cached != null && (now - cachedAt) < 10 * 60 * 1000) {
            return cached
        }

        val candidates = mutableListOf<String>()

        // 1. domains.json'dan inattv adaylarini cek
        try {
            val json = app.get(MANIFEST, headers = mapOf("User-Agent" to UA)).text
            val inattv = JSONObject(json).getJSONObject("sources").getJSONObject("inattv")

            inattv.optJSONArray("candidates")?.let { arr ->
                for (i in 0 until arr.length()) candidates.add(arr.getString(i))
            }
            inattv.optJSONArray("gateways")?.let { arr ->
                for (i in 0 until arr.length()) candidates.add(arr.getString(i))
            }
            Log.d(TAG, "Manifest'ten ${candidates.size} aday alindi")
        } catch (e: Exception) {
            Log.e(TAG, "Manifest parse hatasi: ${e.message}")
        }

        // 2. BOOTSTRAP + nextDomains
        candidates.add(BOOTSTRAP)
        candidates.addAll(nextDomains(BOOTSTRAP))

        // 3. Her adayi dene
        val tried = mutableSetOf<String>()
        for (candidate in candidates) {
            val siteUrl = candidate.trimEnd('/') + "/"
            if (!tried.add(siteUrl)) continue

            try {
                val doc = app.get(siteUrl, headers = mapOf("User-Agent" to UA)).document
                val html = doc.html()

                if (!doc.title().contains("inatTV", ignoreCase = true)) {
                    Log.d(TAG, "inatTV degil: $siteUrl")
                    continue
                }

                val channels = SportsParser.channels(html, siteUrl)
                if (channels.isEmpty()) {
                    Log.d(TAG, "Kanal yok: $siteUrl")
                    continue
                }

                val config = SportsParser.config(html, siteUrl) ?: continue

                val snapshot = SiteSnapshot(siteUrl, channels, config)
                cached = snapshot
                cachedAt = now
                Log.d(TAG, "DOMAIN OK: $siteUrl (${channels.size} kanal)")
                return snapshot
            } catch (e: Exception) {
                Log.d(TAG, "FAIL: $siteUrl - ${e.message}")
            }
        }

        Log.e(TAG, "Hicbir domain calismadi!")
        return cached
    }

    private fun nextDomains(bootstrap: String): List<String> {
        val regex = Regex("^(www\\.)?inatt?vizle([0-9]{1,6})\\.(top|xyz)$", RegexOption.IGNORE_CASE)
        val host = try { URI(bootstrap).host ?: return emptyList() } catch (e: Exception) { return emptyList() }
        val match = regex.matchEntire(host) ?: return emptyList()
        val number = match.groupValues[2].toIntOrNull() ?: return emptyList()
        val tld = match.groupValues[3]

        return (-5..5).filter { it != 0 }.map { offset ->
            "https://www.inattvizle${number + offset}.$tld/"
        }
    }

    fun currentUrl(): String = cached?.url ?: BOOTSTRAP
}
