package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NetMirrorPlugin : Plugin() {
    override fun load(context: Context) {
        NetMirrorStorage.init(context)
        registerMainAPI(NetMirrorAPI())
    }
}
