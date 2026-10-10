package com.safakstream.cinestream.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.safakstream.cinestream.ProviderRegistry

object Settings {

    const val PROVIDER_CINESTREAM = "ProviderCineStream"
    const val PROVIDER_SIMKL = "ProviderSimkl"
    const val PROVIDER_TMDB = "ProviderTmdb"

    fun getConcurrency(): Int = 10

    val allowDownloadLinks: Boolean = false

    val activeProviderOrder: List<String>
        get() = ProviderRegistry.keys

    fun cloudflareBypassEnabled(): Boolean = false

    data class BypassDomain(val domain: String, val displayName: String)

    fun getCloudflareBypassDomains(): List<BypassDomain> = emptyList()

    fun hasCloudflareBypassForUrl(url: String): Boolean = false

    fun saveCookieForDomain(urlOrDomain: String, cookies: String) {}
    fun getCookieForDomain(urlOrDomain: String): String? = null
    fun hasSavedCookieForDomain(urlOrDomain: String): Boolean = false
    fun removeCookieForDomain(urlOrDomain: String) {}

    val TORRENT_KEYS: Set<String> get() = ProviderRegistry.torrentKeys
    val PROVIDER_NAMES: Map<String, String> get() = ProviderRegistry.namesMap
    val DEFAULT_ORDER: List<String> get() = ProviderRegistry.keys

    fun newProviderDefaultOn(): Boolean = true

    fun getSeenProviders(): Set<String> = emptySet()

    fun initSeenProviders() {}

    fun markProvidersSeen(keys: Collection<String>) {}

    fun enabled(key: String): Boolean = true

    fun getOrder(): List<String> = DEFAULT_ORDER

    fun saveOrder(order: List<String>) {}

    enum class AddonType { HTTPS, TORRENT, DEBRID, SUBTITLE }

    data class StremioAddon(val name: String, val url: String, val type: AddonType)

    fun stremioAddonKey(name: String): String =
        "stremio_${name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}"

    fun providerDisplayName(key: String): String {
        PROVIDER_NAMES[key]?.let { return it }
        return key
    }

    fun isStremioTorrent(key: String): Boolean = false

    fun getStremioAddons(): MutableList<StremioAddon> = mutableListOf()

    fun saveStremioAddons(addons: List<StremioAddon>) {}

    fun saveShowboxToken(token: String) {}
    fun getShowboxToken(): String? = null
    fun clearShowboxToken() {}

    fun saveWyzieSubsKey(key: String) {}
    fun getWyzieSubsKey(): String? = null
    fun clearWyzieSubsKey() {}

    fun saveGramCinemaToken(token: String) {}
    fun getGramCinemaToken(): String? = null
    fun clearGramCinemaToken() {}

    fun showSettingsDialog(context: Context, onSave: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("🎬 CineStream Ayarları")
            .setMessage("Sürüm: v1\nGeliştirici: SafakStream\n\nFilm, Dizi, Anime ve Asya Dizileri için tek adres çözümü.\n\nBu sürümde gelişmiş ayarlar devre dışıdır.")
            .setPositiveButton("Tamam") { dialog, _ ->
                dialog.dismiss()
                onSave()
            }
            .show()
    }
}
