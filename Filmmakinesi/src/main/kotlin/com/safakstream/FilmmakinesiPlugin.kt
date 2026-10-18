package com.safakstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmmakinesiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmmakinesiProvider())
    }
}
