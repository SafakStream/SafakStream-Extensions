package com.safakstream.netmirror

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class NetMirrorPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(NetMirror())
    }
}
