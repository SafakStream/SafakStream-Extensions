package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DiziBoxPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziBox())
        // Video extractor'ı Faz 2'de ekleyeceğiz
    }
}
