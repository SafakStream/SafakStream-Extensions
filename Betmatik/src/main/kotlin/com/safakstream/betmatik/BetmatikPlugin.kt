package com.safakstream.betmatik

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BetmatikPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BetmatikProvider())
    }
}
