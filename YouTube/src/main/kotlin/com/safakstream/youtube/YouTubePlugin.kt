package com.safakstream.youtube

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class YouTubePlugin : Plugin() {
    private val sharedPref: SharedPreferences? by lazy {
        CommonActivity.activity?.getSharedPreferences("Youtube", Context.MODE_PRIVATE)
    }

    override fun load(context: Context) {
        // Eğer önceden kaydedilmiş ayar yoksa varsayılan olarak "tr" ve "TR" kullanılacak.
        // Bu ayarlar cihazda saklanmadığı sürece her açılışta varsayılan değerler geçerli olur.
        val language = sharedPref?.getString("language", "tr") ?: "tr"
        val country = sharedPref?.getString("country", "TR") ?: "TR"

        registerMainAPI(YouTubeProvider(sharedPref))
        registerMainAPI(YouTubePlaylistsProvider(sharedPref))
        registerMainAPI(YouTubeChannelProvider(sharedPref))

        // Not: SettingsFragment orijinal kodda vardı ama bizde yok.
        // Bu yüzden ayarlar menüsünü devre dışı bıraktık.
    }
}
