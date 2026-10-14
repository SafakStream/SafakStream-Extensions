package com.safakstream.inattv

import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder

object SportsParser {

    private val hostPattern = Regex("^(www\\.)?inattvizle([0-9]{1,6})\\.top$", RegexOption.IGNORE_CASE)

    /**
     * URL'yi doğrular: https, port -1/443, host pattern'e uygun mu?
     */
    fun siteUrl(value: String): String? {
        return try {
            val uri = URI(value.trim())
            if (uri.scheme == "https" &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443)
            ) {
                val host = uri.host ?: return null
                if (hostPattern.matches(host)) {
                    "https://${host.lowercase()}/"
                } else null
            } else null
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * HTML'den gateway linklerini çıkarır (a[href], link[rel=canonical])
     */
    fun gatewayTargets(html: String, base: String): List<String> {
        val doc = Jsoup.parse(html, base)
        return doc.select("a[href],link[rel=canonical]")
            .mapNotNull { el ->
                val href = el.absUrl("href").ifBlank { el.attr("href") }
                siteUrl(href)
            }
            .distinct()
    }

    /**
     * Mevcut domain'den sonraki 3 domain adayını üretir
     * Örn: inattvizle492.top -> [493, 494, 495]
     */
    fun nextDomains(value: String): List<String> {
        val host = try {
            URI(value).host
        } catch (t: Throwable) {
            null
        } ?: return emptyList()

        val match = hostPattern.matchEntire(host) ?: return emptyList()
        val numberStr = match.groupValues.getOrNull(2) ?: return emptyList()
        val number = numberStr.toIntOrNull() ?: return emptyList()

        return (1..3).map { "https://www.inattvizle${number + it}.top/" }
    }

    /**
     * https URL doğrulaması (basit)
     */
    fun httpsUrl(value: String): String? {
        return try {
            val uri = URI(value)
            if (uri.scheme == "https" && uri.host != null && uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443)
            ) value else null
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * HTML'den spor kanallarını çıkarır
     * Selector: #channel-slider .channel-item[data-name][data-target][data-source]
     */
    fun channels(html: String, base: String): List<SportsChannel> {
        val doc = Jsoup.parse(html, base)

        // Site kontrolü
        if (!doc.title().contains("inatTV", ignoreCase = true)) {
            return emptyList()
        }

        return doc.select("#channel-slider .channel-item[data-name][data-target][data-source]")
            .mapNotNull { a ->
                // data-url'den ID çıkar: "#bein-1-canli-izle" -> "bein-1"
                val id = a.attr("data-url")
                    .removePrefix("#")
                    .removeSuffix("-canli-izle")

                // ID formatı kontrolü
                if (!Regex("[a-zA-Z0-9_-]{1,80}").matches(id)) return@mapNotNull null

                val target = a.attr("data-target")
                val source = a.attr("data-source")

                // Kaynak tipi kontrolü
                when (target) {
                    "viptv" -> {
                        if (!Regex("[0-9]{1,12}").matches(source)) return@mapNotNull null
                    }
                    "m3u8" -> {
                        if (httpsUrl(source) == null) return@mapNotNull null
                    }
                    else -> return@mapNotNull null
                }

                // data-name'den başlık al
                val title = a.attr("data-name").trim().takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null

                SportsChannel(
                    id = id,
                    title = title,
                    category = "Spor Kanalları",
                    player = source,
                    target = target
                )
            }
    }

    /**
     * HTML'den PlayerConfig çıkarır
     * Regex: var phptojs = {...};
     */
    fun config(html: String, base: String): PlayerConfig? {
        val match = Regex("var\\s+phptojs\\s*=\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL)
            .find(html) ?: return null

        val data = match.groupValues.getOrNull(1) ?: return null
        val server = configField(data, "stream_server_cdn_url") ?: return null
        var site = configField(data, "site_name") ?: return null

        // site_name doğrulaması: boş değil, 100 karakterden kısa, newline yok
        val validSite = site.isNotBlank() && site.length < 100 &&
            !site.contains('\n') && !site.contains('\r')
        if (!validSite) return null

        // auth.php URL'ini çözümle
        val auth = try {
            URI(base).resolve(server).resolve("auth.php").toString()
        } catch (t: Throwable) {
            return null
        }

        if (httpsUrl(auth) == null) return null

        // Host kontrolü
        val authHost = try { URI(auth).host } catch (t: Throwable) { null }
        val baseHost = try { URI(base).host } catch (t: Throwable) { null }
        if (authHost == null || baseHost == null || authHost != baseHost) return null

        return PlayerConfig(authUrl = auth, siteName = site)
    }

    private fun configField(data: String, key: String): String? {
        val match = Regex("['\"]$key['\"]\\s*:\\s*['\"]([^'\"]*)['\"]").find(data)
        return match?.groupValues?.getOrNull(1)
    }

    /**
     * JSON'dan StreamSession çıkarır
     * Beklenen format: {"URL": "...", "TOKEN": "..."}
     */
    fun streamSession(json: String): StreamSession? {
        return try {
            val mapper = ObjectMapper()
            val node = mapper.readTree(json)

            if (node.has("ERROR")) return null

            val urlRaw = node.path("URL").asText()
            val url = httpsUrl(urlRaw) ?: return null

            val token = node.path("TOKEN").asText("")
            if (token.length > 2048) return null
            if (token.contains('\n') || token.contains('\r')) return null

            StreamSession(url = url, token = token)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * URL'den query parametresi çıkarır
     */
    fun queryParam(url: String, key: String): String? {
        return try {
            val query = URI(url).rawQuery ?: return null
            query.split("&")
                .firstOrNull { it.substringBefore("=") == key }
                ?.substringAfter("=", "")
                ?.let { URLDecoder.decode(it, "UTF-8") }
        } catch (t: Throwable) {
            null
        }
    }
}
