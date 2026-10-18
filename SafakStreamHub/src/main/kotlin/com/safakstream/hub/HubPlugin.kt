package com.safakstream.hub

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import kotlin.Unit

@CloudstreamPlugin
class HubPlugin : Plugin() {

    override fun load(context: Context) {
        // Context'i AppCompatActivity olarak alıyoruz
        val activity = context as AppCompatActivity
        
        // Cloudstream'in istediği setOpenSettings fonksiyonunu kullanıyoruz
        setOpenSettings { 
            val frag = HubFragment(this)
            frag.show(activity.supportFragmentManager, "HubFragment")
            Unit // Fonksiyonun bir değer döndürmesi gerekiyor
        }
    }
}
