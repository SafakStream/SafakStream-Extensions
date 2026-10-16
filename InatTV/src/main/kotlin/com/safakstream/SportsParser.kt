// SafakStream-Extensions/InatTV/src/main/kotlin/com/safakstream/SportsParser.kt

package com.safakstream

import org.jsoup.Jsoup
import java.net.URI

object SportsParser {
    private val hostPattern = Regex("^(www\\.)?inatt?vizle([0-9]{1,6})\\.(top|xyz)$", RegexOption.IGNORE_CASE)

    fun siteUrl(value: String): String? {
        return try {
            val uri = URI(value.trim())
            if (uri.scheme == "https" &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                hostPattern.matches(uri.host ?: "")) {
                "https://${uri.host.lowercase()}/"
            } else null
        } catch (e: Exception) { null }
    }

    fun config(html: String, base: String): PlayerConfig? {
        val phptojsMatch = Regex("var\\s+phptojs\\s*=\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: return null
        val data = phptojsMatch.groupValues[1]

        val server = configField(data, "stream_server_cdn_url") ?: return null
        val siteName = configField(data, "site_name") ?: return null

        if (siteName.isBlank() || siteName.length >= 100 || siteName.contains('\n') || siteName.contains('\r')) {
            return null
        }

        return try {
            val baseUri = URI(base)
            val authUrl = baseUri.resolve(server).resolve("auth.php").toString()
            val authHost = URI(authUrl).host
            if (authHost == null || authHost != baseUri.host) return null
            PlayerConfig(authUrl, siteName)
        } catch (e: Exception) { null }
    }

    private fun configField(data: String, key: String): String? {
        return Regex("['\"]$key['\"]\\s*:\\s*['\"]([^'\"]*)['\"]")
            .find(data)?.groupValues?.get(1)
    }

    fun channels(html: String, base: String): List<SportsChannel> {
        val doc = Jsoup.parse(html, base)
        if (!doc.title().contains("inatTV", ignoreCase = true)) return emptyList()

        return doc.select("#channel-slider .channel-item[data-name][data-target][data-source]")
            .mapNotNull { el ->
                val id = el.attr("data-url").removePrefix("#").removeSuffix("-canli-izle")
                if (!Regex("[a-zA-Z0-9_-]{1,80}").matches(id)) return@mapNotNull null

                val target = el.attr("data-target").trim()
                val source = el.attr("data-source").trim()
                val title = el.attr("data-name").trim()
                if (title.isBlank()) return@mapNotNull null

                val poster = el.selectFirst("img")?.attr("src")?.let {
                    if (it.startsWith("http")) it else base.trimEnd('/') + "/" + it.trimStart('/')
                }

                when (target) {
                    "viptv" -> if (Regex("[0-9]{1,12}").matches(source))
                        SportsChannel(id, title, "Spor Kanallari", source, target, poster) else null
                    "m3u8" -> if (source.startsWith("http"))
                        SportsChannel(id, title, "Spor Kanallari", source, target, poster) else null
                    "frame" -> SportsChannel(id, title, "Spor Kanallari", source, target, poster)
                    else -> null
                }
            }.distinctBy { it.id }
    }

    fun matches(html: String, base: String): List<SportsChannel> {
        val doc = Jsoup.parse(html, base)
        return doc.select("ul.match-list li[data-source]").mapNotNull { el ->
            val rawName = el.attr("data-name")
            val name = if (rawName.isNotBlank()) Jsoup.parse(rawName).text()
                       else el.attr("data-title").substringBefore(" Canli Izle").trim()
            if (name.isBlank()) return@mapNotNull null

            val sourcesRaw = el.attr("data-source")
            val target = el.attr("data-target").trim()

            val player: String
            val targetType: String

            if (sourcesRaw.startsWith("[")) {
                // JSON array - mac formati
                try {
                    val arr = org.json.JSONArray(sourcesRaw)
                    if (arr.length() == 0) return@mapNotNull null
                    val first = arr.getJSONObject(0)
                    player = first.optString("link", "")
                    targetType = first.optString("tip", "viptv")
                    if (player.isBlank()) return@mapNotNull null
                } catch (e: Exception) { return@mapNotNull null }
            } else if (sourcesRaw.isNotBlank()) {
                player = sourcesRaw
                targetType = target.ifBlank { "viptv" }
            } else return@mapNotNull null

            val poster = el.attr("data-home_logo").ifBlank {
                el.selectFirst("img")?.attr("src")?.let {
                    if (it.startsWith("http")) it else base.trimEnd('/') + "/" + it.trimStart('/')
                }
            }

            val id = el.attr("data-url").removePrefix("#").removeSuffix("-canli-izle")
                .ifBlank { name.replace(" ", "-").lowercase() }

            SportsChannel(id, name, "Maclar", player, targetType, poster)
        }.distinctBy { it.id }
    }
}
