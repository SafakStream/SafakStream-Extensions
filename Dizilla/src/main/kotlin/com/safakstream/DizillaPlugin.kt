package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DizillaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DizillaProvider())
    }
}
