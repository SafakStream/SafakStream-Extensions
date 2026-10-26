package com.safakstream.hdfilmcehennemi
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
@CloudstreamPlugin
class HDFilmCehennemiPlugin : Plugin() {
    override fun load() {
        registerMainAPI(HDFilmCehennemi())
    }
}
