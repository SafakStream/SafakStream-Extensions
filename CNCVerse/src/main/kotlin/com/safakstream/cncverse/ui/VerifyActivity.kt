package com.safakstream.cncverse.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.safakstream.cncverse.CNCVerseCookieStore

/**
 * Görünür WebView Activity'si.
 * verify.php'yi açar, kullanıcı reCAPTCHA'yı çözer,
 * sonra ?hee5= URL'i yakalanır ve cookie'ler kaydedilir.
 */
class VerifyActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = DEFAULT_UA

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // userver.net52.cc/?hee5=... URL'i yakala
                    if (url.contains("hee5=")) {
                        handleTokenCaught(url)
                        return true
                    }
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val u = url ?: return false
                    if (u.contains("hee5=")) {
                        handleTokenCaught(u)
                        return true
                    }
                    return false
                }
            }
        }

        // CookieManager etkinleştir
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        val container = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(container)

        // verify.php'yi aç
        webView.loadUrl("$NET52/verify.php", mapOf("Referer" to "$NET52/"))
    }

    private fun handleTokenCaught(url: String) {
        // URL'den hee5 token'ını al
        val hee5 = url.substringAfter("hee5=", "").substringBefore("&").ifBlank { null }

        // CookieManager'dan tüm cookie'leri çek
        val cookieString = CookieManager.getInstance().getCookie(NET52) ?: ""
        val cookies = parseCookies(cookieString)

        // SharedPreferences'a kaydet
        CNCVerseCookieStore.save(
            context = applicationContext,
            hee5 = hee5,
            cookies = cookies
        )

        // WebView'i temizle ve activity'yi kapat
        webView.stopLoading()
        webView.destroy()
        finish()
    }

    private fun parseCookies(cookieString: String): Map<String, String> {
        return cookieString.split(";").mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webView.isInitialized) {
            webView.destroy()
        }
    }

    companion object {
        const val NET52 = "https://net52.cc"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 5 Build/TQ3A.230901.001; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.132 " +
            "Mobile Safari/537.36 /OS.Gatu v3.0"
    }
}
