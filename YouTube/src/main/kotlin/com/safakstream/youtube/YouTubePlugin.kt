package com.safakstream.youtube

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.safakstream.youtube.settings.SettingsFragment

@CloudstreamPlugin
class YouTubePlugin : Plugin() {
    private val sharedPref: SharedPreferences? by lazy {
        CommonActivity.activity?.getSharedPreferences("Youtube", Context.MODE_PRIVATE)
    }

    override fun load(context: Context) {
        val language = sharedPref?.getString("language", "it") ?: "it"
        val country = sharedPref?.getString("country", "IT") ?: "IT"

        registerMainAPI(YouTubeProvider(sharedPref))
        registerMainAPI(YouTubePlaylistsProvider(sharedPref))
        registerMainAPI(YouTubeChannelProvider(sharedPref))

        setOpenSettings {
            val activity = context as? androidx.appcompat.app.AppCompatActivity ?: return@setOpenSettings
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}
