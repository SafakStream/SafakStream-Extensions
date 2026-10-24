package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SafakStreamCinemaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SafakStreamCinema())
    }
}
