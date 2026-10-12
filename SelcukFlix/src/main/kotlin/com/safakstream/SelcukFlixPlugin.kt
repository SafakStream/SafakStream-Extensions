package com.safakstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SelcukFlixPlugin : Plugin() {
    override fun load(context: Context) {
        // Ana site eklentisi
        registerMainAPI(SelcukFlix())
        // pichive iframe extractor'ı
        registerExtractorAPI(PichiveExtractor())
    }
}
