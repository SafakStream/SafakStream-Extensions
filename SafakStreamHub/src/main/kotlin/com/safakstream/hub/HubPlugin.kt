package com.safakstream.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HubPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HubProvider())
    }
}

class HubProvider : MainAPI() {
    override var mainUrl = "https://github.com/SafakStream"
    override var name = "SafakStream Destek"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val welcomeText = newMovieSearchResponse(
            "SafakStream projesine hoş geldiniz! Bu bölümden projeye destek olabilirsiniz.",
            "destek1"
        )

        val goalText = newMovieSearchResponse(
            "🎯 HEDEFİMİZ: 20 Destekçi\n\n20 destekçiye ulaştığımızda, sunucu ve altyapı masraflarını garanti altına alarak eklentilerin sürekliliğini sağlayacağım. Desteğiniz, projenin geleceği için büyük bir motivasyon kaynağıdır.",
            "destek2"
        )

        val githubButton = newMovieSearchResponse(
            "🌐 GitHub Depomu Ziyaret Et",
            "github"
        )

        val kreosusButton = newMovieSearchResponse(
            "☕ Kreosus Üzerinden Destek Ol",
            "kreosus"
        )

        val items = listOf(welcomeText, goalText, githubButton, kreosusButton)

        return newHomePageResponse(
            list = listOf(HomePageList("Destek Ol", items)),
            hasNext = false
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        val ctx = appContext 
        
        when (url) {
            "github" -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SafakStream"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }
            "kreosus" -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kreosus.com/safakstream"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }
        }
        
        return null
    }
}
