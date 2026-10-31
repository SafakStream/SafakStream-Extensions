package com.safakstream.mahsunsports

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MahsunSportsPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MahsunSportsProvider())
    }
}
