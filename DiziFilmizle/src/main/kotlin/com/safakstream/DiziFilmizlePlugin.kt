package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DiziFilmizlePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziFilmizle())
        registerExtractorAPI(VidmixiExtractor())
    }
}
