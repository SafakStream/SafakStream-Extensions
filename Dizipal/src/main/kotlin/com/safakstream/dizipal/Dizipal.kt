package com.safakstream.dizipal

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Dizipal : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DizipalProvider())
        registerExtractorAPI(DizipalPlayer())
    }
}
