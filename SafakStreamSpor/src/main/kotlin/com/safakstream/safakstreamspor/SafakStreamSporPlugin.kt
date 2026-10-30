package com.safakstream.safakstreamspor

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SafakStreamSporPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SafakStreamSporProvider())
    }
}
