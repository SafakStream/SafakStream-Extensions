// SafakStream-Extensions/SafakStream Spor/src/main/kotlin/com/safakstream/SafakStream SporPlugin.kt

package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SafakStream SporPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SafakStream Spor())
    }
}
