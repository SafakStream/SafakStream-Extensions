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
        var language = sharedPref?.getString("language", "it")
        var country = sharedPref?.getString("country", "IT")

        if (language.isNullOrEmpty()) language = "it"
        if (country.isNullOrEmpty()) country = "IT"

        registerMainAPI(YouTubeProvider(sharedPref))
        registerMainAPI(YouTubePlaylistsProvider(sharedPref))
        registerMainAPI(YouTubeChannelProvider(sharedPref))

        setOpenSettings { context ->
            val activity = context as androidx.appcompat.app.AppCompatActivity
            SettingsFragment(this, sharedPref).show(activity.supportFragmentManager, "Frag")
        }
    }
}
