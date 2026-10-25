package com.safakstream.safakstreamtv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SafakStreamTVPlugin : Plugin() {
    override fun load(context: Context) {
        SafakStreamTVProvider.appContext = context
        registerMainAPI(SafakStreamTVProvider())
    }
}
