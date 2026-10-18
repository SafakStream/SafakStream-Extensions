package com.safakstream.hub

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.SettingsScreen
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.text
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.button
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.openBrowser

@CloudstreamPlugin
class HubPlugin : Plugin() {

    override fun load(context: Context) {
        // Eklenti yüklendiğinde çalışacak kodlar
    }

    override fun getSettings(): SettingsScreen {
        val context = context ?: return super.getSettings()

        return SettingsScreen("SafakStream Destek", context) {
            
            text("SafakStream projesine hoş geldiniz! Bu bölümden projeye destek olabilirsiniz.")
            
            text(
                "🎯 HEDEFİMİZ: 20 Destekçi\n\n" +
                "20 destekçiye ulaştığımızda, sunucu ve altyapı masraflarını garanti altına alarak eklentilerin sürekliliğini sağlayacağım. Desteğiniz, projenin geleceği için büyük bir motivasyon kaynağıdır."
            )

            button("🌐 GitHub Depomu Ziyaret Et") {
                openBrowser("https://github.com/SafakStream")
            }

            button("☕ Kreosus Üzerinden Destek Ol") {
                openBrowser("https://kreosus.com/safakstream")
            }
        }
    }
}
