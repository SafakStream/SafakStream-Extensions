// SafakStream-Extensions/InatTV/src/main/kotlin/com/safakstream/InatTVPlugin.kt

package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class InatTVPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(InatTV())
    }
}
