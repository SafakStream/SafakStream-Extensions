// SafakStream-Extensions/BeyazElma/src/main/kotlin/com/safakstream/BeyazElmaPlugin.kt

package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BeyazElmaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BeyazElma())
    }
}
