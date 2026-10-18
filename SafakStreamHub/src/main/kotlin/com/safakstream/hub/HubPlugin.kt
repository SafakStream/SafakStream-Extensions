package com.safakstream.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HubPlugin : Plugin() {
    override fun load(context: Context) {
        // Eklenti yüklendiğinde çalışacak kodlar (şimdilik boş)
    }

    // Cloudstream'in ayarlara tıklandığında çağırdığı fonksiyon
    override fun getSettings(context: Context): Intent? {
        // Doğrudan GitHub'daki HTML sayfasını tarayıcıda aç
        val url = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/main/index.html"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        
        // Cloudstream'in kendi ayar ekranını açmasını engellemek için null dön
        return null
    }
}
