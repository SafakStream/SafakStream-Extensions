package com.safakstream.trgoals

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TrGoalsPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TrGoalsProvider())
    }
}
