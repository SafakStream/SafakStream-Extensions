package com.safakstream.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.SettingsScreen

@CloudstreamPlugin
class HubPlugin : Plugin() {

    override fun load(context: Context) {
        println("SafakStream Hub başarıyla yüklendi!")
    }

    override fun getSettings(): SettingsScreen {
        val context = context ?: return super.getSettings()

        return SettingsScreen("SafakStream Hakkında", context) {
            
            // Karşılama Metni
            text("SafakStream projesine hoş geldiniz! Bu eklenti, proje hakkında bilgi almak, GitHub depoma ulaşmak ve projeye destek olmak için tasarlanmıştır.")
            
            // Kreosus Hedef Bilgisi (Görseldeki gibi şık bir metin)
            text(
                "🎯 HEDEFİMİZ: 20 Destekçi\n\n" +
                "20 destekçiye ulaştığımızda, sunucu ve altyapı masraflarını garanti altına alarak eklentilerin sürekliliğini sağlayacağım. Desteğiniz, projenin geleceği için büyük bir motivasyon kaynağıdır."
            )

            // GitHub Butonu
            button("🌐 GitHub Depomu Ziyaret Et") {
                val url = "https://github.com/SafakStream"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }

            // Kreosus Destek Butonu
            button("☕ Kreosus Üzerinden Destek Ol") {
                val url = "https://kreosus.com/safakstream"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        }
    }
}
