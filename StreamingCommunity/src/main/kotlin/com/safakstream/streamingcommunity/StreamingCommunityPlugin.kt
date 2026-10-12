package com.safakstream.streamingcommunity

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class StreamingCommunityPlugin : Plugin() {
    companion object {
        const val PREF_BASE_URL = "baseUrl"
        const val PREF_LANG = "lang"
        const val PREF_LANG_POSITION = "langPosition"
        const val PREF_SHOW_UPCOMING = "showUpcoming"
    }

    private var sharedPref: SharedPreferences? = null

    override fun load(context: Context) {
        // Ayarları yükle
        sharedPref = CommonActivity.activity?.getSharedPreferences("StreamingCommunity", Context.MODE_PRIVATE)
        
        val lang = sharedPref?.getString(PREF_LANG, "it") ?: "it"
        val baseUrl = sharedPref?.getString(PREF_BASE_URL, null)
        val showUpcoming = sharedPref?.getBoolean(PREF_SHOW_UPCOMING, true) ?: true

        // Ana API'yi kaydet (StreamingCommunity sınıfınızı burada çağırıyoruz)
        registerMainAPI(StreamingCommunity(lang, baseUrl, showUpcoming))

        // Extractor'ları kaydet
        registerExtractorAPI(VixCloudExtractor())
        registerExtractorAPI(VixSrcExtractor())

        // Ayarlar butonuna tıklandığında Settings fragment'ını aç
        setOpenSettings {
            val activity = CommonActivity.activity as? androidx.appcompat.app.AppCompatActivity
            activity?.supportFragmentManager?.let { fm ->
                Settings(this, sharedPref).show(fm, "Settings")
            }
        }
    }
}
