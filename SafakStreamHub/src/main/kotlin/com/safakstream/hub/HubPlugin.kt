package com.safakstream.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HubPlugin : Plugin() {
    
    override fun load(context: Context) {
        // Dişli çarka basıldığında çalışacak kod
        openSettings = {
            try {
                // HTML sayfasını tarayıcının web sayfası olarak açması için htmlpreview kullanıyoruz
                val url = "https://htmlpreview.github.io/?https://github.com/SafakStream/SafakStream-Extensions/blob/main/index.html"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
