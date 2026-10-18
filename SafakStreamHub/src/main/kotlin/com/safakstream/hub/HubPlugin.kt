package com.safakstream.hub

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HubPlugin : Plugin() {
    
    override fun load(context: Context) {
        // Activity'yi güvenli bir şekilde saklıyoruz
        val activity = context as? AppCompatActivity
        
        // Ayar butonuna basıldığında çalışacak kod
        openSettings = {
            activity?.let { act ->
                val frag = HubFragment(this)
                frag.show(act.supportFragmentManager, "HubFragment")
            }
        }
    }
}
