package com.safakstream.hub

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HubPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as AppCompatActivity
        
        // Ayarlar butonuna basıldığında HubFragment'ı göster
        openSettings = { 
            val frag = HubFragment(this)
            frag.show(activity!!.supportFragmentManager, "HubFragment")
        }
    }
}
