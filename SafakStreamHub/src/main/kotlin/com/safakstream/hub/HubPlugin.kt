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
        // Eklenti yüklendiğinde API'mizi kaydediyoruz
        registerMainAPI(HubProvider())
    }
}

class HubProvider : MainAPI() {
    override var mainUrl = "https://github.com/SafakStream"
    override var name = "SafakStream Destek"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    // Ana sayfa oluşturma
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val context = this.context ?: return newHomePageResponse(emptyList())

        // 1. Karşılama Metni
        val welcomeText = newMovieSearchResponse(
            "SafakStream projesine hoş geldiniz! Bu bölümden projeye destek olabilirsiniz.",
            "destek1"
        )

        // 2. Hedef Metni (Görseldeki gibi)
        val goalText = newMovieSearchResponse(
            "🎯 HEDEFİMİZ: 20 Destekçi\n\n20 destekçiye ulaştığımızda, sunucu ve altyapı masraflarını garanti altına alarak eklentilerin sürekliliğini sağlayacağım. Desteğiniz, projenin geleceği için büyük bir motivasyon kaynağıdır.",
            "destek2"
        )

        // 3. GitHub Butonu
        val githubButton = newMovieSearchResponse(
            "🌐 GitHub Depomu Ziyaret Et",
            "github"
        )

        // 4. Kreosus Butonu
        val kreosusButton = newMovieSearchResponse(
            "☕ Kreosus Üzerinden Destek Ol",
            "kreosus"
        )

        // Hepsini listeye ekle
        val items = listOf(welcomeText, goalText, githubButton, kreosusButton)

        return newHomePageResponse(
            list = listOf(HomePageList("Destek Ol", items)),
            hasNext = false
        )
    }

    // Tıklama işlemleri
    override suspend fun load(url: String): LoadResponse {
        val context = this.context ?: return newMovieLoadResponse("Hata", "hata", TvType.Movie, "hata") {}
        
        when (url) {
            "github" -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SafakStream"))
                context.startActivity(intent)
            }
            "kreosus" -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kreosus.com/safakstream"))
                context.startActivity(intent)
            }
        }
        
        // Tıkladıktan sonra ekranda kalmaması için boş bir yanıt dönüyoruz
        return newMovieLoadResponse("Tıklandı", url, TvType.Movie, url) {
            MainPageData(emptyList(), false, emptyList())
        }
    }
}
