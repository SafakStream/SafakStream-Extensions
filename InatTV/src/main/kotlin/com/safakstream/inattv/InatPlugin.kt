package com.safakstream.inattv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class InatPlugin : Plugin() {
    override fun load(context: Context) {
        val prefs = context.getSharedPreferences("safakstream_inattv", Context.MODE_PRIVATE)
        val resolver = DomainResolver(prefs)
        val artwork = ChannelArtwork(context)
        registerMainAPI(InatTV(resolver, artwork))
    }
}
