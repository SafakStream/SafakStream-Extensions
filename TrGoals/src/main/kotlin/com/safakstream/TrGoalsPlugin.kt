// SafakStream-Extensions/TrGoals/src/main/kotlin/com/safakstream/TrGoalsPlugin.kt

package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TrGoalsPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TrGoals())
        registerExtractorAPI(TrGoalsExtractor())
    }
}
